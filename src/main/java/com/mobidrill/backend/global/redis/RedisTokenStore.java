package com.mobidrill.backend.global.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class RedisTokenStore {

    private static final String REFRESH_TOKEN_PREFIX = "RT:";
    private static final String BLACKLIST_PREFIX = "BL:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveRefreshToken(String hashedToken, Long userId, long ttlMillis) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + hashedToken,
                userId.toString(),
                Duration.ofMillis(ttlMillis)
        );
    }

    public Long findRefreshTokenUserId(String hashedToken) {
        String value = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + hashedToken);
        return value == null ? null : Long.valueOf(value);
    }

    public boolean rotateRefreshToken(
            String oldHashedToken,
            String newHashedToken,
            Long userId,
            long ttlMillis
    ) {
        String script = "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                + "redis.call('DEL', KEYS[1]); "
                + "redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2]); "
                + "return 1 else return 0 end";
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        Long result = redisTemplate.execute(
                redisScript,
                List.of(
                        REFRESH_TOKEN_PREFIX + oldHashedToken,
                        REFRESH_TOKEN_PREFIX + newHashedToken
                ),
                userId.toString(),
                String.valueOf(ttlMillis)
        );
        return result != null && result == 1L;
    }

    public void deleteRefreshToken(String hashedToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + hashedToken);
    }

    public void saveBlacklistedToken(String jti, String tokenType, long ttlMillis) {
        redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + jti,
                tokenType,
                Duration.ofMillis(ttlMillis)
        );
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }

}
