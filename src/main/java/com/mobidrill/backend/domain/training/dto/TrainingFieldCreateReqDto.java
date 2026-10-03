package com.mobidrill.backend.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "훈련 분야 생성 요청")
public record TrainingFieldCreateReqDto(
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "활성 여부, 생략하면 true") Boolean isActive
) {
}
