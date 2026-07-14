package com.leo.erp.system.support;

import com.leo.erp.common.config.RedisTuningProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTuningPropertiesScanTest {

    @Test
    void shouldExposeScanBatchSize() {
        RedisTuningProperties.Scan scan = new RedisTuningProperties.Scan();

        assertThat(scan.getBatchSize()).isEqualTo(256);

        scan.setBatchSize(512);

        assertThat(scan.getBatchSize()).isEqualTo(512);
    }
}
