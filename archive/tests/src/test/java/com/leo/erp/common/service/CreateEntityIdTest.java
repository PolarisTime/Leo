package com.leo.erp.common.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateEntityIdTest {

    @Test
    void shouldReportPreallocatedModuleKeyOnlyWhenPresent() {
        assertThat(new CreateEntityId(1L, "sales-order").hasPreallocatedModuleKey()).isTrue();
        assertThat(new CreateEntityId(1L, " sales-order ").hasPreallocatedModuleKey()).isTrue();
        assertThat(new CreateEntityId(1L, null).hasPreallocatedModuleKey()).isFalse();
        assertThat(new CreateEntityId(1L, " ").hasPreallocatedModuleKey()).isFalse();
    }
}
