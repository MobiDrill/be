package com.mobidrill.backend.domain.training.mapper;

import com.mobidrill.backend.domain.training.dto.TrainingFieldCreateReqDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldResDto;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import org.springframework.stereotype.Component;

@Component
public class TrainingFieldMapper {

    /**
     * 생성 요청을 훈련 분야로 변환하며 기본 활성 상태를 적용한다.
     * @param request : 생성 요청
     * @return : 저장할 훈련 분야
     */
    public TrainingField toTrainingField(TrainingFieldCreateReqDto request) {
        return TrainingField.builder().name(request.name().strip())
                .isActive(request.isActive() == null || request.isActive()).build();
    }

    /**
     * 훈련 분야를 응답으로 변환한다.
     * @param field : 훈련 분야
     * @return : 분야 정보 응답
     */
    public TrainingFieldResDto toResDto(TrainingField field) {
        return TrainingFieldResDto.builder().trainingFieldId(field.getId())
                .name(field.getName()).isActive(field.getIsActive()).build();
    }
}
