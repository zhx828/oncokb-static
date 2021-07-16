package org.mskcc.cbio.oncokb.web.rest;


import org.mskcc.cbio.oncokb.config.application.ApplicationProperties;
import org.mskcc.cbio.oncokb.domain.Token;
import org.mskcc.cbio.oncokb.domain.User;
import org.mskcc.cbio.oncokb.domain.UserStatusChecks;
import org.mskcc.cbio.oncokb.domain.enumeration.LicenseType;
import org.mskcc.cbio.oncokb.domain.enumeration.MailType;
import org.mskcc.cbio.oncokb.repository.UserRepository;
import org.mskcc.cbio.oncokb.security.SecurityUtils;
import org.mskcc.cbio.oncokb.security.uuid.TokenProvider;
import org.mskcc.cbio.oncokb.service.*;
import org.mskcc.cbio.oncokb.service.dto.PasswordChangeDTO;
import org.mskcc.cbio.oncokb.service.dto.UserDTO;
import org.mskcc.cbio.oncokb.service.dto.UserDetailsDTO;
import org.mskcc.cbio.oncokb.service.mapper.UserMapper;
import org.mskcc.cbio.oncokb.web.rest.errors.*;
import org.mskcc.cbio.oncokb.web.rest.errors.EmailAlreadyUsedException;
import org.mskcc.cbio.oncokb.web.rest.errors.InvalidPasswordException;
import org.mskcc.cbio.oncokb.web.rest.vm.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.context.Context;

import javax.naming.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.time.Instant;
import java.util.*;

