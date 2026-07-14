package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdConfigurationYamlTest {

    @Test
    void applicationYamlDefinesStrictMachineIdDefault() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySource = loader.load("application", new ClassPathResource("application.yml")).getFirst();

        assertThat(propertySource.getProperty("leo.id.machine-id"))
                .isEqualTo("${LEO_MACHINE_ID:0}");
        assertThat(propertySource.getProperty("leo.id.strict-machine-id"))
                .isEqualTo("${LEO_ID_STRICT_MACHINE_ID:false}");
    }

    @Test
    void productionYamlEnablesStrictMachineIdByDefault() throws Exception {
        var loader = new YamlPropertySourceLoader();
        var propertySource = loader.load("application-prod", new ClassPathResource("application-prod.yml")).getFirst();

        assertThat(propertySource.getProperty("leo.id.strict-machine-id"))
                .isEqualTo("${LEO_ID_STRICT_MACHINE_ID:true}");
    }
}
