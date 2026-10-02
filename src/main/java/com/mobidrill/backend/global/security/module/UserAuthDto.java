package com.mobidrill.backend.global.security.module;

import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.domain.user.enums.UserStatus;

public record UserAuthDto(
        Long userId,
        String email,
        String password,
        UserRole role,
        UserStatus status
) {
}
