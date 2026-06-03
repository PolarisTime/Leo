package com.leo.erp.system.schedule.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

class MaintenanceSchedulingConfigTest {

    @Test
    void shouldCreateTaskScheduler() {
        var config = new MaintenanceSchedulingConfig();

        ThreadPoolTaskScheduler scheduler = config.taskScheduler();

        assertThat(scheduler).isNotNull();
        assertThat(scheduler.getPoolSize()).isEqualTo(3);
        assertThat(scheduler.getThreadNamePrefix()).isEqualTo("leo-maintenance-");
    }
}
