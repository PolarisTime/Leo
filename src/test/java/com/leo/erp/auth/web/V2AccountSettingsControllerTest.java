package com.leo.erp.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.service.UserAccountService;
import com.leo.erp.auth.service.UserAccountSettingsService;
import com.leo.erp.auth.service.UserSettingsConflictException;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.auth.web.dto.UserListColumnSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsPayload;
import com.leo.erp.auth.web.dto.UserSettingsSectionPayload;
import com.leo.erp.auth.web.dto.UserSettingsUpdateRequest;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.security.support.SecurityPrincipal;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * {@code /account/settings} 的 HTTP 契约锁定：v2 响应结构、409 带 {@code sections}/{@code serverValue}、
 * 413、404、空请求体、以及 v1 别名 {@code /account/preferences} 仍然只返回 {@code pages}。
 *
 * <p>未认证的 401 不在这里测：它由全局安全链 {@code anyRequest().authenticated()} 统一拦截
 * （见 {@code SecurityConfig}），standalone MockMvc 不装配安全过滤器链，硬测只会测到
 * 「principal 为 null 时抛 IllegalStateException」，与本接口契约无关。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2AccountSettingsControllerTest {

    private static final long USER_ID = 900000000000000002L;
    private static final String SETTINGS_PATH = "/v2.0/account/settings";
    private static final String PREFERENCES_PATH = "/v2.0/account/preferences";

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private UserAccountSettingsService userAccountSettingsService;

    private final ObjectMapper objectMapper = objectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiProblemFactory problemFactory = new ApiProblemFactory("Asia/Shanghai");
        V2UserAccountController controller = new V2UserAccountController(
                userAccountService, userAccountSettingsService, problemFactory);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(problemFactory))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        authenticate();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    @Test
    void getSettings_shouldReturnV2Contract() throws Exception {
        when(userAccountSettingsService.getSettings(USER_ID)).thenReturn(payload());

        mockMvc.perform(get(SETTINGS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.sections.ui.value.fontSize").value(14))
                .andExpect(jsonPath("$.sections.ui.clientUpdatedAt").value("2026-11-14T07:05:00Z"))
                .andExpect(jsonPath("$.sections.ui.serverUpdatedAt").value("2026-11-14T07:05:01Z"))
                .andExpect(jsonPath("$.pages.sales-order.orderedKeys[0]").value("no"))
                .andExpect(jsonPath("$.pages.sales-order.columnSizes.no").value(120));

        verify(userAccountSettingsService).getSettings(USER_ID);
    }

    @Test
    void getPreferences_alias_shouldReturnPagesOnlyWithoutVersionOrSections() throws Exception {
        when(userAccountSettingsService.getPreferences(USER_ID)).thenReturn(preferences());

        mockMvc.perform(get(PREFERENCES_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.sales-order.orderedKeys[0]").value("no"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.sections").doesNotExist());

        verify(userAccountSettingsService).getPreferences(USER_ID);
    }

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    @Test
    void putSettings_shouldReturnMergedPayload() throws Exception {
        when(userAccountSettingsService.saveSettings(eq(USER_ID), any()))
                .thenReturn(payload());

        mockMvc.perform(put(SETTINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections":{"ui":{"value":{"fontSize":14},
                                 "clientUpdatedAt":"2026-11-14T07:05:00Z"}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.sections.ui.value.fontSize").value(14));
    }

    @Test
    void putSettings_whenBodyAbsent_shouldDelegateWithNullRequest() throws Exception {
        when(userAccountSettingsService.saveSettings(eq(USER_ID), isNull()))
                .thenReturn(payload());

        mockMvc.perform(put(SETTINGS_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        verify(userAccountSettingsService).saveSettings(eq(USER_ID), isNull());
    }

    @Test
    void putSettings_whenServerVersionIsNewer_shouldReturn409WithSectionsAndServerValue() throws Exception {
        UserSettingsSectionPayload serverSection = new UserSettingsSectionPayload(
                json("{\"fontSize\":16}"), "2026-11-14T07:10:00Z", "2026-11-14T07:10:01Z");
        when(userAccountSettingsService.saveSettings(eq(USER_ID), any()))
                .thenThrow(new UserSettingsConflictException(List.of("ui"), Map.of("ui", serverSection)));

        mockMvc.perform(put(SETTINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections":{"ui":{"value":{"fontSize":14},
                                 "clientUpdatedAt":"2026-11-14T07:05:00Z"}}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()))
                .andExpect(jsonPath("$.type").value("urn:leo:problem:concurrent-modification"))
                .andExpect(jsonPath("$.sections[0]").value("ui"))
                .andExpect(jsonPath("$.serverValue.ui.value.fontSize").value(16))
                .andExpect(jsonPath("$.serverValue.ui.clientUpdatedAt").value("2026-11-14T07:10:00Z"));
    }

    @Test
    void putSettings_whenSectionTooLarge_shouldReturn413() throws Exception {
        when(userAccountSettingsService.saveSettings(eq(USER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                        "设置分片 ui 超过 32KB 上限（当前 40000 字节）"));

        mockMvc.perform(put(SETTINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections":{"ui":{"value":{"blob":"x"},
                                 "clientUpdatedAt":"2026-11-14T07:05:00Z"}}}
                                """))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value(ErrorCode.PAYLOAD_TOO_LARGE.getCode()))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("32KB 上限")));
    }

    @Test
    void putSettings_whenUserMissing_shouldReturn404() throws Exception {
        when(userAccountSettingsService.saveSettings(eq(USER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        mockMvc.perform(put(SETTINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections":{"ui":{"value":{"fontSize":14},
                                 "clientUpdatedAt":"2026-11-14T07:05:00Z"}}}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void putSettings_whenServiceRejectsPayload_shouldReturn422() throws Exception {
        // standalone MockMvc 不装配 Bean Validation（与仓库其他控制器测试一致），
        // 因此这里锁的是「service 抛 VALIDATION_ERROR → 422 ProblemDetail」的映射，
        // 而「value 为 null 必须拒绝」由 UserAccountSettingsServiceTest 直接锁 service。
        when(userAccountSettingsService.saveSettings(eq(USER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "设置分片 ui 的提交内容不能为空"));

        mockMvc.perform(put(SETTINGS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sections":{"ui":{"value":null,
                                 "clientUpdatedAt":"2026-11-14T07:05:00Z"}}}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void putPreferences_alias_shouldDelegateToSameService() throws Exception {
        when(userAccountSettingsService.savePreferences(eq(USER_ID), any()))
                .thenReturn(preferences());

        mockMvc.perform(put(PREFERENCES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pages":{"sales-order":{"orderedKeys":["no"],
                                 "hiddenKeys":[],"columnSizes":{"no":120}}}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.sales-order.orderedKeys[0]").value("no"))
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    // ------------------------------------------------------------------
    // 契约：写映射必须要求幂等键（仓库统一门禁）
    // ------------------------------------------------------------------

    @Test
    void contract_writeEndpointStaysCoveredByIdempotencyRequired() throws Exception {
        assertThat(V2UserAccountController.class.isAnnotationPresent(IdempotencyRequired.class))
                .as("PUT /account/settings 依赖类级 @IdempotencyRequired 强制 X-Idempotency-Key")
                .isTrue();

        Method saveSettings = V2UserAccountController.class.getDeclaredMethod(
                "saveSettings", SecurityPrincipal.class, UserSettingsUpdateRequest.class);
        assertThat(saveSettings.getAnnotation(PutMapping.class).value())
                .containsExactly("/settings");
        assertThat(saveSettings.getParameters()[1].getAnnotation(RequestBody.class).required())
                .as("空请求体必须按「无变化」处理，而不是 400")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private void authenticate() {
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "tester", "", true, 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }

    private UserSettingsPayload payload() {
        return new UserSettingsPayload(
                UserSettingsPayload.CURRENT_VERSION,
                Map.of("ui", new UserSettingsSectionPayload(json("{\"fontSize\":14}"),
                        "2026-11-14T07:05:00Z", "2026-11-14T07:05:01Z")),
                Map.of("sales-order", new UserListColumnSettingsPayload(
                        List.of("no"), List.of(), Map.of("no", 120))));
    }

    private UserAccountPreferencesPayload preferences() {
        return new UserAccountPreferencesPayload(Map.of("sales-order",
                new UserListColumnSettingsPayload(List.of("no"), List.of(), Map.of("no", 120))));
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("测试数据不是合法 JSON", ex);
        }
    }

    private static ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }
}
