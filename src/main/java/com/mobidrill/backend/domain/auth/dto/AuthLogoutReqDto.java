package com.mobidrill.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthLogoutReqDto(
        @NotBlank String accessToken,
        @NotBlank String refreshToken
) {
}
