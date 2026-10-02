package com.mobidrill.backend.domain.manual.repository;

import com.mobidrill.backend.domain.manual.entity.Manual;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualRepository extends JpaRepository<Manual, Long> {
}
