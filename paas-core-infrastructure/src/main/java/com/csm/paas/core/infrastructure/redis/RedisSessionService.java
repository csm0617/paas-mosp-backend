package com.csm.paas.core.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 操作封装（xfg 约束：Redis 操作只放 redis/，不放 config/）。
 * <p>只做读写，不含业务判断。
 */
@Component
@RequiredArgsConstructor
public class RedisSessionService {

    private final StringRedisTemplate redisTemplate;

    public void setWithTtl(String key, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public long rightPush(String key, String value) {
        Long size = redisTemplate.opsForList().rightPush(key, value);
        return size == null ? 0L : size;
    }

    public String leftPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    public long listSize(String key) {
        Long size = redisTemplate.opsForList().size(key);
        return size == null ? 0L : size;
    }

    public void removeFromList(String key, String value) {
        redisTemplate.opsForList().remove(key, 0, value);
    }

    public void expire(String key, long ttlSeconds) {
        redisTemplate.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    public List<String> scan(String pattern) {
        return redisTemplate.keys(pattern).stream().toList();
    }
}
