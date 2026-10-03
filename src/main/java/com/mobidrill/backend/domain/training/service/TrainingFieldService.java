package com.mobidrill.backend.domain.training.service;

import com.mobidrill.backend.domain.manual.repository.ManualRepository;
import com.mobidrill.backend.domain.training.dto.TrainingFieldCreateReqDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldResDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldUpdateReqDto;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import com.mobidrill.backend.domain.training.exception.TrainingFieldErrorCode;
import com.mobidrill.backend.domain.training.mapper.TrainingFieldMapper;
import com.mobidrill.backend.domain.training.repository.TrainingFieldRepository;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.response.CursorPageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainingFieldService {

    private final TrainingFieldRepository trainingFieldRepository;
    private final ManualRepository manualRepository;
    private final TrainingFieldMapper trainingFieldMapper;

    /**
     * 이름과 활성 상태로 훈련 분야를 생성한다.
     * @param request : 생성 정보
     * @return : 생성한 분야
     */
    @Transactional
    public TrainingFieldResDto createTrainingField(TrainingFieldCreateReqDto request) {
        log.info("[TrainingFieldService] 분야 생성 | createTrainingField() - START");
        /*
            1. 분야 생성
            - 검증된 입력을 정규화하고 활성 상태 기본값을 적용해 저장한다.
         */
        TrainingField field = trainingFieldRepository.save(trainingFieldMapper.toTrainingField(request));
        TrainingFieldResDto result = trainingFieldMapper.toResDto(field);
        log.info("[TrainingFieldService] 분야 생성 | createTrainingField() - END | fieldId: {}", field.getId());
        return result;
    }

    /**
     * 활성·비활성 훈련 분야를 ID 내림차순 페이지로 조회한다.
     * @param page : 1부터 시작하는 페이지 번호
     * @param size : 페이지 크기 (1~100)
     * @return : 페이지 목록과 전체 개수
     */
    @Transactional(readOnly = true)
    public CursorPageResponse<TrainingFieldResDto> getTrainingFields(int page, int size) {
        log.info("[TrainingFieldService] 분야 목록 | getTrainingFields() - START | page: {}, size: {}", page, size);
        /*
            1. 페이지 검증 및 조회
            - 외부 페이지 번호를 0 기반으로 변환하며 커서는 사용하지 않는다.
         */
        if (page < 1 || size < 1 || size > 100) {
            throw new CustomException(TrainingFieldErrorCode.INVALID_PAGINATION);
        }
        var fields = trainingFieldRepository.findAll(PageRequest.of(page - 1, size, Sort.by("id").descending()));
        var result = CursorPageResponse.of(fields, trainingFieldMapper::toResDto, null);
        log.info("[TrainingFieldService] 분야 목록 | getTrainingFields() - END | total: {}", result.totalElements());
        return result;
    }

    /**
     * 교범 등록·삭제와 같은 배타 잠금을 획득해 분야를 수정한다.
     * @param id : 수정할 분야 ID
     * @param request : 이름과 활성 여부
     * @return : 수정된 분야
     */
    @Transactional
    public TrainingFieldResDto updateTrainingField(Long id, TrainingFieldUpdateReqDto request) {
        log.info("[TrainingFieldService] 분야 수정 | updateTrainingField() - START | fieldId: {}", id);
        /*
            1. 잠금 및 수정
            - 비활성 변경은 기존 교범과의 연결을 유지한다.
         */
        TrainingField field = findLockedField(id);
        field.update(request.name().strip(), request.isActive());
        TrainingFieldResDto result = trainingFieldMapper.toResDto(field);
        log.info("[TrainingFieldService] 분야 수정 | updateTrainingField() - END | fieldId: {}", id);
        return result;
    }

    /**
     * 교범 연결을 먼저 해제한 후 분야를 삭제하고 실패 시 모두 롤백한다.
     * @param id : 삭제할 분야 ID
     */
    @Transactional
    public void deleteTrainingField(Long id) {
        log.info("[TrainingFieldService] 분야 삭제 | deleteTrainingField() - START | fieldId: {}", id);
        /*
            1. 잠금 및 교범 연결 해제
            - 분야 행의 배타 잠금을 커밋까지 유지하며 교범 FK만 null로 변경한다.
         */
        findLockedField(id);
        int detachedCount = manualRepository.clearTrainingField(id);
        /*
            2. 분야 삭제
            - 일괄 UPDATE로 초기화된 영속성 컨텍스트에서 다시 조회해 삭제한다.
         */
        trainingFieldRepository.deleteById(id);
        trainingFieldRepository.flush();
        log.info("[TrainingFieldService] 분야 삭제 | deleteTrainingField() - END | fieldId: {}, detached: {}", id, detachedCount);
    }

    /**
     * 양수 ID를 검증하고 분야의 배타 잠금을 획득한다.
     * @param id : 분야 ID
     * @return : 잠금한 분야
     */
    private TrainingField findLockedField(Long id) {
        log.debug("[TrainingFieldService] 분야 잠금 | findLockedField() - START | fieldId: {}", id);
        if (id == null || id <= 0) {
            throw new CustomException(TrainingFieldErrorCode.INVALID_TRAINING_FIELD_ID);
        }
        TrainingField field = trainingFieldRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(TrainingFieldErrorCode.TRAINING_FIELD_NOT_FOUND));
        log.debug("[TrainingFieldService] 분야 잠금 | findLockedField() - END | fieldId: {}", id);
        return field;
    }
}
