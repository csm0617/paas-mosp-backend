package com.csm.paas.core.domain.user.service.impl;

import com.csm.paas.core.domain.user.adapter.port.IUserInfoBroadcastPort;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.service.IUserAvatarService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 规则见 {@link IUserAvatarService}。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAvatarService implements IUserAvatarService {

    private final IUserRepository userRepository;
    private final IUserInfoBroadcastPort userInfoBroadcastPort;

    @Override
    public boolean updateAvatar(String userId, String base64, String md5) {
        var user = userRepository.queryById(userId)
                .orElseThrow(() -> new WrongArgumentException("user.not.found"));

        // ③ icon：厂商只在"旧头像与本次 md5 都非空"时才比较
        String icon;
        if (user.getAvatar() != null && !user.getAvatar().isBlank() && md5 != null && !md5.isBlank()) {
            String oldMd5 = DigestUtils.md5DigestAsHex(Base64.getDecoder().decode(user.getAvatar()));
            icon = oldMd5.equals(md5) ? "UN-CHANGE" : "CHANGE";
        } else {
            icon = "CHANGE";
        }

        // ④ 推 WebSocket（厂商：sendStatus 为 true 时推）
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("nickname", user.getNickname());
        userInfo.put("status", user.getStatus());
        userInfo.put("icon", icon);
        Map<String, Object> userStatus = new LinkedHashMap<>();
        userStatus.put("event", "MODIFY");
        userStatus.put("sendStatus", true);
        userStatus.put("userInfo", userInfo);
        try {
            userInfoBroadcastPort.broadcastUserInfo(userStatus);
        } catch (Exception e) {
            log.error("推送 userInfo 失败: {}", e.getMessage());
        }

        // ⑤ 只更新 avatar（厂商 updateByPrimaryKeySelective 只 set 了 id 与 avatar）
        userRepository.updateAvatar(userId, base64);
        return true;
    }
}
