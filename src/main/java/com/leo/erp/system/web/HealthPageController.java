package com.leo.erp.system.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.system.service.HealthPageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "查看系统健康状态页", description = "返回运维用 HTML 健康页（text/html），依赖异常时页面展示降级信息；匿名可访问，用于人工巡检查看。")
    @GetMapping(value = ApiVersion.PREFIX + "/system/health", produces = MediaType.TEXT_HTML_VALUE)
    public String health() {
        return healthPageService.render();
    }
}
