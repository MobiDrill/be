package com.mobidrill.backend.global.exception.model;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    HttpStatus getStatus();

    String getMessage();
}
