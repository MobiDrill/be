package com.mobidrill.backend.global.util;

import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import com.mobidrill.backend.global.security.CustomUserDetails;
import com.mobidrill.backend.global.security.module.JwtTokenType;
import com.mobidrill.backend.global.util.module.TokenInfo;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;

    public JwtUtil(
            @Value("${jwt.token.secretKey}") String secret,
            @Value("${jwt.access-token-validity-in-ms}") Long accessTokenValidity,
            @Value("${jwt.refresh-token-validity-in-ms}") Long refreshTokenValidity
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = Duration.ofMillis(accessTokenValidity);
        this.refreshExpiration = Duration.ofMillis(refreshTokenValidity);
    }

    public TokenInfo createAccessToken(CustomUserDetails userDetails) {
        return createToken(
                userDetails.getUserAuthDto().userId(),
                userDetails.getUserAuthDto().role(),
                JwtTokenType.ACCESS,
                accessExpiration
        );
    }

    public TokenInfo createRefreshToken(CustomUserDetails userDetails) {
        return createToken(
                userDetails.getUserAuthDto().userId(),
                null,
                JwtTokenType.REFRESH,
                refreshExpiration
        );
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(getPayload(token).getSubject());
    }

    public JwtTokenType getTokenType(String token) {
        return JwtTokenType.valueOf(getPayload(token).get("type", String.class));
    }

    public String getJtiFromToken(String token) {
        return getPayload(token).getId();
    }

    public long getExpirationInMs(String token) {
        return getPayload(token).getExpiration().getTime();
    }

    private TokenInfo createToken(
            Long userId,
            UserRole role,
            JwtTokenType tokenType,
            Duration expiration
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(expiration);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("type", tokenType.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));

        if (role != null) {
            builder.claim("role", role.name());
        }

        return TokenInfo.builder()
                .token(builder.signWith(secretKey).compact())
                .expiresAt(LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()))
                .build();
    }

    private Claims getPayload(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException exception) {
            throw new CustomException(GlobalErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new CustomException(GlobalErrorCode.INVALID_TOKEN);
        }
    }
}
