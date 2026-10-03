package com.mobidrill.backend.domain.training.repository;

import com.mobidrill.backend.domain.training.entity.TrainingField;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingFieldRepository extends JpaRepository<TrainingField, Long> {

    /**
     * 수정·삭제·교범 등록이 공유하는 분야 행의 배타 잠금을 획득한다.
     * @param id : 훈련 분야 ID
     * @return : 잠금한 분야 또는 빈 결과
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select field from TrainingField field where field.id = :id")
    Optional<TrainingField> findByIdForUpdate(@Param("id") Long id);
}
