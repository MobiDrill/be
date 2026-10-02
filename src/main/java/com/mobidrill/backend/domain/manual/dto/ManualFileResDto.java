package com.mobidrill.backend.domain.manual.dto;

import com.mobidrill.backend.domain.manual.enums.ManualFileType;
import lombok.Builder;

@Builder
public record ManualFileResDto(
        Long manualFileId,
        String originalName,
        ManualFileType fileType,
        String storageKey,
        String storedFileName,
        Long sizeBytes
) {
}
