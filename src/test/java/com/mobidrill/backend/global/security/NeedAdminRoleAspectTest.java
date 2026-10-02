package com.mobidrill.backend.global.security;

import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.domain.user.enums.UserStatus;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.exception.GlobalErrorCode;
import com.mobidrill.backend.global.security.annotation.NeedAdminRole;
import com.mobidrill.backend.global.security.aspect.NeedAdminRoleAspect;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(NeedAdminRoleAspectTest.TestConfig.class)
class NeedAdminRoleAspectTest {

    @Autowired
    private MethodAdminAction methodAction;
    @Autowired
    private ClassAdminAction classAction;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
        methodAction.reset();
    }

    @Test
    @DisplayName("관리자는 메서드와 클래스에 지정한 관리자 작업을 실행할 수 있다")
    void 관리자_작업_실행_성공() {
        // given
        authenticate(UserRole.ROLE_ADMIN);

        // when & then
        assertThat(methodAction.execute()).isEqualTo(1);
        assertThat(classAction.execute()).isEqualTo("관리자 작업");
    }

    @Test
    @DisplayName("일반 사용자는 메서드와 클래스의 관리자 작업을 실행하기 전에 거부한다")
    void 일반_사용자_관리자_작업_실패() {
        // given
        authenticate(UserRole.ROLE_USER);

        // when & then
        assertThatThrownBy(() -> methodAction.execute()).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(GlobalErrorCode.ACCESS_DENIED);
        assertThatThrownBy(() -> classAction.execute()).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(GlobalErrorCode.ACCESS_DENIED);
        assertThat(methodAction.executions()).isZero();
    }

    @Test
    @DisplayName("인증이 없으면 관리자 작업을 실행하기 전에 401 오류로 거부한다")
    void 미인증_관리자_작업_실패() {
        // when & then
        assertThatThrownBy(() -> methodAction.execute()).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(GlobalErrorCode.UNAUTHORIZED);
        assertThat(methodAction.executions()).isZero();
    }

    @Test
    @DisplayName("관리자 애노테이션이 없는 메서드는 일반 사용자도 실행할 수 있다")
    void 일반_메서드_실행_성공() {
        // given
        authenticate(UserRole.ROLE_USER);

        // when & then
        assertThat(methodAction.executions()).isZero();
    }

    private void authenticate(UserRole role) {
        var details = new CustomUserDetails(new UserAuthDto(1L, "user@example.com", "encoded", role, UserStatus.ACTIVE));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        NeedAdminRoleAspect adminRoleAspect() { return new NeedAdminRoleAspect(); }
        @Bean
        MethodAdminAction methodAdminAction() { return new MethodAdminAction(); }
        @Bean
        ClassAdminAction classAdminAction() { return new ClassAdminAction(); }
    }

    public static class MethodAdminAction {
        private int executions;
        @NeedAdminRole
        public int execute() { return ++executions; }
        public int executions() { return executions; }
        public void reset() { executions = 0; }
    }

    @NeedAdminRole
    public static class ClassAdminAction {
        public String execute() { return "관리자 작업"; }
    }
}
