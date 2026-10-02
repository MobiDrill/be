package com.mobidrill.backend.domain.manual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.enums.ManualStatus;
import com.mobidrill.backend.domain.manual.repository.ManualFileRepository;
import com.mobidrill.backend.domain.manual.repository.ManualRepository;
import com.mobidrill.backend.domain.manual.service.ManualFileInspectionService;
import com.mobidrill.backend.domain.manual.service.ManualFileStorageService;
import com.mobidrill.backend.domain.manual.service.ManualPersistenceService;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import com.mobidrill.backend.domain.training.repository.TrainingFieldRepository;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.domain.user.repository.UserRepository;
import com.mobidrill.backend.global.redis.RedisTokenStore;
import com.mobidrill.backend.global.security.CustomUserDetails;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import com.mobidrill.backend.global.util.JwtUtil;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=128KB",
        "spring.servlet.multipart.max-request-size=160KB",
        "manual.storage.max-file-size=128KB"
})
@ActiveProfiles("test")
class ManualRegisterIntegrationTest {

    private static final Path ROOT = Path.of("build", "manual-registration-" + UUID.randomUUID())
            .toAbsolutePath().normalize();
    private final HttpClient client = HttpClient.newHttpClient();
    @LocalServerPort
    private int port;
    @Autowired
    private ManualRepository manualRepository;
    @Autowired
    private ManualFileRepository fileRepository;
    @Autowired
    private TrainingFieldRepository fieldRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ManualPersistenceService persistence;
    @Autowired
    private ManualFileStorageService storage;
    @Autowired
    private ManualFileInspectionService inspection;
    @Autowired
    private PlatformTransactionManager transactionManager;
    // 외부 Redis 경계만 대체하며 HTTP·JWT·JPA·파일 시스템은 실제 구현을 사용한다.
    @MockitoBean
    private RedisTokenStore redisTokenStore;

