package com.csm.paas.core.infrastructure.adapter.repository;

import com.csm.paas.core.domain.user.adapter.repository.IUserDefaultAvatarRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 读厂商的默认头像资源（`classpath:icon/avatar`，契约数据，见
 * `paas-core-trigger/src/main/resources/icon/README.md`）。
 * <p>厂商同款行为：读不到时**返回 null**（`UserConfig.getDefaultAvatar()` catch 后 return null）。
 */
@Slf4j
@Repository
public class UserDefaultAvatarRepository implements IUserDefaultAvatarRepository {

    private static final String DEFAULT_AVATAR_RESOURCE = "icon/avatar";

    @Override
    public String queryDefaultAvatar() {
        try (InputStream in = new ClassPathResource(DEFAULT_AVATAR_RESOURCE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取默认头像失败", e);
            return null;
        }
    }
}
