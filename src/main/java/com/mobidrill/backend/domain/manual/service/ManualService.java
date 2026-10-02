package com.mobidrill.backend.domain.manual.service;

import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterResDto;
import com.mobidrill.backend.domain.manual.service.module.InspectedManualFile;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualService {

    private final ManualFileStorageService storageService;
    private final ManualFileInspectionService inspectionService;
    private final ManualPersistenceService persistenceService;

    /**
     * 현재 사용자의 교범과 원본 파일을 등록한다.
     * @param userId : Controller에서 전달한 인증 사용자 ID
     * @param request : 교범 정보
     * @param file : 원본 파일
     * @return : 커밋된 교범 등록 결과
     */
    public ManualRegisterResDto registerManual(Long userId, ManualRegisterReqDto request, MultipartFile file) {
        log.info("[ManualService] 교범 등록 | registerManual() - START | userId: {}", userId);
        /*
            1. 파일 검증 및 임시 저장
            - 최종 경로에 저장하기 전에 실제 형식을 확인한다.
         */
        if (file != null) {
            inspectionService.validateFileName(file.getOriginalFilename());
        }
        Path staged = storageService.stage(file);
        try {
            InspectedManualFile inspected = inspectionService.inspect(staged, file.getOriginalFilename());
            /*
                2. 교범 저장
                - 별도 프록시의 트랜잭션 커밋까지 완료한 뒤 결과를 반환한다.
             */
            ManualRegisterResDto result = persistenceService.createManualWithFile(
                    request, userId, staged, file.getOriginalFilename(), inspected);
            log.info("[ManualService] 교범 등록 | registerManual() - END | manualId: {}", result.manualId());
            return result;
        } finally {
            storageService.delete(staged);
        }
    }
}
