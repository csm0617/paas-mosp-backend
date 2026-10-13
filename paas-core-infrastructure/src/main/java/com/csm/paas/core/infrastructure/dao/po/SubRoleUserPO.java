package com.csm.paas.core.infrastructure.dao.po;

import lombok.Data;

/** 子角色关联用户的投影（厂商 `SubRoleUser(userId, userName)`）。 */
@Data
public class SubRoleUserPO {

    private String userId;
    private String userName;
}
