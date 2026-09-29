package com.mobidrill.backend.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import com.mobidrill.backend.global.response.GlobalResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        response.setStatus(GlobalErrorCode.ACCESS_DENIED.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                GlobalResponse.error(
                        GlobalErrorCode.ACCESS_DENIED.getStatus().value(),
                        GlobalErrorCode.ACCESS_DENIED.getMessage()
                )
        );
    }
}
