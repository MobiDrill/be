package com.mobidrill.backend.domain.user.mapper;

import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    /**
     * 사용자 엔티티를 상태를 포함한 인증 정보로 변환한다.
     * @param user : 인증할 사용자
     * @return : 사용자 인증 정보
     */
    public UserAuthDto toUserAuthDto(User user) {
        return new UserAuthDto(
                user.getId(), user.getEmail(), user.getPassword(), user.getRole(), user.getStatus()
        );
    }
}
