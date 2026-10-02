package com.mobidrill.backend.domain.manual.controller;

import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterResDto;
import com.mobidrill.backend.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Manual", description = "교범 관리 API")
@RequestMapping("/api/v1/manuals")
public interface ManualControllerDocs {

    @Operation(summary = "교범 등록", description = """
            multipart/form-data로 request(JSON, application/json)와 file(원본 파일 1개)을 전달합니다.
            ROLE_ADMIN 권한을 가진 사용자만 등록할 수 있습니다.
            제목은 필수이며 최대 100자, 설명은 선택이며 최대 1000자입니다.
            활성 훈련 분야 ID를 지정해야 합니다. 기본 파일 제한은 50MB입니다.
            PDF/PPT/PPTX/DOC/DOCX/HWP 5의 실제 형식과 확장자를 검증합니다.
            원본은 {storageKey}.{소문자 확장자}로 저장하며 등록 상태는 TEMPORARY_SAVED입니다.
            """, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE))))
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "교범 등록 완료"),
            @ApiResponse(responseCode = "400", description = "입력 오류, 파일 불일치 또는 비활성 훈련 분야"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "관리자 권한 없음 또는 비활성 사용자"),
            @ApiResponse(responseCode = "404", description = "훈련 분야 또는 사용자 없음"),
            @ApiResponse(responseCode = "413", description = "파일 또는 요청 용량 초과"),
            @ApiResponse(responseCode = "415", description = "지원하지 않는 파일 형식 또는 Content-Type"),
            @ApiResponse(responseCode = "500", description = "파일 또는 DB 저장 실패")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<GlobalResponse<ManualRegisterResDto>> registerManual(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "userAuthDto.userId", errorOnInvalidType = true) Long userId,
            @Parameter(description = "교범 정보 JSON (application/json)", required = true)
            @Valid @RequestPart("request") ManualRegisterReqDto request,
            @Parameter(description = "교범 원본 파일", required = true)
            @RequestPart("file") MultipartFile file
    );
}
