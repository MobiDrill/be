package com.mobidrill.backend.global.util;

import com.mobidrill.backend.global.redis.RedisTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisUtilTest {

    @Mock
    private RedisTokenStore redisTokenStore;

    @Mock
    private JwtUtil jwtUtil;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        redisUtil = new RedisUtil(redisTokenStore, jwtUtil);
    }

    @Test
    @DisplayName("Refresh Token 원문 대신 SHA-256 해시를 저장한다")
    void 리프레시_토큰_해시_저장_성공() {
        // given
        String refreshToken = "refresh-token";
        when(jwtUtil.getExpirationInMs(refreshToken))
                .thenReturn(System.currentTimeMillis() + 60_000L);
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);

        // when
        redisUtil.saveRefreshToken(refreshToken, 1L);

        // then
        verify(redisTokenStore).saveRefreshToken(hashCaptor.capture(), eq(1L), anyLong());
        assertThat(hashCaptor.getValue()).hasSize(64).isNotEqualTo(refreshToken);
    }

    @Test
    @DisplayName("RedisTokenStore를 통해 Refresh Token을 원자적으로 회전한다")
    void 리프레시_토큰_회전_성공() {
        // given
        String oldToken = "old-refresh-token";
        String newToken = "new-refresh-token";
        when(jwtUtil.getExpirationInMs(newToken))
                .thenReturn(System.currentTimeMillis() + 60_000L);
        when(redisTokenStore.rotateRefreshToken(
                eq(redisUtil.hashRefreshToken(oldToken)),
                eq(redisUtil.hashRefreshToken(newToken)),
                eq(1L),
                anyLong()
        )).thenReturn(true);

        // when
        boolean rotated = redisUtil.rotateRefreshToken(oldToken, newToken, 1L);

        // then
        assertThat(rotated).isTrue();
    }
}
