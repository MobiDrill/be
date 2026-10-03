package com.mobidrill.backend.domain.manual.repository;

import com.mobidrill.backend.domain.manual.entity.Manual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManualRepository extends JpaRepository<Manual, Long> {

    /**
     * 교범을 보존하고 지정한 훈련 분야와의 연결만 일괄 해제한다.
     * @param trainingFieldId : 삭제할 분야 ID
     * @return : 연결을 해제한 교범 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Manual manual set manual.trainingField = null where manual.trainingField.id = :trainingFieldId")
    int clearTrainingField(@Param("trainingFieldId") Long trainingFieldId);
}
