package com.mobidrill.backend.global.health;

import com.mobidrill.backend.global.response.GlobalResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController implements HealthControllerDocs {

    @Override
    public ResponseEntity<GlobalResponse<String>> health() {
        return ResponseEntity.ok(GlobalResponse.success("MobiDrill Backend OK"));
    }
}
