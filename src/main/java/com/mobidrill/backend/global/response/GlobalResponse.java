package com.mobidrill.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"status", "message", "data"})
public record GlobalResponse<T>(
        Integer status,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) T data
) {

    public static <T> GlobalResponse<T> success(T data) {
        return new GlobalResponse<>(200, "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> GlobalResponse<T> success(String message, T data) {
        return new GlobalResponse<>(200, message, data);
    }

    public static <T> GlobalResponse<T> success(Integer status, String message, T data) {
        return new GlobalResponse<>(status, message, data);
    }

    public static GlobalResponse<Void> error(Integer status, String message) {
        return new GlobalResponse<>(status, message, null);
    }
}
