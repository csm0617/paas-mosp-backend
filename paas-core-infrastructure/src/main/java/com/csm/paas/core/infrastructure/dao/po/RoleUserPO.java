package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** 战位关联用户的投影（厂商 `RoleUser(userId, userName)`）。 */
@Data
public class RoleUserPO {

    private String userId;
    private String userName;
}
