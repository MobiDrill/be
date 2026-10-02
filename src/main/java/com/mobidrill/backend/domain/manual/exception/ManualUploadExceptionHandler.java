package com.mobidrill.backend.domain.manual.exception;

import com.mobidrill.backend.global.response.GlobalResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ManualUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        ManualErrorCode error = ManualErrorCode.FILE_TOO_LARGE;
        return ResponseEntity.status(error.getStatus())
                .body(GlobalResponse.error(error.getStatus().value(), error.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<GlobalResponse<Void>> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest()
                .body(GlobalResponse.error(400, exception.getRequestPartName() + " 파트가 필요합니다."));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<GlobalResponse<Void>> handleInvalidRequest(MultipartException exception) {
        return ResponseEntity.badRequest().body(GlobalResponse.error(400, "요청 본문이 올바르지 않습니다."));
    }
}
