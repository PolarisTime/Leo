package com.leo.erp.finance.ledgeradjustment.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.finance.ledgeradjustment.service.LedgerAdjustmentService;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 台账调整端点契约：写端点（POST / PUT / PATCH /{id}/status / DELETE）已随整改下线，仅保留只读 GET。
 *
 * <p>纯反射 + standalone MockMvc，不启动 Spring 上下文：台账余额不得通过独立调整单直接改写，
 * 因此除了「权限码已删」，还要钉住「控制器不再声明任何写 mapping，写动词请求落到 404」。</p>
 */
class V2LedgerAdjustmentControllerContractTest {

    private static final Class<V2LedgerAdjustmentController> CONTROLLER = V2LedgerAdjustmentController.class;
    private static final String BASE_PATH = ApiVersion.V2_PREFIX + "/ledger-adjustments";

    private static final List<String> RETIRED_PERMISSION_CONSTANTS = List.of(
            "LEDGER_ADJUSTMENTS_CREATE",
            "LEDGER_ADJUSTMENTS_UPDATE",
            "LEDGER_ADJUSTMENTS_DELETE",
            "LEDGER_ADJUSTMENTS_AUDIT");

    @Test
    void controller_shouldNotDeclareAnyWriteMapping() {
        List<String> writeMappings = new ArrayList<>();
        for (Method method : CONTROLLER.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PostMapping.class)) {
                writeMappings.add("POST " + method.getName());
            }
            if (method.isAnnotationPresent(PutMapping.class)) {
                writeMappings.add("PUT " + method.getName());
            }
            if (method.isAnnotationPresent(PatchMapping.class)) {
                writeMappings.add("PATCH " + method.getName());
            }
            if (method.isAnnotationPresent(DeleteMapping.class)) {
                writeMappings.add("DELETE " + method.getName());
            }
        }

        assertThat(writeMappings)
                .as("台账调整写端点已下线，控制器不得再声明 POST/PUT/PATCH/DELETE mapping")
                .isEmpty();
    }

    @Test
    void controller_shouldExposeOnlyListAndDetailReadMappings() {
        Map<String, String> getMappings = new LinkedHashMap<>();
        for (Method method : CONTROLLER.getDeclaredMethods()) {
            GetMapping mapping = method.getAnnotation(GetMapping.class);
            if (mapping != null) {
                getMappings.put(method.getName(), firstPath(mapping.value(), mapping.path()));
            }
        }

        assertThat(getMappings).containsOnlyKeys("page", "detail");
        assertThat(getMappings.get("page")).isEmpty();
        assertThat(getMappings.get("detail")).isEqualTo("/{id}");
    }

    @Test
    void controllerBasePath_shouldBeVersionedKebabCaseResource() {
        RequestMapping mapping = CONTROLLER.getAnnotation(RequestMapping.class);

        assertThat(mapping).as("控制器必须声明类级 @RequestMapping").isNotNull();
        assertThat(mapping.value()).containsExactly(BASE_PATH);
        assertThat(BASE_PATH).isEqualTo("/v2.0/ledger-adjustments");
    }

    @Test
    void readEndpoints_shouldRequireLedgerAdjustmentsRead() {
        List<Method> readMethods = Arrays.stream(CONTROLLER.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GetMapping.class))
                .toList();

        assertThat(readMethods).hasSize(2);
        assertThat(readMethods).allSatisfy(method -> {
            RequirePermission permission = method.getAnnotation(RequirePermission.class);
            assertThat(permission).as(method.getName() + " 必须声明权限").isNotNull();
            assertThat(permission.value()).containsExactly(PermissionCodes.LEDGER_ADJUSTMENTS_READ);
        });
    }

    @Test
    void writePermissionCodes_shouldBeRetiredFromCatalog() {
        assertThat(PermissionCodes.all())
                .contains(PermissionCodes.LEDGER_ADJUSTMENTS_READ)
                .doesNotContain("ledger-adjustments:create", "ledger-adjustments:update",
                        "ledger-adjustments:delete", "ledger-adjustments:audit");

        for (String constantName : RETIRED_PERMISSION_CONSTANTS) {
            assertThatThrownBy(() -> PermissionCodes.class.getField(constantName))
                    .as(constantName + " 常量应已删除，写权限码不得被重新加回")
                    .isInstanceOf(NoSuchFieldException.class);
        }
    }

    @Test
    void standaloneDispatcher_shouldRejectRetiredWriteVerbs() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new V2LedgerAdjustmentController(mock(LedgerAdjustmentService.class)))
                .build();

        // 与 GET 同路径的写动词：Spring 判定「路径匹配、方法不允许」→ 405 Method Not Allowed
        mockMvc.perform(post(BASE_PATH)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(BASE_PATH + "/1")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(BASE_PATH + "/1")).andExpect(status().isMethodNotAllowed());
        // 已下线的状态子资源（原 PATCH /{id}/status）没有对应 GET，路径本身不存在 → 404
        mockMvc.perform(patch(BASE_PATH + "/1/status")).andExpect(status().isNotFound());
    }

    private String firstPath(String[] value, String[] path) {
        if (value.length > 0) {
            return value[0];
        }
        return path.length > 0 ? path[0] : "";
    }
}
