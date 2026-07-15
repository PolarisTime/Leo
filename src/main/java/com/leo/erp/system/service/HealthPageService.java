package com.leo.erp.system.service;

import com.leo.erp.common.support.DateTimeFormatSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        return renderer.render(DateTimeFormatSupport.now(), jvm);
    }
}
