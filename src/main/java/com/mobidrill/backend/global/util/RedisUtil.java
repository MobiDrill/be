package com.mobidrill.backend.global.util;

import com.mobidrill.backend.global.redis.RedisTokenStore;
import com.mobidrill.backend.global.security.module.JwtTokenType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RedisUtil {

    private final RedisTokenStore redisTokenStore;
    private final JwtUtil jwtUtil;

    public RedisUtil(RedisTokenStore redisTokenStore, JwtUtil jwtUtil) {
        this.redisTokenStore = redisTokenStore;
        this.jwtUtil = jwtUtil;
    }

    public void saveRefreshToken(String refreshToken, Long userId) {
        long ttlMillis = remainingTtl(refreshToken);
        redisTokenStore.saveRefreshToken(hashRefreshToken(refreshToken), userId, ttlMillis);
    }

    public Long findRefreshTokenUserId(String refreshToken) {
        return redisTokenStore.findRefreshTokenUserId(hashRefreshToken(refreshToken));
    }

    public boolean rotateRefreshToken(String oldRefreshToken, String newRefreshToken, Long userId) {
        return redisTokenStore.rotateRefreshToken(
                hashRefreshToken(oldRefreshToken),
                hashRefreshToken(newRefreshToken),
                userId,
                remainingTtl(newRefreshToken)
        );
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTokenStore.deleteRefreshToken(hashRefreshToken(refreshToken));
    }

    public void blacklistAccessToken(String accessToken) {
        long ttlMillis = remainingTtl(accessToken);
        if (ttlMillis <= 0) {
            return;
        }
        redisTokenStore.saveBlacklistedToken(
                jwtUtil.getJtiFromToken(accessToken),
                JwtTokenType.ACCESS.name(),
                ttlMillis
        );
    }

    public boolean isAccessTokenBlacklisted(String accessToken) {
        return redisTokenStore.isBlacklisted(jwtUtil.getJtiFromToken(accessToken));
    }

    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    messageDigest.digest(refreshToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private long remainingTtl(String token) {
        return Math.max(jwtUtil.getExpirationInMs(token) - System.currentTimeMillis(), 1L);
    }
}
