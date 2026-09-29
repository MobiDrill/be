package com.mobidrill.backend.global.util;

import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.security.CustomUserDetails;
import com.mobidrill.backend.global.security.module.JwtTokenType;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import com.mobidrill.backend.global.util.module.TokenInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                60_000L,
                120_000L
        );
        userDetails = new CustomUserDetails(
                new UserAuthDto(1L, "user@example.com", "encoded", UserRole.ROLE_USER)
        );
    }

    @Test
    @DisplayName("Access Token에 사용자와 토큰 타입을 포함한다")
    void 액세스_토큰_생성_성공() {
        // when
        TokenInfo tokenInfo = jwtUtil.createAccessToken(userDetails);

        // then
        assertThat(jwtUtil.getUserIdFromToken(tokenInfo.token())).isEqualTo(1L);
        assertThat(jwtUtil.getTokenType(tokenInfo.token())).isEqualTo(JwtTokenType.ACCESS);
        assertThat(jwtUtil.getJtiFromToken(tokenInfo.token())).isNotBlank();
    }

    @Test
    @DisplayName("Refresh Token은 Refresh 타입으로 생성한다")
    void 리프레시_토큰_생성_성공() {
        // when
        TokenInfo tokenInfo = jwtUtil.createRefreshToken(userDetails);

        // then
        assertThat(jwtUtil.getTokenType(tokenInfo.token())).isEqualTo(JwtTokenType.REFRESH);
    }

    @Test
    @DisplayName("위조된 JWT는 인증에 사용할 수 없다")
    void 위조된_토큰_검증_실패() {
        // given
        String token = jwtUtil.createAccessToken(userDetails).token() + "tampered";

        // when & then
        assertThatThrownBy(() -> jwtUtil.getUserIdFromToken(token))
                .isInstanceOf(CustomException.class);
    }
}
