package com.mobidrill.backend.domain.manual.repository;

import com.mobidrill.backend.domain.manual.entity.ManualFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualFileRepository extends JpaRepository<ManualFile, Long> {
}
