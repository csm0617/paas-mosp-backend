package com.csm.paas.core.domain.user.model.valobj;

import lombok.Builder;
import lombok.Data;

/** 登录页账号下拉所需的轻量用户项（厂商 GET /v1/user/get 的三键）。 */
@Data
@Builder
public class UserLoginItemVO {
    private String username;
    private String lastLoginRole;
    private String avatar;
}