    private User user;
    private TrainingField field;
    private String token;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("manual.storage.root", ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().name("등록자").email("manual@example.com")
                .password("encoded").role(UserRole.ROLE_ADMIN).build());
        field = fieldRepository.save(TrainingField.builder().name("전술").build());
        token = jwtUtil.createAccessToken(new CustomUserDetails(new UserAuthDto(user.getId(), user.getEmail(),
                user.getPassword(), user.getRole(), user.getStatus()))).token();
    }

    @AfterEach
    void cleanUp() throws Exception {
        fileRepository.deleteAll();
        manualRepository.deleteAll();
        fieldRepository.deleteAll();
        userRepository.deleteAll();
        clearFiles();
    }

    @AfterAll
    static void removeTestDirectory() throws Exception {
        clearFiles();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "ppt", "pptx", "doc", "docx", "hwp"})
    @DisplayName("JWT multipart 등록으로 두 엔티티와 확장자를 가진 원본 파일을 저장한다")
    void 교범_등록_성공(String extension) throws Exception {
        // given
        byte[] bytes = ManualFileFixtures.file(extension);
        String originalName = "manual." + extension.toUpperCase();

        // when
        HttpResponse<String> response = upload(request("교범"), originalName, bytes, token);

        // then
        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("status").asInt()).isEqualTo(201);
        JsonNode data = body.path("data");
        var manual = manualRepository.findById(data.path("manualId").asLong()).orElseThrow();
        var file = fileRepository.findById(data.path("file").path("manualFileId").asLong()).orElseThrow();
        assertThat(manual.getStatus()).isEqualTo(ManualStatus.TEMPORARY_SAVED);
        assertThat(manual.getTrainingField().getId()).isEqualTo(field.getId());
        assertThat(manual.getRegisteredBy().getId()).isEqualTo(user.getId());
        assertThat(file.getManual().getId()).isEqualTo(manual.getId());
        assertThat(file.getStorageKey()).matches("[0-9a-f]{32}");
        assertThat(file.getOriginalName()).isEqualTo(originalName);
        assertThat(file.getSizeBytes()).isEqualTo(bytes.length);
        String storedName = data.path("file").path("storedFileName").asText();
        assertThat(storedName).isEqualTo(file.getStorageKey() + "." + extension);
        assertThat(Files.readAllBytes(ROOT.resolve(storedName))).isEqualTo(bytes);
        assertThat(fileCount()).isEqualTo(1);
        HttpResponse<String> direct = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/originalManual/" + storedName))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(direct.statusCode()).isEqualTo(403);
        HttpResponse<String> mixedCase = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/ORIGINALMANUAL/" + storedName + "?download=true"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(mixedCase.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("제목 100자와 설명 1000자는 허용하고 초과 입력은 DB와 파일을 남기지 않는다")
    void 입력_길이_경계_검증_성공() throws Exception {
        // given
        byte[] pdf = ManualFileFixtures.file("pdf");

        // when & then
        assertThat(upload(new ManualRegisterReqDto("가".repeat(100), field.getId(), "나".repeat(1000)),
                "manual.pdf", pdf, token).statusCode()).isEqualTo(201);
        assertThat(upload(request("가".repeat(101)), "manual.pdf", pdf, token).statusCode()).isEqualTo(400);
        assertThat(upload(new ManualRegisterReqDto("교범", field.getId(), "나".repeat(1001)),
                "manual.pdf", pdf, token).statusCode()).isEqualTo(400);
        assertThat(upload(request(" "), "manual.pdf", pdf, token).statusCode()).isEqualTo(400);
        assertThat(upload(new ManualRegisterReqDto("교범", null, null), "manual.pdf", pdf, token)
                .statusCode()).isEqualTo(400);
        assertThat(manualRepository.count()).isEqualTo(1);
        assertThat(fileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("파일 누락·빈 파일·확장자 위장·지원하지 않는 형식과 JSON 오류를 명확히 반환한다")
    void 잘못된_파일과_요청_실패() throws Exception {
        // given
        byte[] pdf = ManualFileFixtures.file("pdf");

        // when & then
        assertThat(upload(request("교범"), null, null, token).statusCode()).isEqualTo(400);
        assertThat(upload(request("교범"), "manual.pdf", new byte[0], token).statusCode()).isEqualTo(400);
        assertThat(upload(request("교범"), "manual.ppt", pdf, token).statusCode()).isEqualTo(400);
        assertThat(upload(request("교범"), "manual.pdf", new byte[]{1, 2, 3}, token).statusCode()).isEqualTo(400);
        assertThat(upload(request("교범"), "manual.exe", pdf, token).statusCode()).isEqualTo(415);
        assertThat(uploadRaw("{", "manual.pdf", pdf, token).statusCode()).isEqualTo(400);
        assertThat(uploadRaw(null, "manual.pdf", pdf, token).statusCode()).isEqualTo(400);
        assertThat(uploadRaw("{\"manualTitle\":\"교범\",\"trainingFieldId\":\"\"}",
                "manual.pdf", pdf, token).statusCode()).isEqualTo(400);
        assertThat(manualRepository.count()).isZero();
        assertThat(fileRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("없는 분야와 비활성 분야는 등록을 거부하고 임시 파일을 정리한다")
    void 없는_분야와_비활성_분야_실패() throws Exception {
        // given
        TrainingField inactive = fieldRepository.save(TrainingField.builder().name("비활성").isActive(false).build());
        byte[] pdf = ManualFileFixtures.file("pdf");

        // when & then
        assertThat(upload(new ManualRegisterReqDto("교범", Long.MAX_VALUE, null), "manual.pdf", pdf, token)
                .statusCode()).isEqualTo(404);
        assertThat(upload(new ManualRegisterReqDto("교범", inactive.getId(), null), "manual.pdf", pdf, token)
                .statusCode()).isEqualTo(400);
        assertThat(manualRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("인증 없는 요청은 등록할 수 없다")
    void 인증_없는_등록_실패() throws Exception {
        // when
        var response = upload(request("교범"), "manual.pdf", ManualFileFixtures.file("pdf"), null);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(manualRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("일반 사용자의 교범 등록은 AOP에서 403으로 거부하며 DB와 파일을 남기지 않는다")
    void 일반_사용자_교범_등록_실패() throws Exception {
        // given
        User member = userRepository.save(User.builder().name("일반 사용자").email("member@example.com")
                .password("encoded").role(UserRole.ROLE_USER).build());
        String memberToken = jwtUtil.createAccessToken(new CustomUserDetails(new UserAuthDto(member.getId(),
                member.getEmail(), member.getPassword(), member.getRole(), member.getStatus()))).token();

        // when
        var response = upload(request("교범"), "manual.pdf", ManualFileFixtures.file("pdf"), memberToken);

        // then
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.body()).path("status").asInt()).isEqualTo(403);
        assertThat(manualRepository.count()).isZero();
        assertThat(fileRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("관리자 토큰 발급 후 권한을 회수하면 현재 사용자 권한에 따라 등록을 거부한다")
    void 관리자_권한_회수후_등록_실패() throws Exception {
        // given
        jdbcTemplate.update("UPDATE users SET role = 'ROLE_USER' WHERE id = ?", user.getId());

        // when
        var response = upload(request("교범"), "manual.pdf", ManualFileFixtures.file("pdf"), token);

        // then
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(manualRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("요청의 userId 대신 JWT 인증 사용자 ID로 등록자를 저장한다")
    void 요청_userId_위조_차단_성공() throws Exception {
        // when
        var response = uploadRaw(objectMapper.writeValueAsString(request("교범")), "manual.pdf",
                ManualFileFixtures.file("pdf"), token, Long.MAX_VALUE);

        // then
        assertThat(response.statusCode()).isEqualTo(201);
        var manual = manualRepository.findAll().getFirst();
        assertThat(manual.getRegisteredBy().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("서블릿 파일 및 전체 요청 제한 초과는 413으로 반환한다")
    void 업로드_용량_초과_실패() throws Exception {
        // when & then
        var fileExceeded = upload(request("교범"), "manual.pdf", new byte[129 * 1024], token);
        var requestExceeded = uploadRaw("x".repeat(161 * 1024), "manual.pdf", new byte[]{1}, token);
        assertThat(fileExceeded.statusCode()).isEqualTo(413);
        assertThat(objectMapper.readTree(fileExceeded.body()).path("status").asInt()).isEqualTo(413);
        assertThat(requestExceeded.statusCode()).isEqualTo(413);
        assertThat(manualRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("실제 DB 제약 위반 시 교범·파일 레코드와 최종 원본 파일을 모두 롤백한다")
    void DB_저장_실패시_전체_롤백_성공() throws Exception {
        // given
        jdbcTemplate.execute("ALTER TABLE manual ADD CONSTRAINT test_manual_title CHECK (title <> 'ROLLBACK')");
        try {
            // when
            var response = upload(request("ROLLBACK"), "manual.pdf", ManualFileFixtures.file("pdf"), token);

            // then
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(manualRepository.count()).isZero();
            assertThat(fileRepository.count()).isZero();
            assertThat(fileCount()).isZero();
        } finally {
            jdbcTemplate.execute("ALTER TABLE manual DROP CONSTRAINT test_manual_title");
        }
    }

    @Test
    @DisplayName("트랜잭션 커밋 직전 실패에도 최종 파일을 제거한다")
    void 커밋_실패시_파일_보상_성공() throws Exception {
        // given
        Path staged = storage.stage(new MockMultipartFile("file", "manual.pdf", null, ManualFileFixtures.file("pdf")));
        var inspected = inspection.inspect(staged, "manual.pdf");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            // when & then: 실제 트랜잭션에만 콜백을 등록하며 수동 실행하지 않는다.
            assertThatThrownBy(() -> transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("커밋 실패");
                    }
                });
                return persistence.createManualWithFile(request("교범"), user.getId(), staged, "manual.pdf", inspected);
            })).isInstanceOf(IllegalStateException.class);
        } finally {
            storage.delete(staged);
        }
        assertThat(manualRepository.count()).isZero();
        assertThat(fileRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("같은 이름의 파일을 동시에 등록해도 서로 다른 키로 바이트를 보존한다")
    void 동시_교범_등록_성공() throws Exception {
        // given
        byte[] pdf = ManualFileFixtures.file("pdf");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            // when
            var first = executor.submit(() -> { start.await(); return upload(request("첫 교범"), "manual.pdf", pdf, token); });
            var second = executor.submit(() -> { start.await(); return upload(request("둘째 교범"), "manual.pdf", pdf, token); });
            start.countDown();

            // then
            assertThat(first.get().statusCode()).isEqualTo(201);
            assertThat(second.get().statusCode()).isEqualTo(201);
            assertThat(fileRepository.findAll()).extracting("storageKey").doesNotHaveDuplicates().hasSize(2);
            assertThat(manualRepository.count()).isEqualTo(2);
            assertThat(fileCount()).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Swagger에 multipart JSON과 바이너리 파일 파트를 명시한다")
    void 교범_등록_OpenAPI_문서_성공() throws Exception {
        // when
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        JsonNode operation = objectMapper.readTree(response.body()).path("paths").path("/api/v1/manuals").path("post");
        JsonNode multipart = operation.path("requestBody").path("content").path("multipart/form-data");

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(multipart.path("schema").path("properties").has("request")).isTrue();
        assertThat(multipart.path("schema").path("properties").has("file")).isTrue();
        assertThat(multipart.path("encoding").path("request").path("contentType").asText()).isEqualTo("application/json");
        assertThat(operation.path("responses").has("201")).isTrue();
        assertThat(operation.path("parameters").toString()).doesNotContain("userId");
    }

    private ManualRegisterReqDto request(String title) {
        return new ManualRegisterReqDto(title, field.getId(), "설명");
    }

    private HttpResponse<String> upload(ManualRegisterReqDto request, String fileName, byte[] bytes, String accessToken)
            throws Exception {
        return uploadRaw(objectMapper.writeValueAsString(request), fileName, bytes, accessToken);
    }

    private HttpResponse<String> uploadRaw(String json, String fileName, byte[] bytes, String accessToken) throws Exception {
        return uploadRaw(json, fileName, bytes, accessToken, null);
    }

    private HttpResponse<String> uploadRaw(String json, String fileName, byte[] bytes, String accessToken,
                                           Long suppliedUserId) throws Exception {
        String boundary = "manual-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (json != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"request\"\r\n"
                    + "Content-Type: application/json\r\n\r\n" + json + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (fileName != null) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + fileName + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);
            body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        String query = suppliedUserId == null ? "" : "?userId=" + suppliedUserId;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/manuals" + query))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (accessToken != null) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long fileCount() throws Exception {
        if (!Files.exists(ROOT)) {
            return 0;
        }
        try (var files = Files.walk(ROOT)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private static void clearFiles() throws Exception {
        if (Files.exists(ROOT)) {
            try (var paths = Files.walk(ROOT)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(ROOT)) {
                        throw new IllegalStateException("테스트 저장 경로 이탈");
                    }
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
