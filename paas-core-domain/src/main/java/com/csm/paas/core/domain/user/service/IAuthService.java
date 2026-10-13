package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.entity.LoginResultEntity;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;

import java.util.Optional;

/** 认证领域服务：登录、登出、当前会话。 */
public interface IAuthService {

    LoginResultEntity login(String username, String rawPassword, String roleKey);

    void logout(String token);

    Optional<SessionVO> currentSession(String token);

    /**
     * 当前登录态（用户 + 当前战位 + token）。
     * <p>厂商 `GET /v1/user` 返回的就是 `UserLogined`——与登录同一形状（20 键，含 `role`），
     * 前端 `getInitialState` 拿它当 `currentUser`，并用 `currentUser.role.key` 判 `canAdmin`。
     */
    Optional<LoginResultEntity> currentLogin(String token);
}
