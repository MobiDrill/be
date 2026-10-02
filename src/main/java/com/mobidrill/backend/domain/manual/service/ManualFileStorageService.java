package com.mobidrill.backend.domain.manual.service;

import com.mobidrill.backend.domain.manual.config.ManualStorageProperties;
import com.mobidrill.backend.domain.manual.exception.ManualErrorCode;
import com.mobidrill.backend.domain.manual.service.module.InspectedManualFile;
import com.mobidrill.backend.domain.manual.service.module.StoredManualFile;
import com.mobidrill.backend.global.exception.CustomException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualFileStorageService {

    private final ManualStorageProperties properties;

    /**
     * 업로드 파일을 스트림으로 임시 저장하고 실제 바이트 수를 제한한다.
     * @param file : 업로드한 원본 파일
     * @return : 임시 저장 경로
     */
    public Path stage(MultipartFile file) {
        log.info("[ManualFileStorageService] 임시 파일 저장 | stage() - START");
        if (file == null) {
            throw new CustomException(ManualErrorCode.FILE_REQUIRED);
        }
        if (file.isEmpty()) {
            throw new CustomException(ManualErrorCode.EMPTY_FILE);
        }
        long limit = properties.getMaxFileSize().toBytes();
        if (file.getSize() > limit) {
            throw new CustomException(ManualErrorCode.FILE_TOO_LARGE);
        }
        Path staged = null;
        /*
            1. 임시 파일 저장
            - 전체 파일을 메모리에 올리지 않고 실제 읽은 크기를 기준으로 제한한다.
         */
        try {
            Path stagingDirectory = Path.of(properties.getRoot()).toAbsolutePath().normalize().resolve(".staging");
            Files.createDirectories(stagingDirectory);
            staged = Files.createTempFile(stagingDirectory, "manual-", ".upload");
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(staged)) {
                byte[] buffer = new byte[8192];
                long size = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    size += count;
                    if (size > limit) {
                        throw new CustomException(ManualErrorCode.FILE_TOO_LARGE);
                    }
                    output.write(buffer, 0, count);
                }
                if (size == 0) {
                    throw new CustomException(ManualErrorCode.EMPTY_FILE);
                }
            }
            log.info("[ManualFileStorageService] 임시 파일 저장 | stage() - END");
            return staged;
        } catch (IOException exception) {
            delete(staged);
            log.error("[ManualFileStorageService] 임시 파일 저장 실패", exception);
            throw new CustomException(ManualErrorCode.FILE_STORAGE_FAILED);
        } catch (RuntimeException exception) {
            delete(staged);
            throw exception;
        }
    }

    /**
     * 임시 파일을 고유 키와 판별된 확장자를 가진 최종 파일로 저장한다.
     * @param staged : 임시 파일 경로
     * @param originalName : 원본 이름
     * @param inspected : 실제 형식 판별 결과
     * @return : DB 저장에 사용할 파일 메타데이터
     */
    public StoredManualFile promote(Path staged, String originalName, InspectedManualFile inspected) {
        log.info("[ManualFileStorageService] 최종 파일 저장 | promote() - START");
        /*
            1. 최종 파일 생성
            - 기존 파일을 덮어쓰지 않고 키 충돌 시 새 UUID로 재시도한다.
         */
        Path root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
        for (int attempt = 0; attempt < 5; attempt++) {
            String storageKey = UUID.randomUUID().toString().replace("-", "");
            Path destination = root.resolve(storageKey + "." + inspected.extension());
            try {
                Files.copy(staged, destination);
                StoredManualFile result = new StoredManualFile(destination, storageKey, originalName,
                        inspected.fileType(), Files.size(destination));
                log.info("[ManualFileStorageService] 최종 파일 저장 | promote() - END | storageKey: {}", storageKey);
                return result;
            } catch (FileAlreadyExistsException exception) {
                log.warn("[ManualFileStorageService] 저장 키 충돌 | storageKey: {}", storageKey);
            } catch (IOException exception) {
                delete(destination);
                log.error("[ManualFileStorageService] 최종 파일 저장 실패", exception);
                throw new CustomException(ManualErrorCode.FILE_STORAGE_FAILED);
            }
        }
        throw new CustomException(ManualErrorCode.FILE_STORAGE_FAILED);
    }

    /**
     * 저장 경로 내부의 파일을 정리하며 삭제 실패는 로그로 남긴다.
     * @param path : 삭제할 파일 경로 (null이면 생략)
     */
    public void delete(Path path) {
        log.info("[ManualFileStorageService] 파일 정리 | delete() - START");
        if (path != null) {
            Path root = Path.of(properties.getRoot()).toAbsolutePath().normalize();
            Path target = path.toAbsolutePath().normalize();
            if (target.equals(root) || !target.startsWith(root)) {
                throw new CustomException(ManualErrorCode.FILE_STORAGE_FAILED);
            }
            try {
                Files.deleteIfExists(target);
            } catch (IOException exception) {
                log.error("[ManualFileStorageService] 잔여 파일 정리 필요 | path: {}", target, exception);
            }
        }
        log.info("[ManualFileStorageService] 파일 정리 | delete() - END");
    }
}