import static org.mskcc.cbio.oncokb.config.Constants.MAIL_LICENSE;
import static org.mskcc.cbio.oncokb.util.MainUtil.isMSKUser;
import static org.mskcc.cbio.oncokb.util.StringUtil.getEmailDomain;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private static class AccountResourceException extends RuntimeException {
        private AccountResourceException(String message) {
            super(message);
        }
    }

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    private final UserRepository userRepository;

    private final UserService userService;

    private final UserDetailsService userDetailsService;

    private final SlackService slackService;

    private final EmailService emailService;

    private final TokenService tokenService;

    private final PasswordEncoder passwordEncoder;

    private final ApplicationProperties applicationProperties;

    @Autowired
    private UserMapper userMapper;

    private final MailService mailService;

    private final TokenProvider tokenProvider;

    public AccountResource(UserRepository userRepository, UserService userService,
                           MailService mailService, TokenProvider tokenProvider,
                           SlackService slackService, EmailService emailService,
                           AuthenticationManagerBuilder authenticationManagerBuilder,
                           PasswordEncoder passwordEncoder, UserDetailsService userDetailsService,
                           TokenService tokenService, ApplicationProperties applicationProperties
                           ) {

        this.userRepository = userRepository;
        this.userDetailsService = userDetailsService;
        this.userService = userService;
        this.mailService = mailService;
        this.tokenProvider = tokenProvider;
        this.slackService = slackService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.applicationProperties = applicationProperties;
    }

    /**
     * {@code POST  /register} : register the user.
     *
     * @param managedUserVM the managed user View Model.
     * @throws InvalidPasswordException  {@code 400 (Bad Request)} if the password is incorrect.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws LoginAlreadyUsedException {@code 400 (Bad Request)} if the login is already used.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody ManagedUserVM managedUserVM) {
        if (!checkPasswordLength(managedUserVM.getPassword())) {
            throw new InvalidPasswordException();
        }
        User user = userService.registerUser(managedUserVM, managedUserVM.getPassword());
        mailService.sendActivationEmail(userMapper.userToUserDTO(user));
    }

    /**
     * {@code GET  /activate} : activate the registered user.
     *
     * @param key the activation key.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be activated.
     */
    @GetMapping("/activate")
    public boolean activateAccount(@RequestParam(value = "key") String key) {
        Optional<User> userOptional = userService.getUserByActivationKey(key);
        if (!userOptional.isPresent()) {
            throw new AccountResourceException("Your user account could not be activated as no user was found associated with this activation key.");
        } else {
            boolean newUserActivation = !userOptional.get().getActivated();
            userOptional = userService.activateRegistration(key);
            User user = userOptional.get();
            if (newUserActivation) {
                if (emailService.getAccountApprovalWhitelistEmailsDomains().contains(getEmailDomain(user.getEmail()))) {
                    Optional<User> existingUser = userRepository.findOneByLogin(user.getLogin());
                    if (existingUser.isPresent()) {
                        UserDTO userDTO = userMapper.userToUserDTO(existingUser.get());
                        if (userDTO.getLicenseType().equals(LicenseType.ACADEMIC)) {
                            userService.approveUser(userDTO);
                            slackService.sendApprovedConfirmation(userMapper.userToUserDTO(userOptional.get()));
                            return true;
                        } else {
                            return activateUser(userDTO);
                        }
                    } else {
                        throw new AccountResourceException("User could not be found");
                    }
                } else {
                    return activateUser(userMapper.userToUserDTO(user));
                }
            } else {
                // This user exists before, we are looking for to extend the expiration date of all tokens associated
                List<Token> userTokens = tokenService.findByUser(user);
                boolean userAccountCanNOTBeExtended = userTokens.stream().filter(token -> !token.isRenewable()).findAny().isPresent();
                if (userAccountCanNOTBeExtended) {
                    throw new AccountResourceException("Your account token is expired and cannot be extended.");
                } else {
                    Instant defaultExpiration = Instant.now().plusSeconds(tokenProvider.EXPIRATION_TIME_IN_SECONDS);
                    tokenService.findByUser(user).forEach(token -> {
                        // if the extended date based on the current token expiration is before the date in 6month, we should use the bigger one
                        Instant expirationBased = token.getExpiration().plusSeconds(tokenProvider.EXPIRATION_TIME_IN_SECONDS);
                        token.setExpiration(expirationBased.isBefore(defaultExpiration) ? defaultExpiration : expirationBased);
                        tokenService.save(token);
                    });
                }
            }
            return true;
        }
    }

    private boolean activateUser(UserDTO userDTO) {
        if (isMSKUser(userDTO)) {
            LicenseType registeredLicenseType = userDTO.getLicenseType();
            userDTO.setLicenseType(LicenseType.ACADEMIC);
            userService.approveUser(userDTO);

            // In this case, we also want to send an email to user to explain
            Context context = new Context();
            context.setVariable(MAIL_LICENSE, registeredLicenseType.getName());
            mailService.sendEmailFromTemplate(userDTO, MailType.APPROVAL_MSK_IN_COMMERCIAL, context);
        }
        slackService.withClarificationNote(userDTO, true, true);
        slackService.sendUserRegistrationToChannel(userDTO, new UserStatusChecks(userDTO,
                userService.trialAccountActivated(userDTO),
                userService.trialAccountInitiated(userDTO),
                slackService.withClarificationNote(userDTO, false, false),
                slackService.withEmbargoNote(userDTO)));
        return false;
    }

    /**
     * {@code GET  /authenticate} : check if the user is authenticated, and return its login.
     *
     * @param request the HTTP request.
     * @return the login if the user is authenticated.
     */
    @GetMapping("/authenticate")
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * {@code GET  /account} : get the current user.
     *
     * @return the current user.
     * @throws RuntimeException {@code 500 (Internal Server Error)} if the user couldn't be returned.
     */
    @GetMapping("/account")
    public UserDTO getAccount() {
        return userService.getUserWithAuthorities()
            .map(user -> userMapper.userToUserDTO(user))
            .orElseThrow(() -> new AccountResourceException("User could not be found"));
    }

    /**
     * {@code POST  /account} : update the current user information.
     *
     * @param userDTO the current user information.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already used.
     * @throws RuntimeException          {@code 500 (Internal Server Error)} if the user login wasn't found.
     */
    @PostMapping("/account")
    public void saveAccount(@Valid @RequestBody UserDTO userDTO) {
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new AccountResourceException("Current user login not found"));
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(userDTO.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getLogin().equalsIgnoreCase(userLogin))) {
            throw new EmailAlreadyUsedException();
        }
        Optional<User> user = userRepository.findOneByLogin(userLogin);
        if (!user.isPresent()) {
            throw new AccountResourceException("User could not be found");
        }
        userService.updateUser(this.userMapper.userToUserDTO(user.get()));
    }

    /**
     * {@code POST  /account/change-password} : changes the current user's password.
     *
     * @param passwordChangeDto current and new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the new password is incorrect.
     */
    @PostMapping(path = "/account/change-password")
    public void changePassword(@RequestBody PasswordChangeDTO passwordChangeDto) {
        if (!checkPasswordLength(passwordChangeDto.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        userService.changePassword(passwordChangeDto.getCurrentPassword(), passwordChangeDto.getNewPassword());
    }

    /**
     * {@code GET  /account/tokens} : get the list of current logged in user's tokens.
     *
     * @return list of tokens
     */
    @GetMapping(path = "/account/tokens")
    public List<Token> getTokens() {
        Optional<String> userLogin = SecurityUtils.getCurrentUserLogin();
        if (userLogin.isPresent()) {
            Optional<User> user = userService.getUserWithAuthoritiesByLogin(userLogin.get());
            if (user.isPresent()) {
                return tokenProvider.getUserTokens(user.get());
            } else {
                throw new AccountResourceException("Cannot find the user");
            }
        } else {
            throw new AccountResourceException("User is not logged in");
        }
    }

    /**
     * {@code POST  /account/tokens} : create a new token for the current user's token.
     *
     * @return the new token
     */
    @PostMapping(path = "/account/tokens")
    public Token createToken() {
        Optional<String> userLogin = SecurityUtils.getCurrentUserLogin();
        if (userLogin.isPresent()) {
            Optional<User> user = userService.getUserWithAuthoritiesByLogin(userLogin.get());
            List<Token> tokens = tokenProvider.getUserTokens(user.get());
            if (tokens.size() >= 1) {
                throw new AccountResourceException("No more than one token can be created");
            } else {
                // if there is a token already available, we should use the same expiration date
                // we only renew the token after validating the account is valid on half year basis
                if (tokens.size() > 0) {
                    return tokenProvider.createTokenForCurrentUserLogin(Optional.of(tokens.iterator().next().getExpiration()), Optional.empty());
                } else {
                    return tokenProvider.createTokenForCurrentUserLogin(Optional.empty(), Optional.empty());
                }
            }
        } else {
            throw new AccountResourceException("User is not logged in");
        }
    }

    /**
     * {@code DELETE  /account/tokens} : create a new token for the current user's token.
     *
     * @return the new token
     */
    @DeleteMapping(path = "/account/tokens")
    public void deleteToken(@RequestBody Token token) throws AuthenticationException {
        Optional<String> userLogin = SecurityUtils.getCurrentUserLogin();
        if (userLogin.isPresent() && token.getUser() != null) {
            if (token.getUser().getLogin().equalsIgnoreCase(userLogin.get())) {
                tokenProvider.expireToken(token);
            } else {
                throw new AuthenticationException("User does not have the permission to update the token requested");
            }
        } else {
            throw new AccountResourceException("User is not logged in");
        }
    }

    /**
     * {@code POST   /account/reset-password/init} : Send an email to reset the password of the user.
     *
     * @param mail the mail of the user.
     */
    @PostMapping(path = "/account/reset-password/init")
    public void requestPasswordReset(@RequestBody String mail) {
        Optional<User> user = userService.requestPasswordReset(mail);
        if (user.isPresent()) {
            mailService.sendPasswordResetMail(userMapper.userToUserDTO(user.get()));
        } else {
            // Pretend the request has been successful to prevent checking which emails really exist
            // but log that an invalid attempt has been made
            log.warn("Password reset requested for non existing mail");
        }
    }

    /**
     * {@code POST   /account/reset-password/finish} : Finish to reset the password of the user.
     *
     * @param keyAndPassword the generated key and the new password.
     * @throws InvalidPasswordException {@code 400 (Bad Request)} if the password is incorrect.
     * @throws RuntimeException         {@code 500 (Internal Server Error)} if the password could not be reset.
     */
    @PostMapping(path = "/account/reset-password/finish")
    public void finishPasswordReset(@RequestBody KeyAndPasswordVM keyAndPassword) {
        if (!checkPasswordLength(keyAndPassword.getNewPassword())) {
            throw new InvalidPasswordException();
        }
        Optional<User> user =
            userService.completePasswordReset(keyAndPassword.getNewPassword(), keyAndPassword.getKey());

        if (!user.isPresent()) {
            throw new AccountResourceException("No user was found for this reset key");
        }
    }

    @PostMapping(path = "/account/generate-reset-key")
    public UserDTO generateResetKey(@RequestBody String mail) {
        Optional<User> user = userService.requestPasswordReset(mail);
        if (user.isPresent()) {
            return userMapper.userToUserDTO(user.get());
        } else {
            throw new AccountResourceException("No user was found");
        }
    }

    @PostMapping(path = "/account/active-trial/init")
    public UserDTO initiateTrialAccountActivation(@RequestBody String mail) {
        Optional<User> user = userService.initiateTrialAccountActivation(mail);
        if (user.isPresent()) {
            return userMapper.userToUserDTO(user.get());
        } else {
            throw new AccountResourceException("No user was found");
        }
    }

    @PostMapping(path = "/account/active-trial/finish")
    public UserDTO finishTrialAccountActivation(@RequestBody KeyAndTermsVM keyAndTermsVM) {
        if (keyAndTermsVM.getReadAndAgreeWithTheTerms() != Boolean.TRUE) {
            throw new AccountResourceException("You have to read and agree with the terms.");
        }
        Optional<UserDetailsDTO> userDetailsDTO = userDetailsService.findOneByTrialActivationKey(keyAndTermsVM.getKey());
        if (userDetailsDTO.isPresent()) {
            Optional<UserDTO> userDTOOptional = userService.finishTrialAccountActivation(keyAndTermsVM.getKey());
            if (userDTOOptional.isPresent()) {
                return userDTOOptional.get();
            }
        }
        throw new AccountResourceException("No user was found for this activation key");
    }

    @GetMapping(path = "/account/active-trial/info")
    public UserDTO getTrialAccountActivationInfo(@RequestParam String key) {
        Optional<UserDetailsDTO> userDetailsDTO = userDetailsService.findOneByTrialActivationKey(key);
        if (userDetailsDTO.isPresent()) {
            Optional<User> userOptional = userRepository.findById(userDetailsDTO.get().getUserId());
            if (userOptional.isPresent()) {
                return userMapper.userToUserDTO(userOptional.get());
            }
        }
        throw new AccountResourceException("No key found");
    }


    @PostMapping(path = "/account/resend-verification")
    public void resendVerification(@RequestBody LoginVM loginVM) {
        Optional<User> userOptional = userService.getUserWithAuthoritiesByLogin(loginVM.getUsername());

        if (userOptional.isPresent() && passwordEncoder.matches(loginVM.getPassword(), userOptional.get().getPassword())) {
            mailService.sendActivationEmail(userMapper.userToUserDTO(userOptional.get()));
        }
    }

    private static boolean checkPasswordLength(String password) {
        return !StringUtils.isEmpty(password) &&
            password.length() >= ManagedUserVM.PASSWORD_MIN_LENGTH &&
            password.length() <= ManagedUserVM.PASSWORD_MAX_LENGTH;
    }
}
