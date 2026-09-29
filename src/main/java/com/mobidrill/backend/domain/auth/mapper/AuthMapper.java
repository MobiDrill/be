package com.mobidrill.backend.domain.auth.mapper;

import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailResDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenResDto;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.global.util.module.TokenInfo;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    /**
     * 사용자와 발급 토큰을 로그인 응답 DTO로 변환한다.
     * @param user : 로그인 사용자
     * @param accessToken : 발급한 Access Token 정보
     * @param refreshToken : 발급한 Refresh Token 정보
     * @return : 로그인 응답 DTO
     */
    public AuthLoginEmailResDto toLoginResDto(User user, TokenInfo accessToken, TokenInfo refreshToken) {
        return AuthLoginEmailResDto.create(
                user.getId(),
                user.getEmail(),
                user.getName(),
                accessToken.token(),
                refreshToken.token()
        );
    }

    /**
     * 새로 발급한 토큰을 재발급 응답 DTO로 변환한다.
     * @param accessToken : 새 Access Token 정보
     * @param refreshToken : 새 Refresh Token 정보
     * @return : 토큰 재발급 응답 DTO
     */
    public AuthRefreshTokenResDto toRefreshResDto(TokenInfo accessToken, TokenInfo refreshToken) {
        return AuthRefreshTokenResDto.create(accessToken.token(), refreshToken.token());
    }
}
