package com.leo.erp.system.company.service;

import com.leo.erp.system.company.config.CompanyBootstrapProperties;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CompanyBootstrapRunnerTest {

    @Test
    void shouldLogWarning_whenBootstrapEnabled() {
        var properties = new CompanyBootstrapProperties();
        properties.setEnabled(true);
        var repository = mock(CompanySettingRepository.class);
        var idGenerator = mock(SnowflakeIdGenerator.class);
        var runner = new CompanyBootstrapRunner(repository, idGenerator, properties);

        runner.run(mock(ApplicationArguments.class));
    }

    @Test
    void shouldDoNothing_whenBootstrapDisabled() {
        var properties = new CompanyBootstrapProperties();
        properties.setEnabled(false);
        var repository = mock(CompanySettingRepository.class);
        var idGenerator = mock(SnowflakeIdGenerator.class);
        var runner = new CompanyBootstrapRunner(repository, idGenerator, properties);

        runner.run(mock(ApplicationArguments.class));
    }
}
