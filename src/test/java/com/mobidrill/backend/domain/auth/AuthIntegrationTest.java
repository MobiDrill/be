package com.mobidrill.backend.domain.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobidrill.backend.domain.auth.dto.AuthLoginEmailReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthLogoutReqDto;
import com.mobidrill.backend.domain.auth.dto.AuthRegisterEmailReqDto;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.domain.user.enums.UserStatus;
import com.mobidrill.backend.domain.user.repository.UserRepository;
import com.mobidrill.backend.global.redis.RedisTokenStore;
import com.mobidrill.backend.global.security.CustomUserDetails;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import com.mobidrill.backend.global.util.JwtUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;

    // DB·Service·JWT·HTTP는 실제 구현을 사용하고 외부 Redis 경계만 대체한다.
    @MockitoBean
    private RedisTokenStore redisTokenStore;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("이메일 가입 후 해시와 기본 권한·상태를 저장하고 JWT로 보호 API에 접근한다")
    void 회원가입과_로그인_성공() throws Exception {
        // given
        AuthRegisterEmailReqDto registration = registration("member@example.com");

        // when
        HttpResponse<String> registered = post("/register/email", registration);
        HttpResponse<String> loggedIn = post("/login/email",
                new AuthLoginEmailReqDto(registration.email(), registration.password()));

        // then
        assertThat(registered.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(registered.body()).get("status").asInt()).isEqualTo(201);
        assertThat(objectMapper.readTree(registered.body()).has("data")).isFalse();
        User user = userRepository.findByEmail(registration.email()).orElseThrow();
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_USER);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPassword()).isNotEqualTo(registration.password());
        assertThat(passwordEncoder.matches(registration.password(), user.getPassword())).isTrue();
        assertThat(loggedIn.statusCode()).isEqualTo(200);
        String token = objectMapper.readTree(loggedIn.body()).path("data").path("accessToken").asText();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(user.getId());
        assertThat(protectedRequest(token).statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("동시 동일 이메일 가입은 한 건만 저장하고 다른 요청에는 409를 반환한다")
    void 동시_이메일_회원가입_중복_차단() throws Exception {
        // given
        AuthRegisterEmailReqDto request = registration("concurrent@example.com");
        CountDownLatch start = new CountDownLatch(1);

        // when
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return post("/register/email", request); });
            var second = executor.submit(() -> { start.await(); return post("/register/email", request); });
            start.countDown();
            List<Integer> statuses = List.of(first.get().statusCode(), second.get().statusCode());

            // then
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(userRepository.count()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("비밀번호 확인 오류와 72바이트 초과 입력은 사용자 저장 전에 400으로 거부한다")
    void 잘못된_회원가입_입력_실패() throws Exception {
        // when
        HttpResponse<String> mismatch = post("/register/email",
                new AuthRegisterEmailReqDto("사용자", "mismatch@example.com", "password123", "password456"));
        String tooLong = "가".repeat(25);
        HttpResponse<String> multibyte = post("/register/email",
                new AuthRegisterEmailReqDto("사용자", "bytes@example.com", tooLong, tooLong));

        // then
        assertThat(mismatch.statusCode()).isEqualTo(400);
        assertThat(multibyte.statusCode()).isEqualTo(400);
        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("정확히 72바이트인 비밀번호는 가입과 로그인에 사용할 수 있다")
    void 비밀번호_72바이트_경계_성공() throws Exception {
        // given
        String password = "가".repeat(24);
        AuthRegisterEmailReqDto request = new AuthRegisterEmailReqDto(
                "사용자", "boundary@example.com", password, password);

        // when & then
        assertThat(post("/register/email", request).statusCode()).isEqualTo(201);
        assertThat(post("/login/email", new AuthLoginEmailReqDto(request.email(), password)).statusCode())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("없는 계정과 틀린 비밀번호는 동일한 인증 실패 응답을 반환한다")
    void 잘못된_인증정보_동일_응답() throws Exception {
        // given
        saveUser(UserStatus.ACTIVE);

        // when
        HttpResponse<String> unknown = post("/login/email",
                new AuthLoginEmailReqDto("unknown@example.com", "password123"));
        HttpResponse<String> wrong = post("/login/email",
                new AuthLoginEmailReqDto("existing@example.com", "wrong-password"));

        // then
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.body()).isEqualTo(wrong.body());
    }

    @Test
    @DisplayName("비활성 계정은 로그인·기존 Access Token·Refresh Token으로 인증할 수 없다")
    void 비활성_계정의_로그인과_토큰_접근_실패() throws Exception {
        // given
        User user = saveUser(UserStatus.INACTIVE);
        CustomUserDetails details = new CustomUserDetails(new UserAuthDto(
                user.getId(), user.getEmail(), user.getPassword(), user.getRole(), user.getStatus()));
        String accessToken = jwtUtil.createAccessToken(details).token();
        String refreshToken = jwtUtil.createRefreshToken(details).token();
        when(redisTokenStore.findRefreshTokenUserId(anyString())).thenReturn(user.getId());

        // when & then
        assertThat(post("/login/email", new AuthLoginEmailReqDto(user.getEmail(), "password123")).statusCode())
                .isEqualTo(403);
        assertThat(protectedRequest(accessToken).statusCode()).isEqualTo(403);
        assertThat(post("/refresh", java.util.Map.of("refreshToken", refreshToken)).statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Redis 저장 실패 시 토큰 대신 503 오류 응답을 반환한다")
    void 토큰_저장소_장애_로그인_실패() throws Exception {
        // given
        User user = saveUser(UserStatus.ACTIVE);
        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(redisTokenStore).saveRefreshToken(anyString(), anyLong(), anyLong());

        // when
        HttpResponse<String> response = post("/login/email",
                new AuthLoginEmailReqDto(user.getEmail(), "password123"));

        // then
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(objectMapper.readTree(response.body()).has("data")).isFalse();
    }

    @Test
    @DisplayName("일반 사용자도 Controller에서 주입한 인증 사용자 ID로 로그아웃할 수 있다")
    void 인증_사용자_ID_주입_로그아웃_성공() throws Exception {
        // given
        User user = saveUser(UserStatus.ACTIVE);
        var details = new CustomUserDetails(new UserAuthDto(user.getId(), user.getEmail(), user.getPassword(),
                user.getRole(), user.getStatus()));
        String access = jwtUtil.createAccessToken(details).token();
        String refresh = jwtUtil.createRefreshToken(details).token();

        // when
        var response = post("/logout?userId=" + Long.MAX_VALUE, new AuthLogoutReqDto(access, refresh), access);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.body()).path("message").asText()).isEqualTo("로그아웃이 완료되었습니다.");
    }

    @Test
    @DisplayName("인증 사용자와 다른 사용자의 토큰으로 로그아웃하면 거부한다")
    void 다른_사용자_토큰_로그아웃_실패() throws Exception {
        // given
        User user = saveUser(UserStatus.ACTIVE);
        var current = new CustomUserDetails(new UserAuthDto(user.getId(), user.getEmail(), user.getPassword(),
                user.getRole(), user.getStatus()));
        var other = new CustomUserDetails(new UserAuthDto(Long.MAX_VALUE, "other@example.com", "encoded",
                UserRole.ROLE_USER, UserStatus.ACTIVE));
        String access = jwtUtil.createAccessToken(current).token();
        String otherAccess = jwtUtil.createAccessToken(other).token();
        String otherRefresh = jwtUtil.createRefreshToken(other).token();

        // when
        var response = post("/logout", new AuthLogoutReqDto(otherAccess, otherRefresh), access);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.body()).path("message").asText()).isEqualTo("토큰의 사용자 정보가 일치하지 않습니다.");
    }

    private User saveUser(UserStatus status) {
        return userRepository.saveAndFlush(User.builder().name("사용자").email("existing@example.com")
                .password(passwordEncoder.encode("password123")).role(UserRole.ROLE_USER).status(status).build());
    }

    private AuthRegisterEmailReqDto registration(String email) {
        return new AuthRegisterEmailReqDto("사용자", email, "password123", "password123");
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        return post(path, body, null);
    }

    private HttpResponse<String> post(String path, Object body, String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth" + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> protectedRequest(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/test/protected"))
                .header("Authorization", "Bearer " + token).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
