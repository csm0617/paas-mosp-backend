package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.user.adapter.repository.ISettingRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.adapter.repository.IUserSessionRepository;
import com.csm.paas.core.domain.user.model.entity.LoginResultEntity;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.model.valobj.SessionVO;
import com.csm.paas.core.domain.user.service.IAuthService;
import com.csm.paas.core.types.enums.ResponseCode;
import com.csm.paas.core.types.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 认证领域服务。
 * <p>契约照抄厂商（见 docs/findings/12-auth-contract.md）：
 * BCrypt 校验口令（`UserService.java:331`）→ token = UUID（`:353`）→ 会话键 `gateway-token:<token>` 值 `<user>:<战位>`；
 * 有效期读 `g_setting.system.permission.expired_time`（分钟，默认 30）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private static final String EXPIRED_TIME_SETTING = "system.permission.expired_time";
    private static final int DEFAULT_EXPIRED_MINUTES = 30;

    private final IUserRepository userRepository;
    private final IRoleRepository roleRepository;
    private final IUserSessionRepository userSessionRepository;
    private final ISettingRepository settingRepository;

    @Override
    public LoginResultEntity login(String username, String rawPassword, String roleKey) {
        UserEntity user = userRepository.queryByUsername(username)
                .orElseThrow(() -> new BusinessException(ResponseCode.UNAUTHENTICATED, "用户名或密码错误"));
        if (!BCrypt.checkpw(rawPassword, user.getPassword())) {
            throw new BusinessException(ResponseCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        RoleEntity role = roleRepository.queryByKey(roleKey)
                .orElseThrow(() -> new BusinessException(ResponseCode.UNAUTHENTICATED, "战位不存在: " + roleKey));

        String token = UUID.randomUUID().toString();
        int expiredMinutes = settingRepository.queryIntValue(EXPIRED_TIME_SETTING, DEFAULT_EXPIRED_MINUTES);
        long ttlSeconds = expiredMinutes * 60L;
        SessionVO session = SessionVO.builder().username(username).roleKey(roleKey).build();
        userSessionRepository.save(token, session, ttlSeconds, 0L);
        log.info("登录成功 username:{} 战位:{} ttl:{}s", username, roleKey, ttlSeconds);

        UserEntity safeUser = UserEntity.builder()
                .id(user.getId()).username(user.getUsername()).password(null).status(user.getStatus())
                .email(user.getEmail()).birth(user.getBirth()).phone(user.getPhone())
                .updatedAt(user.getUpdatedAt()).createdAt(user.getCreatedAt())
                .lastLoginRole(roleKey).nickname(user.getNickname()).uuid(user.getUuid())
                .desc(user.getDesc()).avatar(user.getAvatar())
                .build();
        return LoginResultEntity.builder().user(safeUser).role(role).token(token).ttlSeconds(ttlSeconds).build();
    }

    @Override
    public void logout(String token) {
        if (token == null) {
            return;
        }
        userSessionRepository.find(token).ifPresent(session -> userSessionRepository.delete(token, session));
    }

    @Override
    public Optional<SessionVO> currentSession(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return userSessionRepository.find(token);
    }
}
