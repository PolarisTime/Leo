package com.leo.erp.system.web;

import com.leo.erp.system.service.HealthPageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@ConditionalOnProperty(prefix = "leo.health.page", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthPageController {

    private final HealthPageService healthPageService;

    public HealthPageController(HealthPageService healthPageService) {
        this.healthPageService = healthPageService;
    }

    @GetMapping(value = "/system/health", produces = MediaType.TEXT_HTML_VALUE)
    public String health() {
        return healthPageService.render();
    }
}
