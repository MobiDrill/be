package com.mobidrill.backend.global.security;

import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 검증된 현재 인증 사용자의 ID를 반환한다.
     * @return : JWT 인증 사용자 ID
     */
    public static Long getCurrentUserId() {
        return getCurrentUserDetails().getUserAuthDto().userId();
    }

    /**
     * JWT 필터가 설정한 인증 사용자 정보를 반환한다.
     * @return : 인증된 사용자 정보
     */
    public static CustomUserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userDetails;
    }
}
