package com.mobidrill.backend.domain.manual.service.module;

import com.mobidrill.backend.domain.manual.enums.ManualFileType;

public record InspectedManualFile(ManualFileType fileType, String extension) {
}
