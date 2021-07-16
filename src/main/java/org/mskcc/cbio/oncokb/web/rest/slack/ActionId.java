package org.mskcc.cbio.oncokb.web.rest.slack;


/**
 * Created by Hongxin Zhang on 4/30/21.
 */
public enum ActionId {
    APPROVE_USER("approve-user")
    , CHANGE_LICENSE_TYPE("change-license-type")
    , GIVE_TRIAL_ACCESS("give-trial-access")
    , CONVERT_TO_REGULAR_ACCOUNT("convert-to-regular-account")
    , COLLAPSE("collapse")
    , EXPAND("expand")
    , MORE_ACTIONS("more-actions")
    , SEND_EMBARGO_EMAIL("send-embargo-email")
    , SEND_ACADEMIC_CLARIFICATION_EMAIL("send-academic-clarification-email")
    , UPDATE_USER("update-user")
    ;

    String id;

    ActionId(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static ActionId getById(String id) {
        for (ActionId actionId : ActionId.values()) {
            if (actionId.getId().equals(id)) {
                return actionId;
            }
        }
        return null;
    }
}
