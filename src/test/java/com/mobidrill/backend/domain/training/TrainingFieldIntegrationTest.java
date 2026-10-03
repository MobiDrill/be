package com.mobidrill.backend.domain.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobidrill.backend.domain.manual.ManualFileFixtures;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterResDto;
import com.mobidrill.backend.domain.manual.entity.ManualExtractionRun;
import com.mobidrill.backend.domain.manual.exception.ManualErrorCode;
import com.mobidrill.backend.domain.manual.mapper.ManualMapper;
import com.mobidrill.backend.domain.manual.repository.ManualFileRepository;
import com.mobidrill.backend.domain.manual.repository.ManualRepository;
import com.mobidrill.backend.domain.manual.service.ManualService;
import com.mobidrill.backend.domain.training.dto.TrainingFieldUpdateReqDto;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import com.mobidrill.backend.domain.training.repository.TrainingFieldRepository;
import com.mobidrill.backend.domain.training.service.TrainingFieldService;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.enums.UserRole;
import com.mobidrill.backend.domain.user.repository.UserRepository;
import com.mobidrill.backend.global.exception.CustomException;
import com.mobidrill.backend.global.redis.RedisTokenStore;
import com.mobidrill.backend.global.security.CustomUserDetails;
import com.mobidrill.backend.global.security.module.UserAuthDto;
import com.mobidrill.backend.global.util.JwtUtil;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:training-test;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=USER")
@ActiveProfiles("test")
class TrainingFieldIntegrationTest {

    private static final Path ROOT = Path.of("build", "training-files-" + UUID.randomUUID()).toAbsolutePath().normalize();
    private final HttpClient client = HttpClient.newHttpClient();
    @LocalServerPort
    private int port;
    @Autowired
    private TrainingFieldRepository fieldRepository;
    @Autowired
    private TrainingFieldService fieldService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ManualRepository manualRepository;
    @Autowired
    private ManualFileRepository fileRepository;
    @Autowired
    private ManualService manualService;
    @Autowired
    private ManualMapper manualMapper;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private ObjectMapper objectMapper;
    // 외부 Redis 경계만 대체한다. HTTP·JPA·트랜잭션·파일 저장은 실제 구현을 사용한다.
    @MockitoBean
    private RedisTokenStore redisTokenStore;

