package com.mobidrill.backend.domain.auth.service;

import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailResDto;
import com.mobidrill.backend.domain.auth.dto.AuthLogoutReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRefreshTokenResDto;
import com.mobidrill.backend.domain.auth.dto.AuthRegisterEmailReqDto;
import com.mobidrill.backend.domain.auth.exception.AuthErrorCode;
import com.mobidrill.backend.domain.auth.mapper.AuthMapper;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.enums.UserStatus;
import com.mobidrill.backend.domain.user.mapper.UserMapper;
import com.mobidrill.backend.domain.user.exception.UserErrorCode;
import com.mobidrill.backend.domain.user.repository.UserRepository;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.security.CustomUserDetails;
import com.mobidrill.backend.global.security.SecurityUtil;
import com.mobidrill.backend.global.security.module.JwtTokenType;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import com.mobidrill.backend.global.util.JwtUtil;
import com.mobidrill.backend.global.util.RedisUtil;
import com.mobidrill.backend.global.util.module.TokenInfo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final AuthMapper authMapper;
    private final UserMapper userMapper;
    private String dummyPasswordHash;

    /**
     * 존재하지 않는 계정의 인증 시간 차이를 줄이기 위한 비밀번호 해시를 초기화한다.
     */
    @PostConstruct
    private void initializeDummyPasswordHash() {
        log.debug("[AuthService] 인증용 더미 해시 초기화 | initializeDummyPasswordHash() - START");
        dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        log.debug("[AuthService] 인증용 더미 해시 초기화 | initializeDummyPasswordHash() - END");
    }

    /**
     * 이메일과 비밀번호로 신규 사용자를 등록한다.
     * - 이메일 중복과 비밀번호 확인 값을 검증하고 비밀번호를 BCrypt로 암호화한다.
     * @param request : 이메일 회원가입 요청
     */
    @Transactional
    public void registerEmail(AuthRegisterEmailReqDto request) {
        log.info("[AuthService] 이메일 회원가입 | registerEmail() - START | email: {}", request.email());

        /*
            1. 회원가입 입력값 검증
            - 비밀번호 확인과 이메일 중복을 검증한다.
         */
        if (!request.password().equals(request.passwordConfirm())) {
            throw new CustomException(AuthErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new CustomException(UserErrorCode.USER_ALREADY_EXISTS);
        }

        /*
            2. 사용자 저장
            - 비밀번호를 암호화하고 기본 사용자 권한으로 저장한다.
         */
        User user = authMapper.toUser(request, passwordEncoder.encode(request.password()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueViolation(exception)) {
                throw new CustomException(UserErrorCode.USER_ALREADY_EXISTS);
            }
            throw exception;
        }

        log.info("[AuthService] 이메일 회원가입 | registerEmail() - END | userId: {}", user.getId());
    }

    /**
     * 이메일과 비밀번호를 검증하고 JWT Access/Refresh Token을 발급한다.
     * @param request : 이메일 로그인 요청
     * @return : 사용자와 발급 토큰 정보
     */
    @Transactional(readOnly = true)
    public AuthLoginEmailResDto loginEmail(AuthLoginEmailReqDto request) {
        log.info("[AuthService] 이메일 로그인 | loginEmail() - START | email: {}", request.email());

        /*
            1. 사용자 인증
            - 계정 존재 여부와 비밀번호를 동일한 오류로 처리해 계정 추측을 방지한다.
         */
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new CustomException(AuthErrorCode.INVALID_LOGIN_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new CustomException(AuthErrorCode.INVALID_LOGIN_CREDENTIALS);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(AuthErrorCode.USER_INACTIVE);
        }

        /*
            2. JWT 발급 및 Refresh Token 저장
            - Access/Refresh Token을 생성하고 Refresh Token 해시를 Redis에 저장한다.
         */
        CustomUserDetails userDetails = toUserDetails(user);
        TokenInfo accessToken = jwtUtil.createAccessToken(userDetails);
        TokenInfo refreshToken = jwtUtil.createRefreshToken(userDetails);
        try {
            redisUtil.saveRefreshToken(refreshToken.token(), user.getId());
        } catch (DataAccessException exception) {
            throw new CustomException(AuthErrorCode.TOKEN_STORE_UNAVAILABLE);
        }

        AuthLoginEmailResDto result = authMapper.toLoginResDto(user, accessToken, refreshToken);
        log.info("[AuthService] 이메일 로그인 | loginEmail() - END | userId: {}", user.getId());
        return result;
    }

    /**
     * 유효한 Refresh Token을 회전시키고 새 Access/Refresh Token을 발급한다.
     * @param request : 기존 Refresh Token
     * @return : 새 Access/Refresh Token
     */
    @Transactional(readOnly = true)
    public AuthRefreshTokenResDto refreshToken(AuthRefreshTokenReqDto request) {
        log.info("[AuthService] JWT 재발급 | refreshToken() - START");

        /*
            1. Refresh Token 검증
            - JWT 타입과 Redis 저장 사용자 정보를 검증한다.
         */
        String oldRefreshToken = request.refreshToken();
        if (jwtUtil.getTokenType(oldRefreshToken) != JwtTokenType.REFRESH) {
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        Long userId = jwtUtil.getUserIdFromToken(oldRefreshToken);
        Long storedUserId = redisUtil.findRefreshTokenUserId(oldRefreshToken);
        if (storedUserId == null) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (!userId.equals(storedUserId)) {
            throw new CustomException(AuthErrorCode.TOKEN_USER_MISMATCH);
        }

        /*
            2. 새 토큰 발급 및 원자적 회전
            - 기존 Refresh Token을 한 번만 사용할 수 있도록 Redis에서 원자적으로 교체한다.
         */
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        if (user.getStatus() != UserStatus.ACTIVE) {
            redisUtil.deleteRefreshToken(oldRefreshToken);
            throw new CustomException(AuthErrorCode.USER_INACTIVE);
        }
        CustomUserDetails userDetails = toUserDetails(user);
        TokenInfo newAccessToken = jwtUtil.createAccessToken(userDetails);
        TokenInfo newRefreshToken = jwtUtil.createRefreshToken(userDetails);
        boolean rotated = redisUtil.rotateRefreshToken(
                oldRefreshToken,
                newRefreshToken.token(),
                userId
        );
        if (!rotated) {
            throw new CustomException(AuthErrorCode.REFRESH_TOKEN_REUSED);
        }

        AuthRefreshTokenResDto result = authMapper.toRefreshResDto(newAccessToken, newRefreshToken);
        log.info("[AuthService] JWT 재발급 | refreshToken() - END | userId: {}", userId);
        return result;
    }

    /**
     * 현재 사용자의 Refresh Token을 제거하고 Access Token을 블랙리스트에 등록한다.
     * @param request : 폐기할 Access/Refresh Token
     */
    public void logout(AuthLogoutReqDto request) {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        log.info("[AuthService] 로그아웃 | logout() - START | userId: {}", currentUserId);

        /*
            1. 토큰 사용자 검증
            - 요청 토큰과 현재 인증 사용자가 동일한지 확인한다.
         */
        Long accessTokenUserId = jwtUtil.getUserIdFromToken(request.accessToken());
        Long refreshTokenUserId = jwtUtil.getUserIdFromToken(request.refreshToken());
        if (!currentUserId.equals(accessTokenUserId) || !currentUserId.equals(refreshTokenUserId)) {
            throw new CustomException(AuthErrorCode.TOKEN_USER_MISMATCH);
        }
        if (jwtUtil.getTokenType(request.accessToken()) != JwtTokenType.ACCESS
                || jwtUtil.getTokenType(request.refreshToken()) != JwtTokenType.REFRESH) {
            throw new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        /*
            2. 토큰 폐기
            - Refresh Token을 제거하고 Access Token을 남은 만료 시간 동안 차단한다.
         */
        redisUtil.deleteRefreshToken(request.refreshToken());
        redisUtil.blacklistAccessToken(request.accessToken());

        log.info("[AuthService] 로그아웃 | logout() - END | userId: {}", currentUserId);
    }

    /**
     * 사용자 엔티티를 Spring Security 사용자 정보로 변환한다.
     * @param user : 변환할 사용자 엔티티
     * @return : Spring Security 사용자 상세 정보
     */
    private CustomUserDetails toUserDetails(User user) {
        log.debug("[AuthService] 사용자 인증 정보 변환 | toUserDetails() - START | userId: {}", user.getId());
        UserAuthDto userAuthDto = userMapper.toUserAuthDto(user);
        CustomUserDetails result = new CustomUserDetails(userAuthDto);
        log.debug("[AuthService] 사용자 인증 정보 변환 | toUserDetails() - END | userId: {}", user.getId());
        return result;
    }

    /**
     * DB 무결성 오류가 이메일 고유 제약의 충돌인지 확인한다.
     * @param exception : 저장 중 발생한 무결성 오류
     * @return : 이메일 고유 제약 충돌 여부
     */
    private boolean isEmailUniqueViolation(DataIntegrityViolationException exception) {
        log.debug("[AuthService] 이메일 고유 제약 확인 | isEmailUniqueViolation() - START");
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains("uk_users_email")) {
                log.debug("[AuthService] 이메일 고유 제약 확인 | isEmailUniqueViolation() - END | result: true");
                return true;
            }
            cause = cause.getCause();
        }
        log.debug("[AuthService] 이메일 고유 제약 확인 | isEmailUniqueViolation() - END | result: false");
        return false;
    }
}
