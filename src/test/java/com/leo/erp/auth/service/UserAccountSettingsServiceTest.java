package com.leo.erp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserListColumnSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsSectionUpdatePayload;
import com.leo.erp.auth.web.dto.UserSettingsUpdateRequest;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 设置同步服务的行为锁定：v1 兼容读取、分片 LWW（较新覆盖 / 较旧 409 / 相同幂等）、
 * supplierQuote 材质深度合并、未知分片保留、空请求体不写库、32KB/256KB 限额、pages 列设置整段替换
 * 与读写同构、以及 pages/sections 同请求时的原子性。
 *
 * <p>断言口径是「响应 + 实际写进仓库的 JSON」两边都查：只看返回值会漏掉「内存里对、落库写错了」
 * 这类缺陷（{@code preferencesJson} 是字符串，编译器管不到结构）。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAccountSettingsServiceTest {

    private static final long USER_ID = 900000000000000001L;

    @Mock
    private UserAccountRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserAccountSettingsService service() {
        return new UserAccountSettingsService(repository, objectMapper);
    }

    // ------------------------------------------------------------------
    // 读取：v1 兼容 / 空 / 坏数据
    // ------------------------------------------------------------------

    @Test
    void getSettings_whenStoredIsV1OnlyPages_shouldReadPagesAndExposeEmptySectionsV2() {
        UserAccount account = accountWith("""
                {"pages":{"sales-order":{"orderedKeys":["no","customer"],"hiddenKeys":["remark"],
                "columnSizes":{"no":120}}}}
                """);
        when(repository.findByIdAndDeletedFlagFalse(USER_ID)).thenReturn(Optional.of(account));

        UserSettingsPayload payload = service().getSettings(USER_ID);

        assertThat(payload.version()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(payload.sections()).isEmpty();
        assertThat(payload.pages()).containsOnlyKeys("sales-order");
        UserListColumnSettingsPayload page = payload.pages().get("sales-order");
        assertThat(page.orderedKeys()).containsExactly("no", "customer");
        assertThat(page.hiddenKeys()).containsExactly("remark");
        assertThat(page.columnSizes()).containsEntry("no", 120);
    }

    @Test
    void getSettings_whenPreferencesJsonBlank_shouldReturnEmptyV2Payload() {
        UserAccount account = accountWith("   ");
        when(repository.findByIdAndDeletedFlagFalse(USER_ID)).thenReturn(Optional.of(account));

        UserSettingsPayload payload = service().getSettings(USER_ID);

        assertThat(payload.version()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(payload.sections()).isEmpty();
        assertThat(payload.pages()).isEmpty();
    }

    @Test
    void getSettings_whenPreferencesJsonMalformed_shouldDegradeToEmptyV2Payload() {
        UserAccount account = accountWith("{not-json");
        when(repository.findByIdAndDeletedFlagFalse(USER_ID)).thenReturn(Optional.of(account));

        UserSettingsPayload payload = service().getSettings(USER_ID);

        // 旧实现同样降级为空配置：一条坏数据不能把用户设置永久卡死。
        assertThat(payload.version()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(payload.sections()).isEmpty();
        assertThat(payload.pages()).isEmpty();
    }

    @Test
    void getSettings_whenUserMissing_shouldThrowNotFound() {
        when(repository.findByIdAndDeletedFlagFalse(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getSettings(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    // ------------------------------------------------------------------
    // 覆盖型分片：LWW
    // ------------------------------------------------------------------

    @Test
    void saveSettings_whenTimestampIsNewer_shouldOverwriteAndEchoClientTimestamp() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":12},
                "clientUpdatedAt":"2026-11-14T07:00:00Z","serverUpdatedAt":"2026-11-14T07:00:01Z"}},
                "pages":{}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("ui", "{\"fontSize\":14,\"themeMode\":\"system\"}",
                        "2026-11-14T07:05:00Z"));

        assertThat(payload.sections().get("ui").value().get("fontSize").asInt()).isEqualTo(14);
        assertThat(payload.sections().get("ui").clientUpdatedAt()).isEqualTo("2026-11-14T07:05:00Z");
        assertThat(payload.sections().get("ui").serverUpdatedAt()).isNotBlank();

        JsonNode persisted = persistedJson();
        assertThat(persisted.get("version").asInt()).isEqualTo(2);
        assertThat(persisted.get("sections").get("ui").get("clientUpdatedAt").asText())
                .isEqualTo("2026-11-14T07:05:00Z");
    }

    @Test
    void saveSettings_whenClientTimestampIsOlder_shouldThrowConflictCarryingServerValue() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":16},
                "clientUpdatedAt":"2026-11-14T07:10:00Z","serverUpdatedAt":"2026-11-14T07:10:01Z"}},
                "pages":{}}
                """);

        UserSettingsUpdateRequest request = updateRequest("ui",
                "{\"fontSize\":14}", "2026-11-14T07:05:00Z");

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOfSatisfying(UserSettingsConflictException.class, ex -> {
                    assertThat(ex.getConflictSections()).containsExactly("ui");
                    assertThat(ex.getServerSections().get("ui").value().get("fontSize").asInt())
                            .isEqualTo(16);
                    assertThat(ex.getServerSections().get("ui").clientUpdatedAt())
                            .isEqualTo("2026-11-14T07:10:00Z");
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
                });
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_whenTimestampAndValueUnchanged_shouldBeIdempotentWithoutWriting() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":14},
                "clientUpdatedAt":"2026-11-14T07:05:00Z","serverUpdatedAt":"2026-11-14T07:05:01Z"}},
                "pages":{}}
                """);

        UserSettingsUpdateRequest request = updateRequest("ui",
                "{\"fontSize\":14}", "2026-11-14T07:05:00Z");

        UserSettingsPayload first = service().saveSettings(USER_ID, request);
        UserSettingsPayload second = service().saveSettings(USER_ID, request);

        assertThat(first).isEqualTo(second);
        assertThat(first.sections().get("ui").serverUpdatedAt())
                .isEqualTo("2026-11-14T07:05:01Z");
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_whenTimestampEqualsButValueDiffers_shouldOverwrite() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":12},
                "clientUpdatedAt":"2026-11-14T07:05:00Z","serverUpdatedAt":"2026-11-14T07:05:01Z"}},
                "pages":{}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("ui", "{\"fontSize\":18}", "2026-11-14T07:05:00Z"));

        assertThat(payload.sections().get("ui").value().get("fontSize").asInt()).isEqualTo(18);
        verify(repository).updatePreferencesJson(eq(USER_ID), anyString());
    }

    // ------------------------------------------------------------------
    // 合并型分片：supplierQuote.materialTabs
    // ------------------------------------------------------------------

    @Test
    void saveSettings_supplierQuote_shouldDeepMergeMaterialTabsBySupplier() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"supplierQuote":{"value":{
                  "openSupplierIds":["111"],"activeSupplierId":"111",
                  "materialTabs":{"111":{"直条":"HRB400E"},"222":{"盘螺":"HRB400"}}},
                "clientUpdatedAt":"2026-11-14T07:00:00Z","serverUpdatedAt":"2026-11-14T07:00:01Z"}},
                "pages":{}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("supplierQuote", """
                        {"openSupplierIds":["111","222"],"activeSupplierId":"222",
                         "materialTabs":{"111":{"直条":"HRB500","盘螺":"HRB400"},
                                         "222":{"直条":"HRB335"}}}
                        """, "2026-11-14T07:05:00Z"));

        JsonNode tabs = payload.sections().get("supplierQuote").value().get("materialTabs");
        // 服务端独有的 222→盘螺 保留；222→直条 来自客户端；111 下客户端覆盖 直条、新增 盘螺。
        assertThat(tabs.get("222").get("盘螺").asText()).isEqualTo("HRB400");
        assertThat(tabs.get("222").get("直条").asText()).isEqualTo("HRB335");
        assertThat(tabs.get("111").get("直条").asText()).isEqualTo("HRB500");
        assertThat(tabs.get("111").get("盘螺").asText()).isEqualTo("HRB400");
        // 非 materialTabs 字段仍按 LWW 整体覆盖。
        assertThat(payload.sections().get("supplierQuote").value().get("activeSupplierId").asText())
                .isEqualTo("222");
        assertThat(persistedJson().get("sections").get("supplierQuote").get("value")
                .get("materialTabs").get("222").get("盘螺").asText()).isEqualTo("HRB400");
    }

    @Test
    void saveSettings_uiSection_shouldNotDeepMergeJustOverwrite() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"toursSeen":["price-compare"]},
                "clientUpdatedAt":"2026-11-14T07:00:00Z","serverUpdatedAt":"2026-11-14T07:00:01Z"}},
                "pages":{}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("ui", "{\"fontSize\":14}", "2026-11-14T07:05:00Z"));

        // 覆盖型分片整段替换：旧字段不会「残留」成半新半旧的状态。
        assertThat(payload.sections().get("ui").value().has("toursSeen")).isFalse();
    }

    // ------------------------------------------------------------------
    // 未知分片 / 空请求体
    // ------------------------------------------------------------------

    @Test
    void saveSettings_unknownSection_shouldBePreservedAsIs() {
        givenStoredForUpdate("""
                {"version":2,"sections":{
                  "ui":{"value":{"fontSize":12},"clientUpdatedAt":"2026-11-14T07:00:00Z",
                        "serverUpdatedAt":"2026-11-14T07:00:01Z"},
                  "experimental":{"value":{"alpha":true},"clientUpdatedAt":"2026-11-14T06:00:00Z",
                        "serverUpdatedAt":"2026-11-14T06:00:01Z"}},
                "pages":{}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("ui", "{\"fontSize\":14}", "2026-11-14T07:05:00Z"));

        assertThat(payload.sections()).containsKeys("ui", "experimental");
        assertThat(payload.sections().get("experimental").value().get("alpha").asBoolean()).isTrue();
        assertThat(persistedJson().get("sections").get("experimental").get("value")
                .get("alpha").asBoolean()).isTrue();
    }

    @Test
    void saveSettings_whenSectionsEmptyOrNull_shouldReturnCurrentStateWithoutWriting() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":12},
                "clientUpdatedAt":"2026-11-14T07:00:00Z","serverUpdatedAt":"2026-11-14T07:00:01Z"}},
                "pages":{}}
                """);

        UserSettingsPayload emptyMap = service().saveSettings(USER_ID, new UserSettingsUpdateRequest(Map.of(), null));
        UserSettingsPayload nullBody = service().saveSettings(USER_ID, null);

        assertThat(emptyMap.sections()).containsOnlyKeys("ui");
        assertThat(nullBody).isEqualTo(emptyMap);
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_nullSectionValue_shouldThrowValidationError() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{}}
                """);

        UserSettingsUpdateRequest request = new UserSettingsUpdateRequest(Map.of("ui",
                new UserSettingsSectionUpdatePayload(null, "2026-11-14T07:05:00Z")), null);

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_blankSectionKey_shouldThrowValidationError() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{}}
                """);

        UserSettingsUpdateRequest request = new UserSettingsUpdateRequest(Map.of("   ",
                section("{\"fontSize\":14}", "2026-11-14T07:05:00Z")), null);

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ------------------------------------------------------------------
    // 限额：413
    // ------------------------------------------------------------------

    @Test
    void saveSettings_sectionOver32KB_shouldThrowPayloadTooLarge() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{}}
                """);

        String oversized = "x".repeat(UserAccountSettingsService.SECTION_MAX_BYTES + 1);
        UserSettingsUpdateRequest request = updateRequest("ui",
                "{\"blob\":\"" + oversized + "\"}", "2026-11-14T07:05:00Z");

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_totalOver256KB_shouldThrowPayloadTooLarge() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{}}
                """);

        // 每个分片都在 32KB 以内，但 9 个 30KB 分片叠加后总量越过 256KB。
        Map<String, UserSettingsSectionUpdatePayload> sections = new LinkedHashMap<>();
        String blob = "y".repeat(30 * 1024);
        for (int i = 0; i < 9; i++) {
            sections.put("s" + i, section("{\"blob\":\"" + blob + "\"}", "2026-11-14T07:05:00Z"));
        }
        UserSettingsUpdateRequest request = new UserSettingsUpdateRequest(sections, null);

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    // ------------------------------------------------------------------
    // pages（列设置）：整段替换、读写同构、与 sections 同请求的原子性
    // ------------------------------------------------------------------

    @Test
    void saveSettings_pages_shouldReplacePagesKeepSectionsAndApplyV1Normalization() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":14},
                "clientUpdatedAt":"2026-11-14T07:00:00Z","serverUpdatedAt":"2026-11-14T07:00:01Z"}},
                "pages":{"orders":{"orderedKeys":["no"],"hiddenKeys":[],"columnSizes":{}}}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                new UserSettingsUpdateRequest(null, Map.of("sales-order",
                        new UserListColumnSettingsPayload(List.of("no"), List.of(),
                                Map.of("no", 60, "customer", 900)))));

        // v1 savePreferences 的规整规则原样保留：列宽夹到 80~800；pages 整段替换（旧 orders 不保留）。
        assertThat(payload.pages()).containsOnlyKeys("sales-order");
        assertThat(payload.pages().get("sales-order").columnSizes())
                .containsEntry("no", 80)
                .containsEntry("customer", 800);

        JsonNode persisted = persistedJson();
        assertThat(persisted.get("version").asInt()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(persisted.get("sections").get("ui").get("value").get("fontSize").asInt())
                .isEqualTo(14);
        assertThat(persisted.get("pages").has("orders")).isFalse();
        assertThat(persisted.get("pages").get("sales-order").get("columnSizes").get("no").asInt())
                .isEqualTo(80);
    }

    @Test
    void saveSettings_whenPagesAndSectionsInSameRequest_shouldPersistBoth() {
        givenStoredForUpdate("""
                {"pages":{"orders":{"orderedKeys":["no"],"hiddenKeys":[],"columnSizes":{}}}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                new UserSettingsUpdateRequest(
                        Map.of("ui", section("{\"fontSize\":14}", "2026-11-14T07:05:00Z")),
                        Map.of("projects", new UserListColumnSettingsPayload(
                                List.of("name"), List.of(), Map.of()))));

        assertThat(payload.sections()).containsOnlyKeys("ui");
        assertThat(payload.pages()).containsOnlyKeys("projects");

        // 落库为 v2：v1 旧数据在本次写入时升级，pages 写进去什么样、读出来就什么样。
        JsonNode persisted = persistedJson();
        assertThat(persisted.get("version").asInt()).isEqualTo(UserSettingsPayload.CURRENT_VERSION);
        assertThat(persisted.get("sections").get("ui").get("value").get("fontSize").asInt())
                .isEqualTo(14);
        assertThat(persisted.get("pages").has("projects")).isTrue();
        assertThat(persisted.get("pages").get("projects").get("orderedKeys").get(0).asText())
                .isEqualTo("name");
    }

    @Test
    void saveSettings_whenPagesUnchanged_shouldBeIdempotentWithoutWriting() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{"orders":{"orderedKeys":["no"],
                "hiddenKeys":[],"columnSizes":{"no":120}}}}
                """);

        // 服务端读回时同样走 normalizePages，提交内容与读回口径一致即视为无变化。
        UserSettingsPayload payload = service().saveSettings(USER_ID,
                new UserSettingsUpdateRequest(null, Map.of("orders",
                        new UserListColumnSettingsPayload(List.of("no"), List.of(), Map.of("no", 120)))));

        assertThat(payload.pages()).containsOnlyKeys("orders");
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_whenSectionConflict_shouldNotPersistPagesEither() {
        givenStoredForUpdate("""
                {"version":2,"sections":{"ui":{"value":{"fontSize":16},
                "clientUpdatedAt":"2026-11-14T07:10:00Z","serverUpdatedAt":"2026-11-14T07:10:01Z"}},
                "pages":{}}
                """);

        UserSettingsUpdateRequest request = new UserSettingsUpdateRequest(
                Map.of("ui", section("{\"fontSize\":14}", "2026-11-14T07:05:00Z")),
                Map.of("projects", new UserListColumnSettingsPayload(
                        List.of("name"), List.of(), Map.of())));

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOf(UserSettingsConflictException.class);
        // 同一次请求里 sections 冲突时，pages 也不落库（不做半成功写入）。
        verify(repository, never()).updatePreferencesJson(eq(USER_ID), anyString());
    }

    @Test
    void saveSettings_whenPagesNull_shouldKeepExistingPages() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{"orders":{"orderedKeys":["no"],
                "hiddenKeys":[],"columnSizes":{}}}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("ui", "{\"fontSize\":14}", "2026-11-14T07:05:00Z"));

        assertThat(payload.pages()).containsOnlyKeys("orders");
        assertThat(persistedJson().get("pages").has("orders")).isTrue();
    }

    @Test
    void saveSettings_whenPagesExplicitlyEmpty_shouldClearPages() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{"orders":{"orderedKeys":["no"],
                "hiddenKeys":[],"columnSizes":{}}}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                new UserSettingsUpdateRequest(null, Map.of()));

        assertThat(payload.pages()).isEmpty();
        assertThat(persistedJson().get("pages").size()).isZero();
    }

    // ------------------------------------------------------------------
    // 中文时间戳格式
    // ------------------------------------------------------------------

    @Test
    void saveSettings_shouldAcceptOffsetDateTimeFormat() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{}}
                """);
        givenUpdateSucceeds();

        UserSettingsPayload payload = service().saveSettings(USER_ID,
                updateRequest("ui", "{\"fontSize\":14}", "2026-11-14T15:05:00+08:00"));

        assertThat(payload.sections().get("ui").clientUpdatedAt())
                .isEqualTo("2026-11-14T15:05:00+08:00");
    }

    @Test
    void saveSettings_invalidTimestamp_shouldThrowValidationError() {
        givenStoredForUpdate("""
                {"version":2,"sections":{},"pages":{}}
                """);

        UserSettingsUpdateRequest request = updateRequest("ui", "{\"fontSize\":14}", "昨天下午");

        assertThatThrownBy(() -> service().saveSettings(USER_ID, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private UserAccount accountWith(String preferencesJson) {
        UserAccount account = new UserAccount();
        account.setId(USER_ID);
        account.setPreferencesJson(preferencesJson);
        return account;
    }

    private void givenStoredForUpdate(String preferencesJson) {
        lenient().when(repository.findByIdAndDeletedFlagFalseForUpdate(USER_ID))
                .thenReturn(Optional.of(accountWith(preferencesJson)));
    }

    private void givenUpdateSucceeds() {
        lenient().when(repository.updatePreferencesJson(eq(USER_ID), anyString())).thenReturn(1);
    }

    private UserSettingsUpdateRequest updateRequest(String sectionKey, String valueJson, String clientUpdatedAt) {
        return new UserSettingsUpdateRequest(Map.of(sectionKey, section(valueJson, clientUpdatedAt)), null);
    }

    private UserSettingsSectionUpdatePayload section(String valueJson, String clientUpdatedAt) {
        return new UserSettingsSectionUpdatePayload(json(valueJson), clientUpdatedAt);
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("测试数据不是合法 JSON", ex);
        }
    }

    private JsonNode persistedJson() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(repository).updatePreferencesJson(eq(USER_ID), captor.capture());
        return json(captor.getValue());
    }
}
