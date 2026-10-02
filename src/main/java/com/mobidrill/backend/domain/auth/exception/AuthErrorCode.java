package com.mobidrill.backend.domain.auth.exception;

import com.mobidrill.backend.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "비밀번호와 비밀번호 확인이 일치하지 않습니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    TOKEN_STORE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "인증 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."),
    INVALID_LOGIN_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "이미 사용되었거나 폐기된 Refresh Token입니다."),
    TOKEN_USER_MISMATCH(HttpStatus.UNAUTHORIZED, "토큰의 사용자 정보가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String message;
}
