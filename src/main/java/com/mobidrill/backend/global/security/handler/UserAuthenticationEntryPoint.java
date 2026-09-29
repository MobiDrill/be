package com.mobidrill.backend.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import com.mobidrill.backend.global.exception.model.BaseErrorCode;
import com.mobidrill.backend.global.response.GlobalResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class UserAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        Object exception = request.getAttribute("exception");
        BaseErrorCode errorCode = exception instanceof CustomException customException
                ? customException.getErrorCode()
                : GlobalErrorCode.UNAUTHORIZED;

        response.setStatus(errorCode.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                GlobalResponse.error(errorCode.getStatus().value(), errorCode.getMessage())
        );
    }
}
