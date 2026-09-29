package com.mobidrill.backend.domain.auth.dto;

import lombok.Builder;

@Builder
public record AuthLoginEmailResDto(
        Long userId,
        String email,
        String name,
        String accessToken,
        String refreshToken
) {
    public static AuthLoginEmailResDto create(
            Long userId,
            String email,
            String name,
            String accessToken,
            String refreshToken
    ) {
        return AuthLoginEmailResDto.builder()
                .userId(userId)
                .email(email)
                .name(name)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }
}
