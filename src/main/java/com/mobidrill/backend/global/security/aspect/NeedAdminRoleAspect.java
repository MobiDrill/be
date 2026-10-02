package com.mobidrill.backend.global.security.aspect;

import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import com.mobidrill.backend.global.security.SecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class NeedAdminRoleAspect {

    /**
     * 대상 메서드 실행 전에 인증 사용자의 관리자 권한을 검증한다.
     * - JWT를 다시 파싱하지 않고 필터가 검증한 현재 사용자 권한을 사용한다.
     */
    @Before("@annotation(com.mobidrill.backend.global.security.annotation.NeedAdminRole) || "
            + "@within(com.mobidrill.backend.global.security.annotation.NeedAdminRole)")
    public void validateAdminRole() {
        var user = SecurityUtil.getCurrentUserDetails().getUserAuthDto();
        if (user.role() != UserRole.ROLE_ADMIN) {
            log.warn("[NeedAdminRoleAspect] 관리자 권한 없음 | userId: {}", user.userId());
            throw new CustomException(GlobalErrorCode.ACCESS_DENIED);
        }
    }
}
