package com.mobidrill.backend.domain.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "훈련 분야 정보")
public record TrainingFieldResDto(Long trainingFieldId, String name, Boolean isActive) {
}
