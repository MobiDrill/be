package com.mobidrill.backend.domain.manual.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "교범 등록 정보")
public record ManualRegisterReqDto(
        @NotBlank @Size(max = 100) String manualTitle,
        @NotNull @Positive Long trainingFieldId,
        @Size(max = 1000) String manualDescription
) {
}
