package com.mobidrill.backend.domain.auth.controller;

import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailResDto;
import com.mobidrill.backend.domain.auth.dto.AuthLogoutReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenResDto;
import com.mobidrill.backend.domain.auth.dto.AuthRegisterEmailReqDto;
import com.mobidrill.backend.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Auth", description = "이메일 회원가입 및 JWT 인증 API")
@RequestMapping("/api/v1/auth")
public interface AuthControllerDocs {

    @Operation(summary = "이메일 회원가입", description = "이름은 최대 30자, 이메일은 최대 255자입니다. 비밀번호는 8~64자이면서 UTF-8 기준 72바이트 이하이며 확인 값과 일치해야 합니다. 일반 사용자 권한과 활성 상태로 가입합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 완료"),
            @ApiResponse(responseCode = "400", description = "입력값 오류 또는 비밀번호 확인 불일치"),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    })
    @PostMapping("/register/email")
    ResponseEntity<GlobalResponse<Void>> registerEmail(
            @Valid @RequestBody AuthRegisterEmailReqDto request
    );

    @Operation(summary = "이메일 로그인", description = "활성 계정의 이메일과 비밀번호를 검증하고 Access/Refresh Token을 발급합니다. 비밀번호는 UTF-8 기준 72바이트 이하입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 완료"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치"),
            @ApiResponse(responseCode = "403", description = "비활성 계정"),
            @ApiResponse(responseCode = "503", description = "토큰 저장소 연결 실패")
    })
    @PostMapping("/login/email")
    ResponseEntity<GlobalResponse<AuthLoginEmailResDto>> loginEmail(
            @Valid @RequestBody AuthLoginEmailReqDto request
    );

    @Operation(summary = "JWT 재발급")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "토큰 재발급 완료"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "유효하지 않거나 폐기된 토큰"),
            @ApiResponse(responseCode = "403", description = "비활성 계정")
    })
    @PostMapping("/refresh")
    ResponseEntity<GlobalResponse<AuthRefreshTokenResDto>> refreshToken(
            @Valid @RequestBody AuthRefreshTokenReqDto request
    );

    @Operation(summary = "로그아웃")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    ResponseEntity<GlobalResponse<Void>> logout(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userAuthDto.userId", errorOnInvalidType = true) Long userId,
            @Valid @RequestBody AuthLogoutReqDto request
    );
}
