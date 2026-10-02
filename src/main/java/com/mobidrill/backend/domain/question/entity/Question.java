package com.mobidrill.backend.domain.question.entity;

import com.mobidrill.backend.domain.question.enums.QuestionReviewStatus;
import com.mobidrill.backend.domain.question.enums.QuestionTargetType;
import com.mobidrill.backend.domain.user.entity.User;
import com.mobidrill.backend.global.entity.BaseTimeEntity;
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

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "question")
public class Question extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private QuestionTargetType targetType;

    @Column(name = "prev_scenario_video_generation_prompt", nullable = false, columnDefinition = "TEXT")
    private String prevScenarioVideoGenerationPrompt;

    @Column(name = "prev_scenario_evaluation_prompt", nullable = false, columnDefinition = "TEXT")
    private String prevScenarioEvaluationPrompt;

    @Column(name = "training_goal", nullable = false, length = 1000)
    private String trainingGoal;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    @Builder.Default
    private QuestionReviewStatus reviewStatus = QuestionReviewStatus.NEED;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", nullable = true)
    private User reviewedBy;

    @Column(name = "reviewed_at", nullable = true)
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_generation_run_id", nullable = false)
    private QuestionGenerationRun questionGenerationRun;
}
