package com.leo.erp.market.supplierprice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.market.supplierprice.service.SupplierPriceCollabSessionService;
import com.leo.erp.market.supplierprice.web.dto.CollabSessionResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * {@code POST /supplier-price-lists/{listId}/collab-sessions} 的 HTTP 契约。
 *
 * <p>锁 201 响应体字段（room/url/token/writable/color/expiresAt）、404 语义、
 * 只读票据透传，以及「路径雪花 ID 校验 + 读权限 + 幂等键」三条门禁注解仍在 ——
 * 这些注解缺席时不会编译失败，只会静默失去保护，所以用反射固化。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2SupplierPriceCollabSessionControllerTest {

    private static final long LIST_ID = 900000000000000201L;
    private static final long USER_ID = 900000000000000202L;
    private static final String PATH = "/v2.0/supplier-price-lists/" + LIST_ID + "/collab-sessions";
    private static final String ROOM = "supplier-price-list:" + LIST_ID;

    @Mock
    private SupplierPriceCollabSessionService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ApiProblemFactory problemFactory = new ApiProblemFactory("Asia/Shanghai");
        V2SupplierPriceCollabSessionController controller =
                new V2SupplierPriceCollabSessionController(service);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(problemFactory))
                .build();
        SecurityPrincipal principal = new SecurityPrincipal(USER_ID, "zhangsan", "", true, 0L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_shouldReturn201WithSessionContract() throws Exception {
        when(service.createSession(eq(LIST_ID), any())).thenReturn(session(true));

        mockMvc.perform(post(PATH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.room").value(ROOM))
                .andExpect(jsonPath("$.url").value("ws://127.0.0.1:11212/collab"))
                .andExpect(jsonPath("$.token").value("header.payload.signature"))
                .andExpect(jsonPath("$.writable").value(true))
                .andExpect(jsonPath("$.color").value("#1677ff"))
                .andExpect(jsonPath("$.expiresAt").value("2026-11-14T09:30:00Z"));

        verify(service).createSession(eq(LIST_ID), any());
    }

    @Test
    void create_whenReadOnlyUser_shouldReturnWritableFalseButStill201() throws Exception {
        when(service.createSession(eq(LIST_ID), any())).thenReturn(session(false));

        mockMvc.perform(post(PATH))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.writable").value(false))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void create_whenListMissing_shouldReturn404() throws Exception {
        when(service.createSession(eq(LIST_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "供应商价格表不存在"));

        mockMvc.perform(post(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void contract_keepsReadPermissionSnowflakePathAndIdempotency() throws Exception {
        assertThat(V2SupplierPriceCollabSessionController.class
                .isAnnotationPresent(IdempotencyRequired.class))
                .as("写接口必须类级标注 @IdempotencyRequired（仓库统一门禁）")
                .isTrue();
        assertThat(V2SupplierPriceCollabSessionController.class
                .isAnnotationPresent(Validated.class))
                .as("路径参数校验依赖 @Validated")
                .isTrue();

        Method create = V2SupplierPriceCollabSessionController.class.getDeclaredMethod(
                "create", Long.class, SecurityPrincipal.class);
        assertThat(create.getAnnotation(PostMapping.class).value())
                .containsExactly("/{listId}/collab-sessions");
        assertThat(create.getAnnotation(RequirePermission.class).value())
                .as("取票只要求读权限；可写与否由票据里的 writable 表达")
                .containsExactly(PermissionCodes.SUPPLIER_PRICE_LISTS_READ);
        assertThat(create.getParameters()[0].getAnnotation(SnowflakeId.class))
                .as("路径上的 listId 必须是雪花 ID（正数、十进制字符串传入）")
                .isNotNull();
    }

    private CollabSessionResponse session(boolean writable) {
        return new CollabSessionResponse(ROOM, "ws://127.0.0.1:11212/collab",
                "header.payload.signature", writable, "#1677ff", "2026-11-14T09:30:00Z");
    }
}
