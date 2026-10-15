package com.csm.paas.core.domain.image.adapter.port;

/**
 * 镜像上传进度的键值存储端口（厂商 `gaop-image-uploader` 里对 Redis 的那几笔操作）。
 *
 * <p>厂商出处：`recovered/gaop-image-uploader/.../controller/ImageController.java:101-161`。
 * 它用两个 RedisTemplate 存四类键：
 * <ul>
 *   <li>`imageUploadInfo`：上传进度 JSON（`ImageUploadInfo`，字符串）；</li>
 *   <li>`image_current_count` / `image_current_total` / `image_error_count`：整数计数。</li>
 * </ul>
 * 厂商的 TTL 是 `${timeout}` 分钟（uploader `application.yml:35` 默认 30）。
 *
 * <p>本端口只做"字符串/整数的读写与自增"，**不解析 JSON**——JSON 由 trigger 用 Jackson 组装
 * （domain 模块没有 JSON 库，与 `exportConfigmap` 等既有处理一致）。
 *
 * <p><b>类 C：本环境不可验证</b>——要连 Redis；且上传进度真正要推给的 raw websocket
 * （前端连的是 uploader 独立进程的 `ws://<host>:19528/ws/uploadImage/<username>`）不在本进程端口上。
 */
public interface IImageUploadProgressPort {

    /** 读一个字符串键；不存在返回 null。 */
    String getValue(String key);

    /** 写一个字符串键并设置 TTL（分钟）。 */
    void setValue(String key, String value, long ttlMinutes);

    void delete(String key);

    /** 读一个整数键；不存在返回 null。 */
    Integer getInt(String key);

    /** 写一个整数键并设置 TTL（分钟）。 */
    void setInt(String key, int value, long ttlMinutes);

    /** 整数键自增（厂商 `redisTemplate.opsForValue().increment(key)`）。 */
    void incrementInt(String key);
}
