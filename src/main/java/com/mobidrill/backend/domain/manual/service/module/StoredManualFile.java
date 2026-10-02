package com.mobidrill.backend.domain.manual.service.module;

import com.mobidrill.backend.domain.manual.enums.ManualFileType;
import java.nio.file.Path;

public record StoredManualFile(
        Path path, String storageKey, String originalName, ManualFileType fileType, long sizeBytes
) {
}
