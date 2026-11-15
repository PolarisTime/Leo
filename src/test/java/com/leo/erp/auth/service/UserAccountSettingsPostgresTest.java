package com.leo.erp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserListColumnSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsSectionUpdatePayload;
import com.leo.erp.auth.web.dto.UserSettingsUpdateRequest;
import com.leo.erp.common.persistence.JpaAuditConfig;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 真实 PostgreSQL 往返回归（默认跳过，设置 {@code LEO_TEST_POSTGRES=true} 才执行）。
 *
 * <p>锁定单测测不到的那一段：{@code preferences_json} 是 JSONB，写入走
 * {@code CAST(:preferencesJson AS jsonb)} 原生 UPDATE、读取走 {@code preferences_json::text}
 * 的 {@code ColumnTransformer}。只有真库能验证「写进去的 v2 结构能被原样读回」以及
 * 「v1 老数据读得出来、写一次后升级为 v2」。</p>
 *
 * <p>用例自建 {@code PG-SETTINGS-} 前缀用户（{@code @DataJpaTest} 事务回滚），不依赖、不改动
 * 开发库既有数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditConfig.class)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class UserAccountSettingsPostgresTest {

    private static final long USER_ID = 943000000000000201L;
    private static final String LOGIN_NAME = "PG-SETTINGS-001";

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void saveSettings_shouldRoundTripThroughJsonb() throws Exception {
        seedUser();
        UserAccountSettingsService service = service();

        UserSettingsPayload saved = service.saveSettings(USER_ID,
                new UserSettingsUpdateRequest(Map.of("ui", new UserSettingsSectionUpdatePayload(
                        objectMapper.readTree("{\"fontSize\":14,\"themeMode\":\"system\"}"),
                        "2026-11-14T07:05:00Z")), null));

        assertThat(saved.sections()).containsOnlyKeys("ui");

        JsonNode persisted = objectMapper.readTree(storedPreferencesJson());
        assertThat(persisted.get("version").asInt()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(persisted.get("sections").get("ui").get("value").get("fontSize").asInt())
                .isEqualTo(14);
        assertThat(persisted.get("sections").get("ui").get("clientUpdatedAt").asText())
                .isEqualTo("2026-11-14T07:05:00Z");
        assertThat(persisted.get("sections").get("ui").get("serverUpdatedAt").asText()).isNotBlank();

        UserSettingsPayload reloaded = service.getSettings(USER_ID);
        assertThat(reloaded.sections().get("ui").value().get("themeMode").asText())
                .isEqualTo("system");
        assertThat(reloaded.sections().get("ui").clientUpdatedAt())
                .isEqualTo("2026-11-14T07:05:00Z");

        // pages 经同一个 PUT 写入：JSONB 往返后读回同构，且不会清掉已写好的 sections。
        UserSettingsPayload withPages = service.saveSettings(USER_ID,
                new UserSettingsUpdateRequest(null, Map.of("sales-order",
                        new UserListColumnSettingsPayload(List.of("no"), List.of(), Map.of("no", 120)))));
        assertThat(withPages.pages()).containsOnlyKeys("sales-order");
        assertThat(withPages.sections()).containsOnlyKeys("ui");

        UserSettingsPayload reloadedWithPages = service.getSettings(USER_ID);
        assertThat(reloadedWithPages.pages().get("sales-order").orderedKeys()).containsExactly("no");
        assertThat(reloadedWithPages.pages().get("sales-order").columnSizes())
                .containsEntry("no", 120);
        assertThat(reloadedWithPages.sections().get("ui").value().get("fontSize").asInt())
                .isEqualTo(14);
    }

    @Test
    void v1StoredData_shouldBeReadableAndUpgradedToV2OnWrite() throws Exception {
        seedUser();
        jdbc.update("update sys_user set preferences_json = cast(? as jsonb) where id = ?",
                "{\"pages\":{\"sales-order\":{\"orderedKeys\":[\"no\"],\"hiddenKeys\":[],"
                        + "\"columnSizes\":{\"no\":120}}}}",
                USER_ID);

        UserAccountSettingsService service = service();
        UserSettingsPayload payload = service.getSettings(USER_ID);
        assertThat(payload.version()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(payload.sections()).isEmpty();
        assertThat(payload.pages()).containsOnlyKeys("sales-order");

        // 旧 preferences 别名端点删除后，pages 的唯一写通路是 PUT /account/settings。
        service.saveSettings(USER_ID, new UserSettingsUpdateRequest(null, Map.of("projects",
                new UserListColumnSettingsPayload(List.of("name"), List.of(), Map.of()))));

        JsonNode persisted = objectMapper.readTree(storedPreferencesJson());
        assertThat(persisted.get("version").asInt()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(persisted.get("sections")).isNotNull();
        assertThat(persisted.get("pages").has("projects")).isTrue();
    }

    private UserAccountSettingsService service() {
        return new UserAccountSettingsService(repository, objectMapper);
    }

    private void seedUser() {
        jdbc.update("""
                insert into sys_user (id, login_name, password_hash, user_name, status,
                                      created_by, created_name, created_at,
                                      deleted_flag, version)
                values (?, ?, 'not-a-real-hash', 'PG Settings', 'NORMAL',
                        0, 'system', current_timestamp, false, 0)
                """, USER_ID, LOGIN_NAME);
    }

    private String storedPreferencesJson() {
        return jdbc.queryForObject(
                "select preferences_json::text from sys_user where id = ?", String.class, USER_ID);
    }
}
