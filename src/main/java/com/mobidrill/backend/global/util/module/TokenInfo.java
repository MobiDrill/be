package com.mobidrill.backend.global.util.module;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record TokenInfo(
        String token,
        LocalDateTime expiresAt
) {
}
