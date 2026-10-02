package com.mobidrill.backend.domain.manual.dto;

import com.mobidrill.backend.domain.manual.enums.ManualStatus;
import lombok.Builder;

@Builder
public record ManualRegisterResDto(
        Long manualId,
        String manualTitle,
        Long trainingFieldId,
        String manualDescription,
        ManualStatus manualStatus,
        ManualFileResDto file
) {
}
