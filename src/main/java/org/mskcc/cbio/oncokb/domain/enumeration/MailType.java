package org.mskcc.cbio.oncokb.domain.enumeration;

/**
 * The MailType enumeration.
 */
public enum MailType {
    ACTIVATION("activationEmail", "User Activation", null)
    , APPROVAL("approvalEmail", "User Approval", null)
    , CREATION("creationEmail", "Account Creation", null)
    , PASSWORD_RESET("passwordResetEmail", "Reset Password", null)
    , LICENSE_REVIEW_COMMERCIAL("licenseReview", "License Review - Commercial", null)
    , LICENSE_REVIEW_RESEARCH_COMMERCIAL("licenseReview", "License Review - Research in Commercial", null)
    , LICENSE_REVIEW_HOSPITAL("licenseReview", "License Review - Hospital", null)
    , CLARIFY_ACADEMIC_FOR_PROFIT("clarifyLicenseInForProfileCompany", "Clarify - Requested academic license from a for-profit company", null)
    , CLARIFY_ACADEMIC_NON_INSTITUTE_EMAIL("clarifyAcademicUseWithoutInstituteEmail", "Clarify - Requested academic license from a non-institute email", null)
    , COMPANY_IS_IN_EMBARGOED_COUNTRY("companyIsInEmbargoedCountry", "Notify - Unable to approve to due embargo", null)
    , VERIFY_EMAIL_BEFORE_ACCOUNT_EXPIRES("verifyEmailBeforeAccountExpires", "Verify user still owns the email address", null)
    , APPROVAL_MSK_IN_COMMERCIAL("approvalMSKInCommercial", "Autocorrect MSK user license to academic", null)
    , TRIAL_ACCOUNT_IS_ABOUT_TO_EXPIRE("trialAccountIsAboutToExpire", "Trail account is about to expire", null)
    , TRIAL_ACCOUNT_IS_ACTIVATED("trialAccountIsActivated", "Trail Account is Activated", null)
    , ACTIVATE_FREE_TRIAL("activateFreeTrial", "OncoKB Trial Activation Link", null)
    , TOKEN_HAS_BEEN_EXPOSED("tokenHasBeenExposed", "Token has been exposed", null)
    , TOKEN_HAS_BEEN_EXPOSED_USER("tokenHasBeenExposedToUser", "Token has been exposed", null)
    , SEARCHING_RESPONSE_STRUCTURE_HAS_CHANGED("searchingResponseStructureHasChanged", "Searching Response Structure Has Changed", null)
    , LIST_OF_UNAPPROVED_USERS("listOfUnapprovedUsers", "List of unapproved users", null)
    , TEST("testEmail", "Test", null)
    ;

    String templateName;
    String description;
    String attachmentFileNames;

    MailType(String templateName, String description, String attachmentFileNames) {
        this.templateName = templateName;
        this.description = description;
        this.attachmentFileNames = attachmentFileNames;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getDescription() {
        return description;
    }

    public String getAttachmentFileNames() {
        return attachmentFileNames;
    }
}
