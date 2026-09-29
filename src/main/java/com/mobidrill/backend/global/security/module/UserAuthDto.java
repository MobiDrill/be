package com.mobidrill.backend.global.security.module;

import com.mobidrill.backend.domain.user.enums.UserRole;

public record UserAuthDto(
        Long userId,
        String email,
        String password,
        UserRole role
) {
}
