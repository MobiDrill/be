package com.mobidrill.backend.domain;

import com.mobidrill.backend.domain.knowledge.entity.KnowledgeSection;
import com.mobidrill.backend.domain.knowledge.entity.KnowledgeSource;
import com.mobidrill.backend.domain.manual.entity.Manual;
import com.mobidrill.backend.domain.manual.entity.ManualExtractionRun;
import com.mobidrill.backend.domain.manual.entity.ManualFile;
import com.mobidrill.backend.domain.manual.enums.ManualExtractionStatus;
import com.mobidrill.backend.domain.manual.enums.ManualFileType;
import com.mobidrill.backend.domain.question.entity.*;
import com.mobidrill.backend.domain.question.enums.QuestionGenerationStatus;
import com.mobidrill.backend.domain.question.enums.QuestionTargetType;
import com.mobidrill.backend.domain.training.entity.TrainingField;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.domain.user.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EntityMappingTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("동일 추출 실행에서 여러 문제 생성 실행을 저장하고 진행 중 종료일을 비워둘 수 있다")
    void 동일_추출의_복수_생성_실행_저장_성공() {
        // given
        User user = saveUser();
        Manual manual = saveManual(user);
        ManualExtractionRun extraction = saveExtraction(manual, user);

        // when
        QuestionGenerationRun first = saveGeneration(extraction, user);
        QuestionGenerationRun second = saveGeneration(extraction, user);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(entityManager.find(ManualExtractionRun.class, extraction.getId()).getStatus())
                .isEqualTo(ManualExtractionStatus.ONGOING);
        QuestionGenerationRun saved = entityManager.find(QuestionGenerationRun.class, second.getId());
        assertThat(saved.getStatus()).isEqualTo(QuestionGenerationStatus.ONGOING);
        assertThat(saved.getEndedAt()).isNull();
        assertThat(saved.getManualExtractionRun().getId()).isEqualTo(extraction.getId());
    }

    @Test
    @DisplayName("실행 상태를 생략한 SQL INSERT에도 DB 기본값 ONGOING을 적용한다")
    void 실행_상태_DB_기본값_적용_성공() {
        // given
        User user = saveUser();
        Manual manual = saveManual(user);
        entityManager.flush();

        // when
        entityManager.createNativeQuery(
                "insert into manual_extraction_run (current_stage, started_at, manual_id, extracted_by) "
                        + "values (1, CURRENT_TIMESTAMP, :manualId, :userId)")
                .setParameter("manualId", manual.getId()).setParameter("userId", user.getId()).executeUpdate();
        Number extractionId = (Number) entityManager.createNativeQuery(
                "select manual_extraction_run_id from manual_extraction_run where manual_id = :manualId")
                .setParameter("manualId", manual.getId()).getSingleResult();
        entityManager.createNativeQuery(
                "insert into question_generation_run "
                        + "(current_generation_stage, total_question_count, started_at, manual_extraction_run_id, requested_by) "
                        + "values (1, 0, CURRENT_TIMESTAMP, :extractionId, :userId)")
                .setParameter("extractionId", extractionId.longValue()).setParameter("userId", user.getId())
                .executeUpdate();

        // then
        assertThat(entityManager.createNativeQuery("select status from manual_extraction_run where manual_id = :id")
                .setParameter("id", manual.getId()).getSingleResult().toString()).isEqualTo("ONGOING");
        assertThat(entityManager.createNativeQuery("select status from question_generation_run where manual_extraction_run_id = :id")
                .setParameter("id", extractionId.longValue()).getSingleResult().toString()).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("문제 출처는 두 부모의 PK를 복합 PK와 FK로 저장하고 같은 지식 소스를 여러 문제에 연결한다")
    void 두_FK_복합키_저장과_조회_성공() {
        // given
        User user = saveUser();
        Manual manual = saveManual(user);
        ManualFile file = persist(ManualFile.builder().manual(manual).originalName("manual.pdf")
                .fileType(ManualFileType.PDF).storageKey("a".repeat(32)).sizeBytes(100L).build());
        ManualExtractionRun extraction = saveExtraction(manual, user);
        KnowledgeSection section = persist(KnowledgeSection.builder().title("절차").sortOrder(1)
                .contentJson("{\"steps\":[]}").manualExtractionRun(extraction).build());
        KnowledgeSource knowledge = persist(KnowledgeSource.builder().manualFile(file).knowledgeSection(section)
                .startPage(1).endPage(2).heading("기본 절차").excerpt("본문").build());
        QuestionGenerationRun generation = saveGeneration(extraction, user);
        Question first = saveQuestion(generation);
        Question second = saveQuestion(generation);

        // when
        persist(QuestionSource.builder().knowledgeSource(knowledge).question(first).build());
        persist(QuestionSource.builder().knowledgeSource(knowledge).question(second).build());
        entityManager.flush();
        entityManager.clear();

        // then
        QuestionSourceId firstId = new QuestionSourceId(knowledge.getId(), first.getId());
        QuestionSourceId secondId = new QuestionSourceId(knowledge.getId(), second.getId());
        assertThat(entityManager.find(QuestionSource.class, firstId).getKnowledgeSource().getId())
                .isEqualTo(knowledge.getId());
        assertThat(entityManager.find(QuestionSource.class, secondId).getQuestion().getId())
                .isEqualTo(second.getId());
        Question saved = entityManager.find(Question.class, first.getId());
        assertThat(saved.getReviewedBy()).isNull();
        assertThat(saved.getReviewedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getModifiedAt()).isNotNull();
        assertThat(entityManager.createNativeQuery("select modified_at from question where question_id = :id")
                .setParameter("id", first.getId()).getSingleResult()).isNotNull();
    }

    @Test
    @DisplayName("교범 하나에 두 파일을 연결하면 DB의 일대일 제약으로 거부한다")
    void 동일_교범의_중복_파일_저장_실패() {
        // given
        Manual manual = saveManual(saveUser());
        persist(ManualFile.builder().manual(manual).originalName("first.pdf")
                .fileType(ManualFileType.PDF).storageKey("a".repeat(32)).sizeBytes(100L).build());
        entityManager.flush();

        // when & then
        assertThatThrownBy(() -> {
            persist(ManualFile.builder().manual(manual).originalName("second.pdf")
                    .fileType(ManualFileType.PDF).storageKey("b".repeat(32)).sizeBytes(100L).build());
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    @Test
    @DisplayName("역할 없는 분기는 여러 개 저장할 수 있지만 같은 역할을 두 분기에 지정할 수 없다")
    void 분기_선택적_일대일_제약_검증() {
        // given
        User user = saveUser();
        Question question = saveQuestion(saveGeneration(saveExtraction(saveManual(user), user), user));
        SquadRole role = persist(SquadRole.builder().name("분대장").expectedAction("지시").question(question).build());
        persist(branch(question, null));
        persist(branch(question, null));
        persist(branch(question, role));
        entityManager.flush();

        // when & then
        assertThatThrownBy(() -> {
            persist(branch(question, role));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private QuestionBranch branch(Question question, SquadRole role) {
        return QuestionBranch.builder().question(question).squadRole(role)
                .additionalSituationDescription("상황").query("행동은?").build();
    }

    private User saveUser() {
        return persist(User.builder().name("사용자").email("mapping@example.com")
                .password("encoded").role(UserRole.ROLE_USER).build());
    }

    private Manual saveManual(User user) {
        TrainingField field = persist(TrainingField.builder().name("훈련").build());
        return persist(Manual.builder().title("교범").trainingField(field).registeredBy(user).build());
    }

    private ManualExtractionRun saveExtraction(Manual manual, User user) {
        return persist(ManualExtractionRun.builder().manual(manual).extractedBy(user)
                .startedAt(LocalDateTime.now()).build());
    }

    private QuestionGenerationRun saveGeneration(ManualExtractionRun extraction, User user) {
        return persist(QuestionGenerationRun.builder().manualExtractionRun(extraction).requestedBy(user)
                .startedAt(LocalDateTime.now()).build());
    }

    private Question saveQuestion(QuestionGenerationRun generation) {
        return persist(Question.builder().title("문제").targetType(QuestionTargetType.INDIVIDUAL)
                .prevScenarioVideoGenerationPrompt("영상").prevScenarioEvaluationPrompt("평가")
                .trainingGoal("목표").questionGenerationRun(generation).build());
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
