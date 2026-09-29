package com.mobidrill.backend.global.security;

import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.exception.UserErrorCode;
import com.mobidrill.backend.domain.user.repository.UserRepository;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * 사용자 ID로 Spring Security 인증 정보를 조회한다.
     * @param username : 문자열 형식의 사용자 ID
     * @return : Spring Security 사용자 상세 정보
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.info("[CustomUserDetailsService] 인증 사용자 조회 | loadUserByUsername() - START | userId: {}", username);

        Long userId;
        try {
            userId = Long.parseLong(username);
        } catch (NumberFormatException exception) {
            throw new UsernameNotFoundException("잘못된 사용자 ID입니다.", exception);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
        UserAuthDto userAuthDto = new UserAuthDto(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole()
        );

        log.info("[CustomUserDetailsService] 인증 사용자 조회 | loadUserByUsername() - END | userId: {}", userId);
        return new CustomUserDetails(userAuthDto);
    }
}
