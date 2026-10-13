package com.csm.paas.core.domain.user.model.entity;

import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import lombok.Builder;
import lombok.Data;

/** 登录结果：用户 + 当前战位 + 会话 token。 */
@Data
@Builder
public class LoginResultEntity {
    private UserEntity user;
    private RoleEntity role;
    private String token;
    /** 会话有效期（秒），来自 g_setting.system.permission.expired_time。 */
    private long ttlSeconds;
}
