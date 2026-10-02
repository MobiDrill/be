package com.mobidrill.backend.domain.manual.service;

import com.mobidrill.backend.domain.manual.enums.ManualFileType;
import com.mobidrill.backend.domain.manual.exception.ManualErrorCode;
import com.mobidrill.backend.domain.manual.service.module.InspectedManualFile;
import com.mobidrill.backend.global.exception.CustomException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.microsoft.POIFSContainerDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ManualFileInspectionService {

    private static final Map<String, String> MIME_EXTENSIONS = Map.of(
            "application/pdf", "pdf",
            "application/vnd.ms-powerpoint", "ppt",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx",
            "application/msword", "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
            "application/x-hwp", "hwp",
            "application/x-hwp-v5", "hwp",
            "application/vnd.hancom.hwp", "hwp");
    private static final Map<String, ManualFileType> FILE_TYPES = Map.of(
            "pdf", ManualFileType.PDF, "ppt", ManualFileType.PPT, "pptx", ManualFileType.PPT,
            "doc", ManualFileType.WORD, "docx", ManualFileType.WORD, "hwp", ManualFileType.HWP);

    /**
     * 원본 이름을 검증하고 허용된 확장자를 반환한다.
     * @param originalName : 사용자가 올린 파일명
     * @return : 소문자로 정규화된 확장자
     */
    public String validateFileName(String originalName) {
        log.info("[ManualFileInspectionService] 파일명 검증 | validateFileName() - START");
        /*
            1. 파일명과 확장자 검증
            - 경로 문자, 제어 문자, 과도한 길이와 지원하지 않는 확장자를 거부한다.
         */
        if (originalName == null || originalName.isBlank() || originalName.length() > 500
                || originalName.matches(".*[\\\\/:*?\"<>|].*")
                || originalName.chars().anyMatch(Character::isISOControl)) {
            throw new CustomException(ManualErrorCode.INVALID_FILE_NAME);
        }
        int separator = originalName.lastIndexOf('.');
        if (separator <= 0 || separator == originalName.length() - 1) {
            throw new CustomException(ManualErrorCode.INVALID_FILE_NAME);
        }
        String extension = originalName.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!FILE_TYPES.containsKey(extension)) {
            throw new CustomException(ManualErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        log.info("[ManualFileInspectionService] 파일명 검증 | validateFileName() - END | extension: {}", extension);
        return extension;
    }

    /**
     * 파일 내용으로 실제 형식을 판별하고 원본 확장자와 일치하는지 검증한다.
     * @param path : 임시 저장한 원본 파일 경로
     * @param originalName : 원본 파일명
     * @return : 실제 파일 형식과 저장 확장자
     */
    public InspectedManualFile inspect(Path path, String originalName) {
        log.info("[ManualFileInspectionService] 실제 형식 판별 | inspect() - START");
        String extension = validateFileName(originalName);
        /*
            1. 실제 내용 판별
            - 파일명과 클라이언트 Content-Type 힌트를 배제하고 Office 컨테이너 내부를 판별한다.
         */
        try (TikaInputStream stream = TikaInputStream.get(path)) {
            String mimeType = new DefaultDetector().detect(stream, new Metadata()).toString();
            String actualExtension = MIME_EXTENSIONS.get(mimeType);
            if (actualExtension == null && POIFSContainerDetector.OLE.toString().equals(mimeType)) {
                actualExtension = detectHwp(path);
            }
            if (actualExtension == null) {
                throw new CustomException(ManualErrorCode.INVALID_MANUAL_FILE);
            }
            if (!extension.equals(actualExtension)) {
                throw new CustomException(ManualErrorCode.FILE_TYPE_MISMATCH);
            }
            InspectedManualFile result = new InspectedManualFile(FILE_TYPES.get(extension), actualExtension);
            log.info("[ManualFileInspectionService] 실제 형식 판별 | inspect() - END | mimeType: {}", mimeType);
            return result;
        } catch (IOException exception) {
            log.warn("[ManualFileInspectionService] 파일 판별 실패", exception);
            throw new CustomException(ManualErrorCode.INVALID_MANUAL_FILE);
        }
    }

    /**
     * HWP 5 OLE 컨테이너의 FileHeader 서명과 필수 구조로 형식을 판별한다.
     * - 한컴 공개 규격에 따라 파일명 대신 256바이트 헤더와 문서 버전을 확인한다.
     * @param path : OLE 형식 원본 경로
     * @return : HWP이면 hwp, 다른 OLE 문서이면 null
     * @throws IOException : 컨테이너를 읽을 수 없는 경우
     */
    private String detectHwp(Path path) throws IOException {
        log.debug("[ManualFileInspectionService] HWP 서명 확인 | detectHwp() - START");
        String result = null;
        try (POIFSFileSystem container = new POIFSFileSystem(path.toFile(), true)) {
            var root = container.getRoot();
            if (root.hasEntry("FileHeader") && root.hasEntry("DocInfo")
                    && (root.hasEntry("BodyText") || root.hasEntry("ViewText"))) {
                try (var input = container.createDocumentInputStream("FileHeader")) {
                    byte[] header = input.readNBytes(256);
                    byte[] signature = Arrays.copyOf("HWP Document File".getBytes(StandardCharsets.US_ASCII), 32);
                    if (header.length == 256 && Arrays.equals(Arrays.copyOf(header, 32), signature)
                            && Byte.toUnsignedInt(header[35]) == 5) {
                        result = "hwp";
                    }
                }
            }
        }
        log.debug("[ManualFileInspectionService] HWP 서명 확인 | detectHwp() - END | result: {}", result);
        return result;
    }
}
