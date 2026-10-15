package com.csm.paas.core.infrastructure.adapter.port;

import com.csm.paas.core.domain.image.adapter.port.IImageUploadProgressPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 镜像上传进度端口实现（Redis）。
 *
 * <p>厂商 `gaop-image-uploader` 用的是 `RedisTemplate&lt;String,String&gt;` 与
 * `RedisTemplate&lt;String,Integer&gt;` 两个模板；本实现统一走 `StringRedisTemplate`
 * （整数以十进制字符串存放，语义等价——厂商那两处读取也都是 `opsForValue().get(...)` 后当 Integer 用）。
 *
 * <p><b>类 C：本环境不可验证</b>（要连 Redis）。
 */
@Component
@RequiredArgsConstructor
public class ImageUploadProgressPort implements IImageUploadProgressPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void setValue(String key, String value, long ttlMinutes) {
        redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(ttlMinutes));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public Integer getInt(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void setInt(String key, int value, long ttlMinutes) {
        redisTemplate.opsForValue().set(key, String.valueOf(value), Duration.ofMinutes(ttlMinutes));
    }

    @Override
    public void incrementInt(String key) {
        redisTemplate.opsForValue().increment(key);
    }
}
