package com.mobidrill.backend.global.health;

import com.mobidrill.backend.global.response.GlobalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Health", description = "애플리케이션 상태 확인 API")
@RequestMapping("/api/v1/health")
public interface HealthControllerDocs {

    @Operation(summary = "애플리케이션 상태 확인")
    @GetMapping
    ResponseEntity<GlobalResponse<String>> health();
}
