package com.mobidrill.backend.domain.manual.exception;

import com.mobidrill.backend.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ManualErrorCode implements BaseErrorCode {
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "교범 파일을 첨부해주세요."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "빈 파일은 등록할 수 없습니다."),
    INVALID_FILE_NAME(HttpStatus.BAD_REQUEST, "파일명이 없거나 올바르지 않습니다."),
    FILE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "파일 확장자와 실제 파일 형식이 일치하지 않습니다."),
    INVALID_MANUAL_FILE(HttpStatus.BAD_REQUEST, "교범 파일 형식을 판별할 수 없습니다."),
    TRAINING_FIELD_INACTIVE(HttpStatus.BAD_REQUEST, "비활성 훈련 분야에는 교범을 등록할 수 없습니다."),
    TRAINING_FIELD_NOT_FOUND(HttpStatus.NOT_FOUND, "훈련 분야를 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "교범 파일 또는 요청이 허용 용량을 초과했습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF, PPT, PPTX, DOC, DOCX, HWP 파일만 지원합니다."),
    FILE_STORAGE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "교범 파일을 저장하지 못했습니다.");

    private final HttpStatus status;
    private final String message;
}
