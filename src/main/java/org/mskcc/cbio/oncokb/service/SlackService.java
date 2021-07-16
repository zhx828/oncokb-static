package org.mskcc.cbio.oncokb.service;

import com.slack.api.Slack;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.model.Message;
import com.slack.api.model.block.*;
import com.slack.api.model.block.composition.*;
import com.slack.api.model.block.element.BlockElement;
import com.slack.api.model.block.element.ButtonElement;
import com.slack.api.model.block.element.StaticSelectElement;
import com.slack.api.webhook.Payload;
import com.slack.api.webhook.WebhookResponse;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.cbio.oncokb.config.application.ApplicationProperties;
import org.mskcc.cbio.oncokb.domain.UserIdMessagePair;
import org.mskcc.cbio.oncokb.domain.UserStatusChecks;
import org.mskcc.cbio.oncokb.domain.enumeration.*;
import org.mskcc.cbio.oncokb.service.dto.UserDTO;
import org.mskcc.cbio.oncokb.service.dto.useradditionalinfo.AdditionalInfoDTO;
import org.mskcc.cbio.oncokb.service.mapper.UserMapper;
import org.mskcc.cbio.oncokb.web.rest.slack.ActionId;
import org.mskcc.cbio.oncokb.web.rest.slack.BlockId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.mskcc.cbio.oncokb.config.Constants.*;
import static org.mskcc.cbio.oncokb.util.MainUtil.isMSKUser;
import static org.mskcc.cbio.oncokb.util.StringUtil.getEmailDomain;
import static org.mskcc.cbio.oncokb.util.TimeUtil.toNYZoneTime;
import static org.mskcc.cbio.oncokb.web.rest.slack.ActionId.*;
import static org.mskcc.cbio.oncokb.web.rest.slack.BlockId.*;


/**
 * Service for sending account approval info to slack.
 * <p>
 * We use the {@link Async} annotation to send slack messages asynchronously.
 */
@Service
public class SlackService {

    private final Logger log = LoggerFactory.getLogger(SlackService.class);

    private final String VALUE_SEPARATOR = "-";
    private final ApplicationProperties applicationProperties;
    private final MailService mailService;
    private final EmailService emailService;
    private final UserMailsService userMailsService;

    @Autowired
    private UserMapper userMapper;

    public SlackService(ApplicationProperties applicationProperties, MailService mailService, EmailService emailService, UserMailsService userMailsService) {
        this.applicationProperties = applicationProperties;
        this.mailService = mailService;
        this.emailService = emailService;
        this.userMailsService = userMailsService;
    }

    @Async
    public void sendUserRegistrationToChannel(UserDTO user, UserStatusChecks userStatusChecks) {
        log.debug("Sending notification to admin group that a user has registered a new account");
        if (StringUtils.isEmpty(this.applicationProperties.getSlack().getUserRegistrationWebhook())) {
            log.debug("\tSkipped, the webhook is not configured");
        } else {
            List<LayoutBlock> layoutBlocks = this.buildBlocks(user, userStatusChecks, null);
            this.sendBlocks(this.applicationProperties.getSlack().getUserRegistrationWebhook(), layoutBlocks);
        }
    }

    @Async
    public void sendLatestBlocks(String url, UserDTO userDTO, UserStatusChecks userStatusChecks, BlockActionPayload blockActionPayload) {
        this.sendBlocks(url, this.buildBlocks(userDTO, userStatusChecks, blockActionPayload));
    }

    @Async
    public void sendApprovedConfirmation(UserDTO userDTO) {
        Payload payload = Payload.builder()
            .text(userDTO.getEmail() + " has been approved and notified automatically")
            .build();

        Slack slack = Slack.getInstance();
        try {
            // This is an automatic message when user from whitelist is registered.
            WebhookResponse response = slack.send(this.applicationProperties.getSlack().getUserRegistrationWebhook(), payload);
        } catch (IOException e) {
            log.warn("Failed to send message to slack");
        }
    }

