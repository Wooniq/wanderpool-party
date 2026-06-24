package com.wanderpool.be.party.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@Tag(name = "헬스체크 API")
public class HealthController {

    @GetMapping
    @Operation(summary = "헬스 체크 API")
    public String health() {
        return "모든 개발자 여러분 화이팅";
    }
}
