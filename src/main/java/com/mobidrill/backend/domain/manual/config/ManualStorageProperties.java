package com.mobidrill.backend.domain.manual.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "manual.storage")
public class ManualStorageProperties {

    @NotBlank
    private String root = "./src/main/resources/static/originalManual";
    @NotNull
    private DataSize maxFileSize = DataSize.ofMegabytes(50);

    @AssertTrue(message = "교범 파일 크기 제한은 양수여야 합니다.")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }
}