    @Async
    public void sendConfirmationOnUserAcceptsTrialAgreement(UserDTO userDTO, Instant tokenExpiresOn) {
        String expirationDate = toNYZoneTime(tokenExpiresOn);
        Payload payload = Payload.builder()
            .text(userDTO.getEmail() + " has read and agreed to the trial license agreement. The account has been activated, the trial period ends on " + expirationDate)
            .build();

        Slack slack = Slack.getInstance();
        try {
            WebhookResponse response = slack.send(this.applicationProperties.getSlack().getUserRegistrationWebhook(), payload);

            Context context = new Context();
            context.setVariable(EXPIRATION, expirationDate);
            mailService.sendEmailFromTemplate(
                userDTO,
                MailType.TRIAL_ACCOUNT_IS_ACTIVATED,
                "Trial Account Activated",
                applicationProperties.getEmailAddresses().getLicenseAddress(),
                applicationProperties.getEmailAddresses().getContactAddress(),
                null,
                null,
                context
            );
        } catch (IOException e) {
            log.warn("Failed to send message to slack");
        }
    }

    public List<UserIdMessagePair> getAllUnapprovedUserRequestsSentAfter(int daysAgo) {
        List<UserIdMessagePair> userList = new ArrayList<>();
        Slack slack = Slack.getInstance();
        final String REQUEST_MESSAGE_TEXT = "This content can't be displayed.";

        try {
            String daysAgoTs = Long.toString(Instant.now().getEpochSecond() - DAY_IN_SECONDS * daysAgo);
            ConversationsHistoryResponse conversationsHistory = slack.methods().conversationsHistory(r -> r
                .token(applicationProperties.getSlack().getSlackBotOauthToken())
                .channel(applicationProperties.getSlack().getUserRegistrationChannelId())
                .oldest(daysAgoTs)
                .limit(1000)
                .inclusive(true));
            Collections.reverse(conversationsHistory.getMessages());
            for (Message message : conversationsHistory.getMessages()) {
                if (Objects.nonNull(message.getText()) && message.getText().equals(REQUEST_MESSAGE_TEXT) && Objects.nonNull(message.getBlocks())) {
                    if (!(getBlockWithId(message.getBlocks(), COLLAPSED).isPresent()
                        || getBlockWithId(message.getBlocks(), ACADEMIC_CLARIFICATION_NOTE).isPresent()
                        || getBlockWithId(message.getBlocks(), EMBARGOED_COUNTRY_NOTE).isPresent()
                        || getBlockWithId(message.getBlocks(), MSK_USER_NOTE).isPresent()
                        || getBlockWithId(message.getBlocks(), TRIAL_ACCOUNT_NOTE).isPresent()
                        || getBlockWithId(message.getBlocks(), APPROVED_NOTE).isPresent()
                        || getBlockWithId(message.getBlocks(), CONVERT_TO_REGULAR_ACCOUNT_NOTE).isPresent())
                    ) {
                        if (getBlockWithId(message.getBlocks(), USER_ID).isPresent()) {
                            ContextBlock userIdBlock = (ContextBlock) getBlockWithId(message.getBlocks(), USER_ID).get();
                            PlainTextObject userIdText = (PlainTextObject) userIdBlock.getElements().get(0);
                            Long userId = Long.parseLong(userIdText.getText().substring(9));
                            userList.add(new UserIdMessagePair(userId, message));
                        }
                    }
                }
            }
        } catch (IOException | SlackApiException e) {
            log.error("error: {}", e.getMessage(), e);
        }
        return userList;
    }

    public ActionId getActionId(BlockActionPayload blockActionPayload) {
        List<String> actionIds = blockActionPayload.getActions().stream().map(action -> action.getActionId()).collect(Collectors.toList());
        for (String actionIdStr : actionIds) {
            ActionId actionId = ActionId.getById(actionIdStr);
            if (actionId != null) {
                return actionId;
            }
        }
        return null;
    }

