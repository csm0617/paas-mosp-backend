package com.csm.paas.core.domain.user;

import com.csm.paas.core.domain.user.adapter.port.IUserInfoBroadcastPort;
import com.csm.paas.core.domain.user.adapter.repository.IUserRepository;
import com.csm.paas.core.domain.user.model.entity.UserEntity;
import com.csm.paas.core.domain.user.service.impl.UserAvatarService;
import com.csm.paas.core.types.exception.WrongArgumentException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `POST /v1/user/update_avatar/{userId}`（厂商 `UserService.updateAvatar:1538-1591`）。
 * <p>**已实测**（`findings/109`）：写入的 avatar 与上传文件 base64 一致；同一文件再传一次时
 * 日志里的 `icon` 从 `CHANGE` 变为 **`UN-CHANGE`**（证明 md5 比较逻辑与厂商一致）；`>200KiB` → 400。
 */
class UserAvatarServiceTest {

    private final IUserRepository userRepository = mock(IUserRepository.class);
    private final IUserInfoBroadcastPort broadcaster = mock(IUserInfoBroadcastPort.class);

    private UserAvatarService service() {
        return new UserAvatarService(userRepository, broadcaster);
    }

    @Test
    void missingUserIsRejected() {
        when(userRepository.queryById("nope")).thenReturn(Optional.empty());

        assertEquals("user.not.found",
                assertThrows(WrongArgumentException.class,
                        () -> service().updateAvatar("nope", "b64", "md5")).getI18nKey());
        verify(broadcaster, never()).broadcastUserInfo(any());
    }

    /** 没有旧头像 ⇒ `CHANGE`（厂商：任一为空即 CHANGE）。 */
    @Test
    void noExistingAvatarYieldsChangeAndUpdatesOnlyAvatar() {
        when(userRepository.queryById("u1")).thenReturn(Optional.of(
                UserEntity.builder().id("u1").username("bob").nickname("尼克").status(1).build()));

        service().updateAvatar("u1", "b64", "md5");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(broadcaster).broadcastUserInfo(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) captor.getValue().get("userInfo");
        assertEquals("CHANGE", info.get("icon"));
        assertEquals("MODIFY", captor.getValue().get("event"));
        verify(userRepository).updateAvatar("u1", "b64");
    }

    /** 旧头像的 md5 与新文件 md5 相同 ⇒ `UN-CHANGE`（这就是实测里"再传一次"的现象）。 */
    @Test
    void sameContentYieldsUnChanged() {
        byte[] content = "same-bytes".getBytes();
        String storedBase64 = Base64.getEncoder().encodeToString(content);
        String md5 = org.springframework.util.DigestUtils.md5DigestAsHex(content);
        when(userRepository.queryById("u1")).thenReturn(Optional.of(
                UserEntity.builder().id("u1").username("bob").avatar(storedBase64).build()));

        service().updateAvatar("u1", storedBase64, md5);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(broadcaster).broadcastUserInfo(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) captor.getValue().get("userInfo");
        assertEquals("UN-CHANGE", info.get("icon"));
    }

    @Test
    void differentContentYieldsChange() {
        when(userRepository.queryById("u1")).thenReturn(Optional.of(
                UserEntity.builder().id("u1").username("bob")
                        .avatar(Base64.getEncoder().encodeToString("old".getBytes())).build()));

        service().updateAvatar("u1", "bmV3", "someOtherMd5");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(broadcaster).broadcastUserInfo(captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> info = (Map<String, Object>) captor.getValue().get("userInfo");
        assertEquals("CHANGE", info.get("icon"));
        verify(userRepository).updateAvatar(anyString(), anyString());
    }
}
