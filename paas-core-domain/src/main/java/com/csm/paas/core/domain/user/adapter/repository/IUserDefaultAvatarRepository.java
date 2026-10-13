package com.csm.paas.core.domain.user.adapter.repository;

/**
 * 用户默认头像的读取端口（实现在 infrastructure，读 classpath 上的厂商资源 `icon/avatar`）。
 * <p>为什么要开端口：那是**资源文件**不是数据库，domain 层不该直接读 classpath。
 * <p>厂商出处：`UserConfig.getDefaultAvatar()`（`config/UserConfig.java:31`）——
 * `@Value("classpath:icon/avatar")` 读全文当默认头像。
 */
public interface IUserDefaultAvatarRepository {

    /** 默认头像内容（厂商原样的 base64 串）；读不到时返回 null（厂商也是 catch 后返回 null）。 */
    String queryDefaultAvatar();
}