    public Optional<BlockActionPayload.Action> getAction(BlockActionPayload blockActionPayload, ActionId actionKey) {
        return blockActionPayload.getActions().stream().filter(action -> action.getActionId().equalsIgnoreCase(actionKey.getId())).findFirst();
    }

    private TextObject getTextObject(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotEmpty(title)) {
            sb.append(title + ":\n");
        }
        if (StringUtils.isNotEmpty(content)) {
            sb.append("*" + content + "*");
        }
        return MarkdownTextObject.builder().text(sb.toString()).build();
    }

    private void sendBlocks(String url, List<LayoutBlock> layoutBlocks) {
        Payload payload = Payload.builder()
            .blocks(layoutBlocks)
            .build();

        Slack slack = Slack.getInstance();
        try {
            WebhookResponse response = slack.send(url, payload);
            log.info("Send the latest user blocks to slack with response code " + response.getCode());
        } catch (Exception e) {
            log.warn("Failed to send message to slack");
        }
    }

    public List<LayoutBlock> buildBlocks(UserDTO userDTO, UserStatusChecks userStatusChecks, BlockActionPayload responseBlockActionPayload) {
        List<LayoutBlock> blocks = new ArrayList<>();
        ActionId actionId;
        boolean buildCollapsed = false;
        if (responseBlockActionPayload != null) {
            actionId = getActionId(responseBlockActionPayload);
            boolean reviewed = userDTO.isActivated() || userStatusChecks.isReviewed();
            boolean expandedAction = actionId.equals(EXPAND) || (actionId.equals(MORE_ACTIONS) && getActionIdFromMoreActions(responseBlockActionPayload).equals(UPDATE_USER)) || actionId.equals(CHANGE_LICENSE_TYPE);
            buildCollapsed = (reviewed && !expandedAction) || actionId.equals(COLLAPSE);
        }

        if (buildCollapsed) {
            // Add collapsed blocks
            blocks.add(buildCollapsedBlock(userDTO, userStatusChecks));
        } else {
            // Add expanded blocks
            blocks.addAll(buildExpandedBlocks(userDTO, userStatusChecks));
        }

        // Add message divider
        blocks.add(DividerBlock.builder().build());

        return blocks;
    }

    private LayoutBlock buildCollapsedBlock(UserDTO userDTO, UserStatusChecks userStatusChecks) {
        return SectionBlock.builder()
            .text(MarkdownTextObject.builder().text(userDTO.getEmail() + "\n" + userDTO.getCompany() + " (" + userDTO.getLicenseType().getShortName() + (userStatusChecks.isTrialAccount() ? ", *TRIAL*" : userDTO.isActivated() ? "" : ", *NOT ACTIVATED*") + ")").build())
            .accessory(buildExpandButton(userDTO)).blockId(COLLAPSED.getId()).build();
    }

    private List<LayoutBlock> buildExpandedBlocks(UserDTO userDTO, UserStatusChecks userStatusChecks) {
        List<LayoutBlock> blocks = new ArrayList<>();

        // Add mention
        blocks.add(buildHereMentionBlock());

        // Add user id
        blocks.add(buildUserIdBlock(userDTO));

        // Add warning
        blocks.addAll(buildWarningBlocks(userDTO));

        // Add current license
        blocks.add(buildCurrentLicense(userDTO));

        // Add account status
        blocks.add(buildAccountStatusBlock(userDTO, userStatusChecks.isTrialAccountActivated()));

        // Add user info section
        blocks.addAll(buildUserInfoBlocks(userDTO));

        // Add additional info section
        blocks.addAll(buildAdditionalInfoBlocks(userDTO, userStatusChecks));

        // Add action section
        blocks.add(buildActionBlock(userDTO, userStatusChecks));

        return blocks;
    }

    private LayoutBlock buildHereMentionBlock() {
        if (applicationProperties.getProfile() != null && applicationProperties.getProfile().equals(ProjectProfile.PROD)) {
            return SectionBlock.builder().text(MarkdownTextObject.builder().text("<!here>").build()).build();
        } else {
            return SectionBlock.builder().text(PlainTextObject.builder().text("[placeholder for @here handle]").build()).build();
        }
    }

    private LayoutBlock buildChannelMentionBlock() {
        if (applicationProperties.getProfile() != null && applicationProperties.getProfile().equals(ProjectProfile.PROD)) {
            return SectionBlock.builder().text(MarkdownTextObject.builder().text("<!channel>").build()).build();
        } else {
            return SectionBlock.builder().text(PlainTextObject.builder().text("[placeholder for @channel handle]").build()).build();
        }
    }

    private LayoutBlock buildUserIdBlock(UserDTO userDTO) {
        List<ContextBlockElement> elements = new ArrayList<>();
        elements.add(PlainTextObject.builder().text("User ID: " + userDTO.getId()).build());
        return ContextBlock.builder().elements(elements).blockId(USER_ID.getId()).build();
    }

    private List<LayoutBlock> buildWarningBlocks(UserDTO userDTO) {
        List<LayoutBlock> blocks = new ArrayList<>();
        final String LICENSED_DOMAIN_APPROVE_NOTE = ":star: *This email domain belongs to a licensed company. Please review and approve accordingly.*";
        final String TRIALED_DOMAIN_APPROVE_NOTE = ":bangbang: *This email domain belongs to a company that has trial license.*";
        final String EMBARGOED_COUNTRY_NOTE = ":warning: *This account is affiliated with a company located in an embargoed country.*";

        boolean domainIsLicensed = false;
        boolean domainIsTrialed = false;
        boolean inEmbargoedCountry = false;
        List<String> licensedDomains = applicationProperties.getLicensedDomainsList();
        if (!licensedDomains.isEmpty() && licensedDomains.stream().anyMatch(domain -> getEmailDomain(userDTO.getEmail().toLowerCase()).equals(domain.toLowerCase()))) {
            domainIsLicensed = true;
        }
        List<String> trialedDomains = applicationProperties.getTrialedDomainsList();
        if (!trialedDomains.isEmpty() && trialedDomains.stream().anyMatch(domain -> getEmailDomain(userDTO.getEmail().toLowerCase()).equals(domain.toLowerCase()))) {
            domainIsTrialed = true;
        }
        List<String> embargoedCountries = applicationProperties.getEmbargoedCountriesList();
        if (!embargoedCountries.isEmpty() && embargoedCountries.stream().anyMatch(country -> userDTO.getCountry().equalsIgnoreCase(country))) {
            inEmbargoedCountry = true;
        }

        if (domainIsLicensed)
            blocks.add(SectionBlock.builder().text(MarkdownTextObject.builder().text(LICENSED_DOMAIN_APPROVE_NOTE).build()).build());
        if (domainIsTrialed)
            blocks.add(SectionBlock.builder().text(MarkdownTextObject.builder().text(TRIALED_DOMAIN_APPROVE_NOTE).build()).build());
        if (inEmbargoedCountry)
            blocks.add(SectionBlock.builder().text(MarkdownTextObject.builder().text(EMBARGOED_COUNTRY_NOTE).build()).build());

        return blocks;
    }

    private LayoutBlock buildCurrentLicense(UserDTO userDTO) {
        StringBuilder sb = new StringBuilder();
        sb.append("*" + userDTO.getLicenseType().getName() + "*" + (userDTO.getLicenseType().equals(LicenseType.ACADEMIC) ? "" : " :clap:") +"\n");
        if (StringUtils.isNotEmpty(userDTO.getCompany())) {
            sb.append("*" + userDTO.getCompany() + "*");
        }

        return SectionBlock.builder().text(MarkdownTextObject.builder().text(sb.toString()).build()).accessory(getLicenseTypeElement(userDTO)).blockId(LICENSE_TYPE.getId()).build();
    }

    private LayoutBlock buildAccountStatusBlock(UserDTO userDTO, boolean isTrialAccountActivated) {
        List<TextObject> userInfo = new ArrayList<>();

        // Add account information
        userInfo.add(getTextObject("Account Status", userDTO.isActivated() ? "Activated" : (StringUtils.isNotEmpty(userDTO.getActivationKey()) ? "Email not validated" : "Not Activated")));
        userInfo.add(getTextObject("Account Type", isTrialAccountActivated ? "TRIAL" : "REGULAR"));
        if (isTrialAccountActivated) {
            userInfo.add(getTextObject("Trial Expires On", toNYZoneTime(userDTO.getAdditionalInfo().getTrialAccount().getActivation().getActivationDate().plusSeconds(DAY_IN_SECONDS * 90))));
        }
        return SectionBlock.builder().fields(userInfo).blockId(ACCOUNT_STATUS.getId()).build();
    }

    private String getOptionValue(String argument, String login) {
        return String.join(VALUE_SEPARATOR, argument, login);
    }

    public String getOptionValueLogin(String value) {
        String[] values = value.split(VALUE_SEPARATOR);
        return values[1];
    }

    public String getOptionValueArgument(String value) {
        String[] values = value.split(VALUE_SEPARATOR);
        return values[0];
    }

    public boolean withClarificationNote(UserDTO userDTO, boolean sendEmail, boolean newUserActivation) {
        boolean withClarificationNote = false;
        if (userDTO.getLicenseType().equals(LicenseType.ACADEMIC)) {
            if (!this.applicationProperties.getAcademicEmailClarifyDomains().isEmpty() &&
                this.applicationProperties.getAcademicEmailClarifyDomains().stream().filter(domain -> userDTO.getEmail().endsWith(domain)).collect(Collectors.toList()).size() > 0) {
                withClarificationNote = true;
                if (sendEmail) {
                    mailService.sendAcademicClarificationEmail(userDTO);
                }
            } else if (!newUserActivation && !userMailsService.findUserMailsByUserAndMailTypeAndSentDateAfter(userMapper.userDTOToUser(userDTO), MailType.CLARIFY_ACADEMIC_NON_INSTITUTE_EMAIL, null).isEmpty()) {
                withClarificationNote = true;
            }
        }
        return withClarificationNote;
    }

    public boolean withEmbargoNote(UserDTO userDTO) {
        boolean withEmbargoNote = false;
        if (!userMailsService.findUserMailsByUserAndMailTypeAndSentDateAfter(userMapper.userDTOToUser(userDTO), MailType.COMPANY_IS_IN_EMBARGOED_COUNTRY, null).isEmpty()) {
            withEmbargoNote = true;
        }
        return withEmbargoNote;
    }

    private StaticSelectElement getLicenseTypeElement(UserDTO userDTO) {
        List<OptionObject> options = new ArrayList<>();
        for (LicenseType licenseType : LicenseType.values()) {
            if (userDTO.getLicenseType() != licenseType) {
                options.add(OptionObject
                    .builder()
                    .value(this.getOptionValue(licenseType.toString(), userDTO.getLogin()))
                    .text(PlainTextObject.builder().text(licenseType.getName()).build())
                    .build());
            }
        }
        return StaticSelectElement
            .builder()
            .actionId(CHANGE_LICENSE_TYPE.getId())
            .placeholder(PlainTextObject.builder().text("Update license type").build())
            .options(options)
            .build();
    }

    private List<LayoutBlock> buildUserInfoBlocks(UserDTO user) {
        List<LayoutBlock> blocks = new ArrayList<>();
        String companyName = "Company";
        if (user.getLicenseType() != null) {
            if (user.getLicenseType().equals(LicenseType.ACADEMIC)) {
                companyName = "Institute";
            } else if (user.getLicenseType().equals(LicenseType.HOSPITAL)) {
                companyName = "Hospital";
            }
        }

        // Add account information
        List<TextObject> userInfo = new ArrayList<>();
        userInfo.add(MarkdownTextObject.builder().text("Email:\n" + user.getEmail()).build());
        userInfo.add(getTextObject("Name", user.getFirstName() + " " + user.getLastName()));
        userInfo.add(getTextObject("Job Title", user.getJobTitle()));
        userInfo.add(getTextObject(companyName, user.getCompany()));
        blocks.add(SectionBlock.builder().fields(userInfo).blockId(ACCOUNT_INFO.getId()).build());

        // Add company information
        userInfo = new ArrayList<>();
        userInfo.add(getTextObject("City", user.getCity()));
        userInfo.add(getTextObject("Country", user.getCountry()));
        AdditionalInfoDTO additionalInfoDTO = user.getAdditionalInfo();
        if (additionalInfoDTO != null && additionalInfoDTO.getUserCompany() != null) {
            if (StringUtils.isNotEmpty(additionalInfoDTO.getUserCompany().getDescription())) {
                userInfo.add(getTextObject(companyName + " Description", additionalInfoDTO.getUserCompany().getDescription()));
            }
            if (additionalInfoDTO.getUserCompany().getBusinessContact() != null) {
                if (StringUtils.isNotEmpty(additionalInfoDTO.getUserCompany().getBusinessContact().getEmail())) {
                    userInfo.add(MarkdownTextObject.builder().text("Business Contact Email:\n" + additionalInfoDTO.getUserCompany().getBusinessContact().getEmail()).build());
                }
                if (StringUtils.isNotEmpty(additionalInfoDTO.getUserCompany().getBusinessContact().getPhone())) {
                    userInfo.add(MarkdownTextObject.builder().text("Business Contact Phone:\n" + additionalInfoDTO.getUserCompany().getBusinessContact().getPhone()).build());
                }
            }
            if (StringUtils.isNotEmpty(additionalInfoDTO.getUserCompany().getUseCase())) {
                userInfo.add(getTextObject("Use Case", additionalInfoDTO.getUserCompany().getUseCase()));
            }
            if (StringUtils.isNotEmpty(additionalInfoDTO.getUserCompany().getAnticipatedReports())) {
                userInfo.add(getTextObject("Anticipated Reports", additionalInfoDTO.getUserCompany().getAnticipatedReports()));
            }
            if (StringUtils.isNotEmpty(additionalInfoDTO.getUserCompany().getSize())) {
                userInfo.add(getTextObject(companyName + " Size", additionalInfoDTO.getUserCompany().getSize()));
            }
        }
        blocks.add(SectionBlock.builder().fields(userInfo).blockId(ORGANIZATION_INFO.getId()).build());

        return blocks;
    }

    private List<LayoutBlock> buildAdditionalInfoBlocks(UserDTO userDTO, UserStatusChecks userStatusChecks) {
        List<LayoutBlock> layoutBlocks = new ArrayList<>();

        if (withClarificationNote(userDTO, false, false)) {
            layoutBlocks.add(buildPlainTextBlock("We have sent the clarification email to the user asking why they could not use an institution email to register.", ACADEMIC_CLARIFICATION_NOTE));
        }
        if (withEmbargoNote(userDTO)) {
            layoutBlocks.add(buildPlainTextBlock("We have notified the user that they cannot be approved at this time due to United States embargo policy.", EMBARGOED_COUNTRY_NOTE));
        }
        if (isMSKUser(userDTO)) {
            layoutBlocks.add(buildPlainTextBlock("The user has been approved and notified automatically. We also changed their license to Academic and clarified with the user.", MSK_USER_NOTE));
        }
        if (userDTO.isActivated() && !userStatusChecks.isTrialAccountActivated()) {
            if (userStatusChecks.isTrialAccountInitiated()) {
                layoutBlocks.add(buildPlainTextBlock("The user has been approved and notified.", APPROVED_NOTE));
            } else {
                layoutBlocks.add(buildPlainTextBlock("The trial account has been converted to a regular account.", CONVERT_TO_REGULAR_ACCOUNT_NOTE));
            }
        } else if (userStatusChecks.isTrialAccountInitiated()) {
            layoutBlocks.add(buildPlainTextBlock("The trial account has been initialized and notified.", TRIAL_ACCOUNT_NOTE));
        }

        return layoutBlocks;
    }

    private LayoutBlock buildActionBlock(UserDTO userDTO, UserStatusChecks userStatusChecks) {
        List<BlockElement> actionElements = new ArrayList<>();

        // Add button - Approve
        if (!userDTO.isActivated()) {
            actionElements.add(buildApproveButton(userDTO));
        }

        // Add select element - More Actions
        actionElements.add(buildMoreActionsDropdown(userDTO, userStatusChecks));

        return ActionsBlock.builder().elements(actionElements).build();
    }

    private ConfirmationDialogObject buildConfirmationDialogObject(String bodyText) {
        ConfirmationDialogObject confirmationDialogObject = ConfirmationDialogObject.builder()
            .title(PlainTextObject.builder().text("Are you sure?").build())
            .text(PlainTextObject.builder().text(bodyText).build())
            .confirm(PlainTextObject.builder().text("Yes").build())
            .deny(PlainTextObject.builder().text("No").build())
            .build();
        return confirmationDialogObject;
    }

    private ButtonElement buildApproveButton(UserDTO user) {
        ButtonElement button = buildPrimaryButton("Approve", user.getLogin(), APPROVE_USER);
        if (user.getLicenseType() != LicenseType.ACADEMIC) {
            button.setConfirm(buildConfirmationDialogObject("You are going to approve a commercial account."));
        }
        return button;
    }

    private StaticSelectElement buildMoreActionsDropdown(UserDTO user, UserStatusChecks userStatusChecks) {
        List<OptionGroupObject> optionGroups = new ArrayList<>();

        if (user.getLicenseType() != LicenseType.ACADEMIC) {
            // Add option group - Trial
            List<OptionObject> trialGroup = new ArrayList<>();
            // Add option - Give Trial Access
            if (!userStatusChecks.isTrialAccountInitiated() && !user.isActivated()) {
                trialGroup.add(buildGiveTrialAccessOption(user));
            }
            // Add option - Convert to regular
            if (userStatusChecks.isTrialAccountActivated()) {
                trialGroup.add(buildConvertToRegularAccountOption(user));
            }
            if (!trialGroup.isEmpty()) {
                optionGroups.add(OptionGroupObject.builder().label(PlainTextObject.builder().text("Trial").build()).options(trialGroup).build());
            }
        }

        // Add option group - Clarify
        List<OptionObject> clarifyGroup = new ArrayList<>();
        // Add option - Send Academic Clarification Email
        clarifyGroup.add(buildAcademicClarificationOption(user));
        optionGroups.add(OptionGroupObject.builder().label(PlainTextObject.builder().text("Clarify").build()).options(clarifyGroup).build());

        // Add option group - Deny
        List<OptionObject> denyGroup = new ArrayList<>();
        // Add option - Send Embargo Email
        denyGroup.add(buildEmbargoEmailOption(user));
        optionGroups.add(OptionGroupObject.builder().label(PlainTextObject.builder().text("Deny").build()).options(denyGroup).build());

        // Add option group - Other
        List<OptionObject> otherGroup = new ArrayList<>();
        // Add option - Collapse
        otherGroup.add(buildCollapseOption(user));
        // Add option - Update Info
        otherGroup.add(buildUpdateUserOption(user));
        optionGroups.add(OptionGroupObject.builder().label(PlainTextObject.builder().text("Other").build()).options(otherGroup).build());

        StaticSelectElement dropdown = StaticSelectElement.builder().actionId(MORE_ACTIONS.getId()).placeholder(PlainTextObject.builder().text("More Actions").build()).optionGroups(optionGroups).build();
        dropdown.setConfirm(buildConfirmationDialogObject("Please check that the correct action has been selected."));
        return dropdown;
    }

    private OptionObject buildGiveTrialAccessOption(UserDTO user) {
        return OptionObject.builder().value(getOptionValue(GIVE_TRIAL_ACCESS.toString(), user.getLogin())).text(PlainTextObject.builder().text("Give Trial Access").build()).build();
    }

    private OptionObject buildConvertToRegularAccountOption(UserDTO user) {
        return OptionObject.builder().value(getOptionValue(CONVERT_TO_REGULAR_ACCOUNT.toString(), user.getLogin())).text(PlainTextObject.builder().text("Convert To Regular Account").build()).build();
    }

    private OptionObject buildAcademicClarificationOption(UserDTO user) {
        return OptionObject.builder().value(getOptionValue(SEND_ACADEMIC_CLARIFICATION_EMAIL.toString(), user.getLogin())).text(PlainTextObject.builder().text("Send Academic Domain Clarification Email").build()).build();
    }

    private OptionObject buildEmbargoEmailOption(UserDTO user) {
        return OptionObject.builder().value(getOptionValue(SEND_EMBARGO_EMAIL.toString(), user.getLogin())).text(PlainTextObject.builder().text("Send Embargoed Country Email").build()).build();
    }

    private OptionObject buildCollapseOption(UserDTO user) {
        return OptionObject.builder().value(getOptionValue(COLLAPSE.toString(), user.getLogin())).text(PlainTextObject.builder().text("Collapse").build()).build();
    }

    private OptionObject buildUpdateUserOption(UserDTO user) {
        return OptionObject.builder().value(getOptionValue(UPDATE_USER.toString(), user.getLogin())).text(PlainTextObject.builder().text("Update Info Above").build()).build();
    }

    public ActionId getActionIdFromMoreActions(BlockActionPayload blockActionPayload) {
        if (blockActionPayload != null) {
            BlockActionPayload.Action action = getAction(blockActionPayload, MORE_ACTIONS).orElse(null);
            return ActionId.valueOf(getOptionValueArgument(action.getSelectedOption().getValue()));
        }
        return null;
    }

    private ButtonElement buildExpandButton(UserDTO user) {
        return buildButton("Expand", user.getLogin(), EXPAND);
    }

    private ButtonElement buildPrimaryButton(String text, String value, ActionId actionId) {
        return buildButton(text, value, actionId, ButtonStyle.PRIMARY);
    }

    private ButtonElement buildDangerButton(String text, String value, ActionId actionId) {
        return buildButton(text, value, actionId, ButtonStyle.DANGER);
    }

    private ButtonElement buildButton(String text, String value, ActionId actionId) {
        return buildButton(text, value, actionId, null);
    }

    private ButtonElement buildButton(String text, String value, ActionId actionId, ButtonStyle buttonStyle) {
        ButtonElement button = ButtonElement.builder()
            .text(PlainTextObject.builder().emoji(true).text(text).build())
            .actionId(actionId.getId())
            .value(value)
            .build();
        if (buttonStyle != null) {
            button.setStyle(buttonStyle.getStyle());
        }
        return button;
    }

    private LayoutBlock buildPlainTextBlock(String text, BlockId blockId) {
        return SectionBlock.builder().text(PlainTextObject.builder().text(text).build()).blockId(blockId.getId()).build();
    }

    private Optional<LayoutBlock> getBlockWithId(List<LayoutBlock> blocks, BlockId blockId) {
        for (LayoutBlock block : blocks) {
            if (block.getClass().getName().equals("com.slack.api.model.block.SectionBlock")) {
                SectionBlock sectionBlock = (SectionBlock) block;
                if (Objects.nonNull(sectionBlock.getBlockId()) && sectionBlock.getBlockId().equals(blockId.getId())) {
                    return Optional.of(block);
                }
            } else if (block.getClass().getName().equals("com.slack.api.model.block.ContextBlock")) {
                ContextBlock contextBlock = (ContextBlock) block;
                if (Objects.nonNull(contextBlock.getBlockId()) && contextBlock.getBlockId().equals(blockId.getId())) {
                    return Optional.of(block);
                }
            }
        }
        return Optional.empty();
    }
}

enum ButtonStyle {
    PRIMARY("primary"), DANGER("danger");
    String style;

    ButtonStyle(String style) {
        this.style = style;
    }

    public String getStyle() {
        return style;
    }
}
