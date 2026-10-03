package com.mobidrill.backend.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "훈련 분야 수정 요청")
public record TrainingFieldUpdateReqDto(
        @NotBlank @Size(max = 100) String name,
        @NotNull Boolean isActive
) {
}
