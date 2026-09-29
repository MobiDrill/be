package com.mobidrill.backend.domain.auth.dto;

import lombok.Builder;

@Builder
public record AuthRefreshTokenResDto(
        String accessToken,
        String refreshToken
) {
    public static AuthRefreshTokenResDto create(String accessToken, String refreshToken) {
        return AuthRefreshTokenResDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
