package com.mobidrill.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshTokenReqDto(
        @NotBlank String refreshToken
) {
}
