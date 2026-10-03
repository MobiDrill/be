package com.mobidrill.backend.domain.training.controller;

import com.mobidrill.backend.domain.training.dto.TrainingFieldCreateReqDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldResDto;
import com.mobidrill.backend.domain.training.dto.TrainingFieldUpdateReqDto;
import com.mobidrill.backend.global.response.CursorPageResponse;
import com.mobidrill.backend.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Training Field", description = "관리자 훈련 분야 관리 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력값 오류"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "관리자 권한 없음"),
        @ApiResponse(responseCode = "500", description = "DB 처리 실패")
})
@RequestMapping("/api/v1/training-fields")
public interface TrainingFieldControllerDocs {

    @Operation(summary = "훈련 분야 생성", description = "이름은 공백을 제외한 내용이 필요하며 최대 100자입니다. isActive를 생략하면 true입니다.")
    @ApiResponse(responseCode = "201", description = "생성 완료")
    @PostMapping
    ResponseEntity<GlobalResponse<TrainingFieldResDto>> createTrainingField(
            @Valid @RequestBody TrainingFieldCreateReqDto request);

    @Operation(summary = "훈련 분야 페이지 조회", description = "활성·비활성 분야를 모두 ID 내림차순으로 조회합니다. page는 1 이상, size는 1~100입니다. nextCursor는 null입니다.")
    @ApiResponse(responseCode = "200", description = "조회 완료")
    @GetMapping
    ResponseEntity<GlobalResponse<CursorPageResponse<TrainingFieldResDto>>> getTrainingFields(
            @Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지 크기 (1~100)") @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "훈련 분야 수정", description = "이름과 isActive를 모두 전달합니다. 비활성으로 변경해도 기존 교범 연결을 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료"),
            @ApiResponse(responseCode = "404", description = "훈련 분야 없음")
    })
    @PutMapping("/{trainingFieldId}")
    ResponseEntity<GlobalResponse<TrainingFieldResDto>> updateTrainingField(
            @Parameter(description = "양수 훈련 분야 ID", required = true) @PathVariable Long trainingFieldId,
            @Valid @RequestBody TrainingFieldUpdateReqDto request);

    @Operation(summary = "훈련 분야 삭제", description = "연관 교범의 훈련 분야 연결만 null로 해제하고 분야를 삭제합니다. 교범·교범 파일·추출 기록과 원본 파일을 보존합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 완료"),
            @ApiResponse(responseCode = "404", description = "훈련 분야 없음")
    })
    @DeleteMapping("/{trainingFieldId}")
    ResponseEntity<GlobalResponse<Void>> deleteTrainingField(
            @Parameter(description = "양수 훈련 분야 ID", required = true) @PathVariable Long trainingFieldId);
}
