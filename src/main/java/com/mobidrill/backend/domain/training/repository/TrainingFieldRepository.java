package com.mobidrill.backend.domain.training.repository;

import com.mobidrill.backend.domain.training.entity.TrainingField;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingFieldRepository extends JpaRepository<TrainingField, Long> {
}
