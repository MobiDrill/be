package com.mobidrill.backend.domain.question.entity;

import com.mobidrill.backend.domain.manual.entity.ManualExtractionRun;
import com.mobidrill.backend.domain.question.enums.QuestionGenerationStatus;
import com.mobidrill.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "question_generation_run")
public class QuestionGenerationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_generation_run_id")
    private Long id;

    @Column(name = "current_generation_stage", nullable = false)
    @Builder.Default
    private Integer currentGenerationStage = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @ColumnDefault("'ONGOING'")
    @Builder.Default
    private QuestionGenerationStatus status = QuestionGenerationStatus.ONGOING;

    @Column(name = "total_question_count", nullable = false)
    @Builder.Default
    private Integer totalQuestionCount = 0;

    @Column(name = "error_message", nullable = true, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = true)
    private LocalDateTime endedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manual_extraction_run_id", nullable = false)
    private ManualExtractionRun manualExtractionRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;
}
