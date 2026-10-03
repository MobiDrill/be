package com.mobidrill.backend.domain.training.controller;

import com.mobidrill.backend.domain.training.dto.TrainingFieldCreateReqDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldResDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldUpdateReqDto;
import com.mobidrill.backend.domain.training.service.TrainingFieldService;
import com.mobidrill.backend.global.response.CursorPageResponse;
import com.mobidrill.backend.global.response.GlobalResponse;
import com.mobidrill.backend.global.security.annotation.NeedAdminRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@NeedAdminRole
@RequiredArgsConstructor
public class TrainingFieldController implements TrainingFieldControllerDocs {

    private final TrainingFieldService trainingFieldService;

    @Override
    public ResponseEntity<GlobalResponse<TrainingFieldResDto>> createTrainingField(TrainingFieldCreateReqDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalResponse.success(201, "훈련 분야가 생성되었습니다.", trainingFieldService.createTrainingField(request)));
    }

    @Override
    public ResponseEntity<GlobalResponse<CursorPageResponse<TrainingFieldResDto>>> getTrainingFields(int page, int size) {
        return ResponseEntity.ok(GlobalResponse.success(trainingFieldService.getTrainingFields(page, size)));
    }

    @Override
    public ResponseEntity<GlobalResponse<TrainingFieldResDto>> updateTrainingField(Long trainingFieldId,
                                                                               TrainingFieldUpdateReqDto request) {
        return ResponseEntity.ok(GlobalResponse.success("훈련 분야가 수정되었습니다.",
                trainingFieldService.updateTrainingField(trainingFieldId, request)));
    }

    @Override
    public ResponseEntity<GlobalResponse<Void>> deleteTrainingField(Long trainingFieldId) {
        trainingFieldService.deleteTrainingField(trainingFieldId);
        return ResponseEntity.ok(GlobalResponse.success("훈련 분야가 삭제되었습니다.", null));
    }
}
