package com.mobidrill.backend.global.security;

import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new CustomException(GlobalErrorCode.UNAUTHORIZED);
        }
        return userDetails.getUserAuthDto().userId();
    }
}
