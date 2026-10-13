package com.leo.erp.system.service;

import com.leo.erp.common.support.DateTimeFormatSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class HealthPageService {

    private final HealthPageRenderer renderer;

    public HealthPageService(HealthPageRenderer renderer) {
        this.renderer = renderer;
    }

    public String render() {
        HealthPageRenderer.JvmStatus jvm = renderer.getJvmStatus();
        log.debug("health page requested: appStatus=UP, jvmVersion={}", jvm.javaVersion());
        // 页面上的「检查时间」是给人看的，用中文展示口径；/health JSON 的 checkedAt 仍是 ISO。
        return renderer.render(DateTimeFormatSupport.displayDateTime(LocalDateTime.now()), jvm);
    }
}
