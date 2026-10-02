package com.mobidrill.backend.domain.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
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
@Table(name = "question_branch")
public class QuestionBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_branch_id")
    private Long id;

    @Column(name = "additional_situation_description", nullable = false, length = 1000)
    private String additionalSituationDescription;

    @Column(name = "query", nullable = false, length = 1000)
    private String query;

    @Column(name = "correct_answer_count", nullable = false)
    @Builder.Default
    private Integer correctAnswerCount = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "squad_role_id", nullable = true, unique = true)
    private SquadRole squadRole;
}
