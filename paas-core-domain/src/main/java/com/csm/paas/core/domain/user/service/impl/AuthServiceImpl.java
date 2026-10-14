package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.role.adapter.repository.IRoleRepository;
import com.csm.paas.core.domain.role.model.entity.RoleEntity;
import com.csm.paas.core.domain.setting.adapter.repository.ISettingRepository;
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
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 同一 (用户,战位) 允许的并发会话数。厂商默认 **1**（`application.yml:125 max-login: ${MAX_LOGIN:1}`），
     * 超出时会把最早的会话键 DEL 掉——**这是单点登录语义**，重写版必须一致，否则切换后并发行为会变。
     */
    @Value("${gaop.auth.max-login:1}")
    private long maxLogin = 1L;

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
        userSessionRepository.save(token, session, ttlSeconds, maxLogin);
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
    public Optional<LoginResultEntity> currentLogin(String token) {
        return currentSession(token).flatMap(session -> {
            UserEntity user = userRepository.queryByUsername(session.getUsername()).orElse(null);
            RoleEntity role = roleRepository.queryByKey(session.getRoleKey()).orElse(null);
            if (user == null) {
                return Optional.empty();
            }
            boolean isAdmin = "admin".equals(role == null ? null : role.getKey());
            boolean isDev = "dev".equals(role == null ? null : role.getKey());
            if (role != null) {
                role.setAdmin(isAdmin);
                role.setDev(isDev);
                role.setAdminOrDev(isAdmin || isDev);
            }
            return Optional.of(LoginResultEntity.builder()
                    .user(UserEntity.builder()
                            .id(user.getId()).username(user.getUsername()).password(null).status(user.getStatus())
                            .email(user.getEmail()).birth(user.getBirth()).phone(user.getPhone())
                            .updatedAt(user.getUpdatedAt()).createdAt(user.getCreatedAt())
                            .lastLoginRole(session.getRoleKey()).nickname(user.getNickname())
                            .uuid(user.getUuid()).desc(user.getDesc()).avatar(user.getAvatar())
                            .build())
                    .role(role)
                    // **不设 token**：厂商 `GET /v1/user`（当前登录用户）走的是另一条构造路径，
                    // 实测 `data.token` 为 **null**（只有 `login` 会 `setToken`，见 UserService.loginedUser）。
                    // 见 findings/94 的 B11。
                    .build());
        });
    }

    @Override
    public Optional<SessionVO> currentSession(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return userSessionRepository.find(token);
    }
}
