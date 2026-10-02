package com.mobidrill.backend.domain.manual.controller;

import com.mobidrill.backend.domain.manual.dto.ManualRegisterReqDto;
import com.mobidrill.backend.domain.manual.dto.ManualRegisterResDto;
import com.mobidrill.backend.domain.manual.service.ManualService;
import com.mobidrill.backend.global.response.GlobalResponse;
import com.mobidrill.backend.global.security.annotation.NeedAdminRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ManualController implements ManualControllerDocs {

    private final ManualService manualService;

    @Override
    @NeedAdminRole
    public ResponseEntity<GlobalResponse<ManualRegisterResDto>> registerManual(
            @AuthenticationPrincipal(expression = "userAuthDto.userId", errorOnInvalidType = true) Long userId,
            ManualRegisterReqDto request, MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalResponse.success(201, "교범이 등록되었습니다.", manualService.registerManual(userId, request, file)));
    }
}
