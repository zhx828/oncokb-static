package org.mskcc.cbio.oncokb.web.rest.slack;

/**
 * Created by Benjamin Xu on 6/30/21.
 */
public enum BlockId {
    USER_ID("user-id")
    , LICENSE_TYPE("license-type")
    , ACCOUNT_STATUS("account-status")
    , ACCOUNT_INFO("account-info")
    , ORGANIZATION_INFO("organization-info")
    , ACADEMIC_CLARIFICATION_NOTE("academic-clarification-note")
    , EMBARGOED_COUNTRY_NOTE("embargoed-country-note")
    , MSK_USER_NOTE("msk-user-note")
    , APPROVED_NOTE("approved-note")
    , TRIAL_ACCOUNT_NOTE("trial-account-note")
    , CONVERT_TO_REGULAR_ACCOUNT_NOTE("convert-to-regular-account-note")
    , COLLAPSED("collapsed")
    ;

    String id;

    BlockId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static BlockId getById(String id) {
        for (BlockId blockId : BlockId.values()) {
            if (blockId.getId().equals(id)) {
                return blockId;
            }
        }
        return null;
    }
}
