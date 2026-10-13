package com.csm.paas.core.domain.user.service;

import com.csm.paas.core.domain.user.model.entity.LoginResultEntity;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;

import java.util.Optional;

/** 认证领域服务：登录、登出、当前会话。 */
public interface IAuthService {

    LoginResultEntity login(String username, String rawPassword, String roleKey);

    void logout(String token);

    Optional<SessionVO> currentSession(String token);
}
