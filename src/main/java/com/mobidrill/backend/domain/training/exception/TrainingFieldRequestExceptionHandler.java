package com.mobidrill.backend.domain.training.exception;

import com.mobidrill.backend.domain.training.controller.TrainingFieldController;
import com.mobidrill.backend.global.response.GlobalResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = TrainingFieldController.class)
public class TrainingFieldRequestExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<GlobalResponse<Void>> handleInvalidJson(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(GlobalResponse.error(400, "훈련 분야 요청 JSON이 올바르지 않습니다."));
    }
}
