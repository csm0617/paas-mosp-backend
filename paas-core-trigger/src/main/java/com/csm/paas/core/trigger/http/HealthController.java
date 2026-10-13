package com.csm.paas.core.trigger.http;

import com.csm.paas.core.api.dto.HealthResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 内部健康检查。刻意不用厂商契约路径，避免污染 /api/gaop/** 的契约比对。 */
@RestController
@RequestMapping("/internal")
public class HealthController {

    @GetMapping("/health")
    public HealthResponseDTO health() {
        return HealthResponseDTO.builder()
                .status("UP")
                .application("paas-core")
                .version("1.0.0-SNAPSHOT")
                .build();
    }
}
