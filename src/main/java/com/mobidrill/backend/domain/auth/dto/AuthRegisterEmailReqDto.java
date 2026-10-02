package com.mobidrill.backend.domain.auth.dto;

import com.mobidrill.backend.domain.auth.validation.MaxPasswordBytes;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 회원가입 요청")
public record AuthRegisterEmailReqDto(
        @NotBlank
        @Size(max = 30)
        String name,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Size(min = 8, max = 64)
        @MaxPasswordBytes
        String password,

        @NotBlank
        @Size(min = 8, max = 64)
        @MaxPasswordBytes
        String passwordConfirm
) {
}
