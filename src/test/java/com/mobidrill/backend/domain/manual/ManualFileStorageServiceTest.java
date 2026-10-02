package com.mobidrill.backend.domain.manual;

import com.mobidrill.backend.domain.manual.config.ManualStorageProperties;
import com.mobidrill.backend.domain.manual.enums.ManualFileType;
import com.mobidrill.backend.domain.manual.exception.ManualErrorCode;
import com.mobidrill.backend.domain.manual.service.ManualFileInspectionService;
import com.mobidrill.backend.domain.manual.service.ManualFileStorageService;
import com.mobidrill.backend.domain.manual.service.module.InspectedManualFile;
import com.mobidrill.backend.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualFileStorageServiceTest {

    @TempDir
    Path root;
    private ManualStorageProperties properties;
    private ManualFileStorageService storage;
    private final ManualFileInspectionService inspection = new ManualFileInspectionService();

    @BeforeEach
    void setUp() {
        properties = new ManualStorageProperties();
        properties.setRoot(root.toString());
        storage = new ManualFileStorageService(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "ppt", "pptx", "doc", "docx", "hwp"})
    @DisplayName("파일 내용으로 형식을 판별하고 키와 소문자 확장자로 원본 바이트를 보존한다")
    void 형식_판별과_저장_성공(String extension) throws Exception {
        // given
        byte[] bytes = ManualFileFixtures.file(extension);
        String originalName = "교범." + extension.toUpperCase();
        Path staged = storage.stage(new MockMultipartFile("file", originalName, "application/octet-stream", bytes));

        // when
        var inspected = inspection.inspect(staged, originalName);
        var stored = storage.promote(staged, originalName, inspected);
        storage.delete(staged);

        // then
        assertThat(stored.storageKey()).matches("[0-9a-f]{32}");
        assertThat(stored.path().getFileName().toString()).isEqualTo(stored.storageKey() + "." + extension);
        assertThat(Files.readAllBytes(stored.path())).isEqualTo(bytes);
        assertThat(stored.sizeBytes()).isEqualTo(bytes.length);
        assertThat(stored.originalName()).isEqualTo(originalName);
        assertThat(inspected.extension()).isEqualTo(extension);
    }

    @Test
    @DisplayName("PDF를 PPT로 위장한 파일과 일반 ZIP을 거부한다")
    void 확장자_위장과_일반_ZIP_실패() throws Exception {
        // given
        Path pdf = Files.write(root.resolve("pdf.upload"), ManualFileFixtures.file("pdf"));
        var output = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(output)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zip.write(new byte[]{1, 2, 3});
            zip.closeEntry();
        }
        Path archive = Files.write(root.resolve("zip.upload"), output.toByteArray());

        // when & then
        assertThatThrownBy(() -> inspection.inspect(pdf, "교범.ppt"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ManualErrorCode.FILE_TYPE_MISMATCH);
        assertThatThrownBy(() -> inspection.inspect(archive, "교범.docx"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ManualErrorCode.INVALID_MANUAL_FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../교범.pdf", "C:\\교범.pdf", "교범.exe", "교범.pptm", "교범.hwpx", "교범.pdf\n"})
    @DisplayName("경로 문자와 허용하지 않는 확장자를 거부한다")
    void 파일명_검증_실패(String name) {
        // when & then
        assertThatThrownBy(() -> inspection.validateFileName(name)).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("파일 제한 경계는 허용하고 실제 스트림이 제한을 넘으면 부분 파일을 제거한다")
    void 파일_크기_경계와_부분파일_정리_성공() throws Exception {
        // given
        properties.setMaxFileSize(DataSize.ofBytes(4));
        Path exact = storage.stage(new MockMultipartFile("file", "교범.pdf", null, new byte[4]));
        storage.delete(exact);
        MockMultipartFile understated = new MockMultipartFile("file", "교범.pdf", null, new byte[4]) {
            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(new byte[5]);
            }
        };

        // when & then
        assertThatThrownBy(() -> storage.stage(understated)).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ManualErrorCode.FILE_TOO_LARGE);
        try (var files = Files.list(root.resolve(".staging"))) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    @DisplayName("원본 읽기 실패 시 부분 임시 파일을 제거한다")
    void 읽기_실패시_임시파일_정리_성공() throws Exception {
        // given
        MockMultipartFile unreadable = new MockMultipartFile("file", "교범.pdf", null, new byte[4]) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new IOException("읽기 실패");
            }
        };

        // when & then
        assertThatThrownBy(() -> storage.stage(unreadable)).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ManualErrorCode.FILE_STORAGE_FAILED);
        try (var files = Files.list(root.resolve(".staging"))) {
            assertThat(files.count()).isZero();
        }
    }

    @Test
    @DisplayName("HWP FileHeader 이름만 있는 OLE 파일은 HWP로 인정하지 않는다")
    void HWP_서명_없는_OLE_파일_실패() throws Exception {
        // given
        Path fake = root.resolve("fake.upload");
        try (var container = new org.apache.poi.poifs.filesystem.POIFSFileSystem()) {
            container.createDocument(new ByteArrayInputStream(new byte[256]), "FileHeader");
            container.createDocument(new ByteArrayInputStream(new byte[4]), "DocInfo");
            container.getRoot().createDirectory("BodyText");
            try (var output = Files.newOutputStream(fake)) {
                container.writeFilesystem(output);
            }
        }

        // when & then
        assertThatThrownBy(() -> inspection.inspect(fake, "manual.hwp")).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ManualErrorCode.INVALID_MANUAL_FILE);
    }

    @Test
    @DisplayName("파일 정리는 설정된 저장 경로 밖의 파일을 삭제하지 않는다")
    void 저장_경로_외부_삭제_실패(@TempDir Path outside) throws Exception {
        // given
        Path file = Files.write(outside.resolve("keep.pdf"), new byte[]{1});

        // when & then
        assertThatThrownBy(() -> storage.delete(file)).isInstanceOf(CustomException.class);
        assertThat(Files.readAllBytes(file)).containsExactly((byte) 1);
    }

    @Test
    @DisplayName("없는 임시 파일의 최종 저장 실패는 원본 파일을 남기지 않는다")
    void 최종_저장_실패시_파일_정리_성공() throws Exception {
        // when & then
        assertThatThrownBy(() -> storage.promote(root.resolve("missing"), "교범.pdf",
                new InspectedManualFile(ManualFileType.PDF, "pdf"))).isInstanceOf(CustomException.class);
        try (var files = Files.list(root)) {
            assertThat(files.count()).isZero();
        }
    }

}
