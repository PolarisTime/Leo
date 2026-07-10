package com.leo.erp.system.setup;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapStateMigrationTest {

    @Test
    void v8ShouldCreateAndReconcileSingletonBootstrapState() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V8__add_bootstrap_state.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                    .contains("CREATE TABLE public.sys_bootstrap_state")
                    .contains("CHECK (id = 1)")
                    .contains("SYS_OOBE_COMPLETED")
                    .contains("role.role_code = 'ADMIN'")
                    .contains("public.sys_company_setting")
                    .contains("INSERT INTO public.sys_bootstrap_state");
        }
    }
}
