package com.mobidrill.backend.domain.auth.controller;

import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailResDto;
import com.mobidrill.backend.domain.auth.dto.AuthLogoutReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenResDto;
import com.mobidrill.backend.domain.auth.dto.AuthRegisterEmailReqDto;
import com.mobidrill.backend.domain.auth.service.AuthService;
import com.mobidrill.backend.global.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    @Override
    public ResponseEntity<GlobalResponse<Void>> registerEmail(AuthRegisterEmailReqDto request) {
        authService.registerEmail(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalResponse.success(201, "회원가입이 완료되었습니다.", null));
    }

    @Override
    public ResponseEntity<GlobalResponse<AuthLoginEmailResDto>> loginEmail(AuthLoginEmailReqDto request) {
        return ResponseEntity.ok(GlobalResponse.success("로그인이 완료되었습니다.", authService.loginEmail(request)));
    }

    @Override
    public ResponseEntity<GlobalResponse<AuthRefreshTokenResDto>> refreshToken(AuthRefreshTokenReqDto request) {
        return ResponseEntity.ok(GlobalResponse.success("토큰이 재발급되었습니다.", authService.refreshToken(request)));
    }

    @Override
    public ResponseEntity<GlobalResponse<Void>> logout(
            @AuthenticationPrincipal(expression = "userAuthDto.userId", errorOnInvalidType = true) Long userId,
            AuthLogoutReqDto request) {
        authService.logout(userId, request);
        return ResponseEntity.ok(GlobalResponse.success("로그아웃이 완료되었습니다.", null));
    }
}
