package com.mobidrill.backend.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 로그인 요청")
public record AuthLoginEmailReqDto(
        @NotBlank
        @Email
        String email,

        @NotBlank
        String password
) {
}
