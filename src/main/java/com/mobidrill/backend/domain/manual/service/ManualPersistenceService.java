package com.mobidrill.backend.domain.manual.service;

import com.mobidrill.backend.domain.auth.exception.AuthErrorCode;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterResDto;
import com.mobidrill.backend.domain.manual.entity.Manual;
import com.mobidrill.backend.domain.manual.entity.ManualFile;
import com.mobidrill.backend.domain.manual.exception.ManualErrorCode;
import com.mobidrill.backend.domain.manual.mapper.ManualMapper;
import com.mobidrill.backend.domain.manual.repository.ManualFileRepository;
import com.mobidrill.backend.domain.manual.repository.ManualRepository;
import com.mobidrill.backend.domain.manual.service.module.InspectedManualFile;
import com.mobidrill.backend.domain.manual.service.module.StoredManualFile;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import com.mobidrill.backend.domain.training.repository.TrainingFieldRepository;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.enums.UserStatus;
import com.mobidrill.backend.domain.user.exception.UserErrorCode;
import com.mobidrill.backend.domain.user.repository.UserRepository;
import com.mobidrill.backend.global.exception.CustomException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualPersistenceService {

    private final ManualRepository manualRepository;
    private final ManualFileRepository manualFileRepository;
    private final TrainingFieldRepository trainingFieldRepository;
    private final UserRepository userRepository;
    private final ManualFileStorageService storageService;
    private final ManualMapper manualMapper;

    /**
     * 교범과 파일 메타데이터를 한 트랜잭션으로 저장하고 롤백 시 원본 파일을 제거한다.
     * @param request : 교범 등록 정보
     * @param userId : JWT의 현재 사용자 ID
     * @param staged : 임시 저장 파일
     * @param originalName : 원본 파일명
     * @param inspected : 실제 파일 형식
     * @return : 등록 결과
     */
    @Transactional
    public ManualRegisterResDto createManualWithFile(ManualRegisterReqDto request, Long userId, Path staged,
                                                    String originalName, InspectedManualFile inspected) {
        log.info("[ManualPersistenceService] 교범 저장 | createManualWithFile() - START | userId: {}", userId);
        /*
            1. 참조 데이터 검증
            - 훈련 분야와 활성 등록자를 확인한다.
         */
        TrainingField field = trainingFieldRepository.findById(request.trainingFieldId())
                .orElseThrow(() -> new CustomException(ManualErrorCode.TRAINING_FIELD_NOT_FOUND));
        if (!Boolean.TRUE.equals(field.getIsActive())) {
            throw new CustomException(ManualErrorCode.TRAINING_FIELD_INACTIVE);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.USER_INACTIVE);
        }
        /*
            2. 최종 파일 저장 및 롤백 보상
            - DB 커밋 단계에서 실패하는 경우에도 파일을 제거한다.
         */
        StoredManualFile stored = storageService.promote(staged, originalName, inspected);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storageService.delete(stored.path());
                } else if (status == STATUS_UNKNOWN) {
                    log.error("[ManualPersistenceService] 트랜잭션 결과 확인 필요 | storageKey: {}", stored.storageKey());
                }
            }
        });
        /*
            3. 교범과 파일 저장
            - 영속화 오류는 전파하여 전체 트랜잭션을 롤백한다.
         */
        Manual manual = manualRepository.save(manualMapper.toManual(request, field, user));
        ManualFile file = manualFileRepository.saveAndFlush(manualMapper.toManualFile(manual, stored));
        ManualRegisterResDto result = manualMapper.toRegisterResDto(manual, file);
        log.info("[ManualPersistenceService] 교범 저장 | createManualWithFile() - END | manualId: {}", manual.getId());
        return result;
    }
}
