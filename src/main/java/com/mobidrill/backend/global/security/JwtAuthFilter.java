package com.mobidrill.backend.global.security;

import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import com.mobidrill.backend.global.security.handler.UserAuthenticationEntryPoint;
import com.mobidrill.backend.global.security.module.JwtTokenType;
import com.mobidrill.backend.global.util.JwtUtil;
import com.mobidrill.backend.global.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final CustomUserDetailsService customUserDetailsService;
    private final UserAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String accessToken = resolveAccessToken(request);
        if (accessToken == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (jwtUtil.getTokenType(accessToken) != JwtTokenType.ACCESS) {
                throw new CustomException(GlobalErrorCode.ACCESS_WITH_NON_ACCESS_TYPE_TOKEN);
            }
            if (redisUtil.isAccessTokenBlacklisted(accessToken)) {
                throw new CustomException(GlobalErrorCode.BLACKLISTED_TOKEN);
            }

            Authentication authentication = createAuthentication(accessToken);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception exception) {
            SecurityContextHolder.clearContext();
            log.warn("[JwtAuthFilter] JWT 인증 실패 | uri: {}, reason: {}", request.getRequestURI(), exception.getMessage());
            request.setAttribute("exception", exception);
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new AuthenticationException(exception.getMessage(), exception) {
                    }
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private Authentication createAuthentication(String accessToken) {
        Long userId = jwtUtil.getUserIdFromToken(accessToken);
        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService
                .loadUserByUsername(userId.toString());
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }
}
