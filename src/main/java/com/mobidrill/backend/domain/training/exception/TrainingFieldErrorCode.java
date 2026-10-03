package com.mobidrill.backend.domain.training.exception;

import com.mobidrill.backend.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TrainingFieldErrorCode implements BaseErrorCode {
    TRAINING_FIELD_NOT_FOUND(HttpStatus.NOT_FOUND, "훈련 분야를 찾을 수 없습니다."),
    INVALID_TRAINING_FIELD_ID(HttpStatus.BAD_REQUEST, "훈련 분야 ID는 양수여야 합니다."),
    INVALID_PAGINATION(HttpStatus.BAD_REQUEST, "페이지는 1 이상, 페이지 크기는 1~100이어야 합니다.");

    private final HttpStatus status;
    private final String message;
}
