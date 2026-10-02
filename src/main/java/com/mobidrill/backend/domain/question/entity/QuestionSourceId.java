package com.mobidrill.backend.domain.question.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuestionSourceId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "knowledge_source_id", nullable = false)
    private Long knowledgeSourceId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;
}