    private User admin;
    private TrainingField field;
    private String token;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("manual.storage.root", ROOT::toString);
    }

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder().name("관리자").email("training-admin@example.com")
                .password("encoded").role(UserRole.ROLE_ADMIN).build());
        field = fieldRepository.save(TrainingField.builder().name("기본 분야").build());
        token = accessToken(admin);
    }

    @AfterEach
    void cleanUp() throws Exception {
        jdbcTemplate.update("DELETE FROM manual_extraction_run");
        fileRepository.deleteAll();
        manualRepository.deleteAll();
        fieldRepository.deleteAll();
        userRepository.deleteAll();
        if (Files.exists(ROOT)) {
            try (var files = Files.walk(ROOT)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(ROOT)) {
                        throw new IllegalStateException("테스트 저장 경로 이탈");
                    }
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    @DisplayName("분야 생성 시 이름 공백을 제거하고 기본 활성 상태를 저장하며 수정·삭제할 수 있다")
    void 훈련_분야_CRUD_성공() throws Exception {
        // when
        var created = call("POST", "", "{\"name\":\"  전술  \"}", token);
        JsonNode data = data(created);
        long id = data.path("trainingFieldId").asLong();

        // then
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(data.path("name").asText()).isEqualTo("전술");
        assertThat(data.path("isActive").asBoolean()).isTrue();
        var updated = call("PUT", "/" + id, "{\"name\":\"  사격  \",\"isActive\":false}", token);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(data(updated).path("name").asText()).isEqualTo("사격");
        assertThat(fieldRepository.findById(id).orElseThrow().getIsActive()).isFalse();
        var deleted = call("DELETE", "/" + id, null, token);
        assertThat(deleted.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(deleted.body()).has("data")).isFalse();
        assertThat(fieldRepository.existsById(id)).isFalse();
    }

    @Test
    @DisplayName("활성·비활성 분야를 ID 내림차순 페이지로 조회하며 범위 밖 페이지는 빈 목록이다")
    void 페이지_조회_성공() throws Exception {
        // given
        for (int index = 0; index < 4; index++) {
            fieldRepository.save(TrainingField.builder().name("분야 " + index).isActive(index % 2 == 0).build());
        }
        var ids = fieldRepository.findAll().stream().map(TrainingField::getId).sorted(Comparator.reverseOrder()).toList();

        // when & then
        JsonNode first = data(call("GET", "?page=1&size=2", null, token));
        assertThat(first.path("content").get(0).path("trainingFieldId").asLong()).isEqualTo(ids.get(0));
        assertThat(first.path("content").get(1).path("trainingFieldId").asLong()).isEqualTo(ids.get(1));
        assertThat(first.path("totalElements").asLong()).isEqualTo(5);
        assertThat(first.path("totalPages").asInt()).isEqualTo(3);
        assertThat(first.path("page").asInt()).isEqualTo(1);
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        assertThat(first.path("hasPrevious").asBoolean()).isFalse();
        assertThat(first.path("nextCursor").isNull()).isTrue();
        JsonNode last = data(call("GET", "?page=3&size=2", null, token));
        assertThat(last.path("content").size()).isEqualTo(1);
        assertThat(last.path("hasNext").asBoolean()).isFalse();
        assertThat(last.path("hasPrevious").asBoolean()).isTrue();
        assertThat(data(call("GET", "?page=4&size=2", null, token)).path("content").size()).isZero();
        assertThat(data(call("GET", "", null, token)).path("size").asInt()).isEqualTo(20);
        assertThat(call("GET", "?page=1&size=100", null, token).statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("이름 100자 경계와 비활성 생성을 허용하고 잘못된 요청은 400을 반환한다")
    void 입력_검증_성공() throws Exception {
        // when & then
        assertThat(call("POST", "", "{\"name\":\"" + "가".repeat(100) + "\",\"isActive\":false}", token)
                .statusCode()).isEqualTo(201);
        assertThat(fieldRepository.findAll().stream().filter(f -> !f.getIsActive()).count()).isEqualTo(1);
        for (String json : new String[]{"{", "{}", "{\"name\":\" \"}", "{\"name\":\"" + "가".repeat(101) + "\"}"}) {
            assertThat(call("POST", "", json, token).statusCode()).isEqualTo(400);
        }
        assertThat(call("PUT", "/" + field.getId(), "{\"name\":\"전술\"}", token).statusCode()).isEqualTo(400);
        assertThat(call("PUT", "/0", "{\"name\":\"전술\",\"isActive\":true}", token).statusCode()).isEqualTo(400);
        assertThat(call("DELETE", "/-1", null, token).statusCode()).isEqualTo(400);
        for (String query : new String[]{"?page=0", "?size=0", "?size=101", "?page=abc", "?size=2147483648"}) {
            assertThat(call("GET", query, null, token).statusCode()).isEqualTo(400);
        }
        assertThat(fieldRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("존재하지 않는 분야 수정과 삭제는 404를 반환한다")
    void 없는_분야_수정과_삭제_실패() throws Exception {
        // when & then
        assertThat(call("PUT", "/" + Long.MAX_VALUE, "{\"name\":\"전술\",\"isActive\":true}", token)
                .statusCode()).isEqualTo(404);
        assertThat(call("DELETE", "/" + Long.MAX_VALUE, null, token).statusCode()).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "DELETE"})
    @DisplayName("모든 훈련 분야 관리 API는 인증과 관리자 권한을 요구한다")
    void 관리자_권한_검증_성공(String method) throws Exception {
        // given
        User member = userRepository.save(User.builder().name("일반 사용자").email("training-member@example.com")
                .password("encoded").role(UserRole.ROLE_USER).build());
        String path = method.equals("PUT") || method.equals("DELETE") ? "/" + field.getId() : "";
        String body = method.equals("POST") || method.equals("PUT") ? "{\"name\":\"전술\",\"isActive\":true}" : null;

        // when & then
        assertThat(call(method, path, body, null).statusCode()).isEqualTo(401);
        assertThat(call(method, path, body, accessToken(member)).statusCode()).isEqualTo(403);
        assertThat(fieldRepository.count()).isEqualTo(1);
        assertThat(fieldRepository.findById(field.getId()).orElseThrow().getName()).isEqualTo("기본 분야");
    }

    @Test
    @DisplayName("분야 삭제는 연관 교범만 연결 해제하고 교범·파일·추출 기록·원본을 보존한다")
    void 분야_삭제시_교범과_원본_보존_성공() throws Exception {
        // given
        var first = registerManual(field.getId());
        var second = registerManual(field.getId());
        var other = fieldRepository.save(TrainingField.builder().name("다른 분야").build());
        var untouched = registerManual(other.getId());
        long extractionId = transaction().execute(status -> {
            var extraction = ManualExtractionRun.builder().manual(manualRepository.findById(first.manualId()).orElseThrow())
                    .extractedBy(userRepository.findById(admin.getId()).orElseThrow()).startedAt(LocalDateTime.now()).build();
            entityManager.persist(extraction);
            entityManager.flush();
            return extraction.getId();
        });

        // when
        var response = call("DELETE", "/" + field.getId(), null, token);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(fieldRepository.existsById(field.getId())).isFalse();
        assertThat(manualRepository.count()).isEqualTo(3);
        assertThat(fileRepository.count()).isEqualTo(3);
        assertThat(manualRepository.findById(first.manualId()).orElseThrow().getTrainingField()).isNull();
        assertThat(manualRepository.findById(second.manualId()).orElseThrow().getTrainingField()).isNull();
        assertThat(manualRepository.findById(untouched.manualId()).orElseThrow().getTrainingField().getId()).isEqualTo(other.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM manual_extraction_run WHERE manual_extraction_run_id = ?",
                Long.class, extractionId)).isEqualTo(1);
        for (var result : new ManualRegisterResDto[]{first, second, untouched}) {
            assertThat(Files.readAllBytes(ROOT.resolve(result.file().storedFileName()))).isEqualTo(ManualFileFixtures.file("pdf"));
        }
        transaction().executeWithoutResult(status -> {
            var manual = manualRepository.findById(first.manualId()).orElseThrow();
            var file = fileRepository.findById(first.file().manualFileId()).orElseThrow();
            assertThat(manualMapper.toRegisterResDto(manual, file).trainingFieldId()).isNull();
        });
    }

    @Test
    @DisplayName("분야 비활성 변경은 기존 교범 연결을 유지하고 신규 등록을 거부한다")
    void 분야_비활성_수정_성공() throws Exception {
        // given
        var manual = registerManual(field.getId());

        // when
        var response = call("PUT", "/" + field.getId(), "{\"name\":\"비활성 분야\",\"isActive\":false}", token);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(manualRepository.findById(manual.manualId()).orElseThrow().getTrainingField().getId()).isEqualTo(field.getId());
        assertThatThrownBy(() -> registerManual(field.getId())).isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ManualErrorCode.TRAINING_FIELD_INACTIVE);
        assertThat(fileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("교범 연결 해제 후 분야 삭제가 실패하면 분야와 원래 교범 연결을 복원한다")
    void 분야_삭제_실패시_연결_롤백_성공() throws Exception {
        // given
        var manual = registerManual(field.getId());
        jdbcTemplate.execute("CREATE TABLE training_delete_guard (field_id BIGINT REFERENCES training_field(training_field_id))");
        jdbcTemplate.update("INSERT INTO training_delete_guard(field_id) VALUES (?)", field.getId());
        try {
            // when
            var response = call("DELETE", "/" + field.getId(), null, token);

            // then
            assertThat(response.statusCode()).isEqualTo(500);
            assertThat(fieldRepository.existsById(field.getId())).isTrue();
            assertThat(manualRepository.findById(manual.manualId()).orElseThrow().getTrainingField().getId()).isEqualTo(field.getId());
            assertThat(fileCount()).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP TABLE training_delete_guard");
        }
    }

    @Test
    @DisplayName("수정은 같은 분야 행의 잠금이 해제될 때까지 대기한다")
    void 분야_수정_잠금_대기_성공() throws Exception {
        // when
        runAfterLockedTransaction(() -> {
            fieldRepository.findByIdForUpdate(field.getId()).orElseThrow();
            return null;
        }, () -> fieldService.updateTrainingField(field.getId(), new TrainingFieldUpdateReqDto("변경 분야", true)));

        // then
        assertThat(fieldRepository.findById(field.getId()).orElseThrow().getName()).isEqualTo("변경 분야");
    }

    @Test
    @DisplayName("교범 등록이 먼저 잠금을 획득하면 뒤따르는 삭제는 새 교범까지 연결 해제한다")
    void 교범_등록후_동시_분야_삭제_성공() throws Exception {
        // when
        runAfterLockedTransaction(() -> registerManual(field.getId()), () -> {
            fieldService.deleteTrainingField(field.getId());
            return null;
        });

        // then
        assertThat(fieldRepository.existsById(field.getId())).isFalse();
        assertThat(manualRepository.findAll()).hasSize(1).allSatisfy(manual -> assertThat(manual.getTrainingField()).isNull());
        assertThat(fileRepository.count()).isEqualTo(1);
        assertThat(fileCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("분야 삭제가 먼저 잠금을 획득하면 뒤따르는 교범 등록은 실패하고 파일을 정리한다")
    void 분야_삭제후_동시_교범_등록_실패() throws Exception {
        // when
        runAfterLockedTransaction(() -> {
            fieldService.deleteTrainingField(field.getId());
            return null;
        }, () -> {
            assertThatThrownBy(() -> registerManual(field.getId())).isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ManualErrorCode.TRAINING_FIELD_NOT_FOUND);
            return null;
        });

        // then
        assertThat(manualRepository.count()).isZero();
        assertThat(fileRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    @Test
    @DisplayName("비활성 수정이 먼저 잠금을 획득하면 뒤따르는 등록은 수정된 상태를 보고 거부한다")
    void 분야_비활성_수정후_동시_등록_실패() throws Exception {
        // when
        runAfterLockedTransaction(() -> fieldService.updateTrainingField(field.getId(),
                new TrainingFieldUpdateReqDto("비활성 분야", false)), () -> {
            assertThatThrownBy(() -> registerManual(field.getId())).isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ManualErrorCode.TRAINING_FIELD_INACTIVE);
            return null;
        });

        // then
        assertThat(fieldRepository.findById(field.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(manualRepository.count()).isZero();
        assertThat(fileCount()).isZero();
    }

    private void runAfterLockedTransaction(Callable<?> firstAction, Callable<?> waitingAction) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            try {
                var first = executor.submit(() -> transaction().execute(status -> {
                    try {
                        Object result = firstAction.call();
                        locked.countDown();
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("잠금 해제 신호 대기 시간 초과");
                        }
                        return result;
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }));
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var waiting = executor.submit(() -> {
                    attempted.countDown();
                    return waitingAction.call();
                });
                assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> waiting.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                waiting.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private ManualRegisterResDto registerManual(Long fieldId) throws Exception {
        return manualService.registerManual(admin.getId(), new ManualRegisterReqDto("교범", fieldId, "설명"),
                new MockMultipartFile("file", "manual.pdf", "application/pdf", ManualFileFixtures.file("pdf")));
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private String accessToken(User user) {
        return jwtUtil.createAccessToken(new CustomUserDetails(new UserAuthDto(user.getId(), user.getEmail(),
                user.getPassword(), user.getRole(), user.getStatus()))).token();
    }

    private HttpResponse<String> call(String method, String path, String json, String accessToken) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/training-fields" + path));
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (json != null) {
            request.header("Content-Type", "application/json");
        }
        request.method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isIn(200, 201);
        return objectMapper.readTree(response.body()).path("data");
    }

    private long fileCount() throws Exception {
        if (!Files.exists(ROOT)) {
            return 0;
        }
        try (var files = Files.walk(ROOT)) {
            return files.filter(Files::isRegularFile).count();
        }
    }
}
