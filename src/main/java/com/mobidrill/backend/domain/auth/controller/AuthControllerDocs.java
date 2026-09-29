package com.mobidrill.backend.domain.auth.controller;

import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailResDto;
import com.mobidrill.backend.domain.auth.dto.AuthLogoutReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenResDto;
import com.mobidrill.backend.domain.auth.dto.AuthRegisterEmailReqDto;
import com.mobidrill.backend.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth", description = "이메일 회원가입 및 JWT 인증 API")
@RequestMapping("/api/v1/auth")
public interface AuthControllerDocs {

    @Operation(summary = "이메일 회원가입")
    @PostMapping("/register/email")
    ResponseEntity<GlobalResponse<Void>> registerEmail(
            @Valid @RequestBody AuthRegisterEmailReqDto request
    );

    @Operation(summary = "이메일 로그인")
    @PostMapping("/login/email")
    ResponseEntity<GlobalResponse<AuthLoginEmailResDto>> loginEmail(
            @Valid @RequestBody AuthLoginEmailReqDto request
    );

    @Operation(summary = "JWT 재발급")
    @PostMapping("/refresh")
    ResponseEntity<GlobalResponse<AuthRefreshTokenResDto>> refreshToken(
            @Valid @RequestBody AuthRefreshTokenReqDto request
    );

    @Operation(summary = "로그아웃")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    ResponseEntity<GlobalResponse<Void>> logout(
            @Valid @RequestBody AuthLogoutReqDto request
    );
}
