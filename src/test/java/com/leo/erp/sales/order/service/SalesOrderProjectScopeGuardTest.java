package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import com.leo.erp.master.api.ProjectProcurementScopeQuery.Enforcement;
import com.leo.erp.master.api.ProjectProcurementScopeQuery.ProjectProcurementScope;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 销售订单保存时的项目采购范围硬闸回归。
 *
 * <p>这里覆盖的是「服务端必须拦住」的那一半：前端导入提示可以被绕过（手工加行、直接调接口），
 * 因此 BLOCK 档必须在写路径上拒绝，且只有 BLOCK 档拒绝。</p>
 */
class SalesOrderProjectScopeGuardTest {

    private final ProjectProcurementScopeQuery scopeQuery = mock(ProjectProcurementScopeQuery.class);
    private final SalesOrderProjectScopeGuard guard = new SalesOrderProjectScopeGuard(scopeQuery);

    private SalesOrderItemRequest item(String brand, String material) {
        return new SalesOrderItemRequest(
                null, "M-1", brand, "螺纹钢", material, "Φ20", null, "吨",
                null, null, "主仓库", null, 10, "件",
                new BigDecimal("0.00100000"), 0, new BigDecimal("0.01000000"),
                new BigDecimal("4000.00"), new BigDecimal("40.00"));
    }

    private void stubScope(List<String> brands,
                           List<String> materials,
                           Enforcement brandEnforcement,
                           Enforcement materialEnforcement) {
        when(scopeQuery.findProcurementScopeById(9001L)).thenReturn(Optional.of(
                new ProjectProcurementScope(9001L, "项目甲", brands, materials,
                        brandEnforcement, materialEnforcement)));
    }

    @Test
    void block_brandViolation_isRejected() {
        stubScope(List.of("沙钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThatThrownBy(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB400"))))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("项目甲")
                .hasMessageContaining("宝钢")
                .hasMessageContaining("沙钢");
    }

    @Test
    void block_materialViolation_isRejectedWithAllowListInMessage() {
        stubScope(List.of("沙钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThatThrownBy(() -> guard.assertWithinProjectScope(9001L, List.of(item("沙钢", "HRB500"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HRB500")
                .hasMessageContaining("HRB400");
    }

    /** 提示必须保留例外通道, 否则等于把项目约定写成了没有出路的绝对禁止。 */
    @Test
    void rejectionMessage_keepsEqualGradeExceptionChannel() {
        stubScope(List.of("沙钢"), List.of(), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThatThrownBy(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB400"))))
                .hasMessageContaining("或同等档次");
    }

    @Test
    void warn_enforcement_doesNotBlock() {
        stubScope(List.of("沙钢"), List.of("HRB400"), Enforcement.WARN, Enforcement.WARN);

        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB500"))))
                .doesNotThrowAnyException();
    }

    @Test
    void off_enforcement_doesNotBlock() {
        stubScope(List.of("沙钢"), List.of("HRB400"), Enforcement.OFF, Enforcement.OFF);

        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB500"))))
                .doesNotThrowAnyException();
    }

    /** 只有品牌配了 BLOCK 时, 材质越界不应被拦（维度力度互相独立）。 */
    @Test
    void block_isPerDimension() {
        stubScope(List.of("沙钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.WARN);

        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of(item("沙钢", "HRB500"))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB500"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void matchingItems_pass() {
        stubScope(List.of("沙钢", "永钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThatCode(() -> guard.assertWithinProjectScope(9001L,
                List.of(item("沙钢", "HRB400"), item("永钢", "HRB400"))))
                .doesNotThrowAnyException();
    }

    @Test
    void blankScope_doesNotBlock() {
        stubScope(List.of(), List.of(), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB500"))))
                .doesNotThrowAnyException();
    }

    @Test
    void blankItemValues_areNotReported() {
        stubScope(List.of("沙钢"), List.of("HRB400"), Enforcement.BLOCK, Enforcement.BLOCK);

        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of(item("  ", ""))))
                .doesNotThrowAnyException();
    }

    @Test
    void absentProject_doesNotBlockAndSkipsLookup() {
        assertThatCode(() -> guard.assertWithinProjectScope(null, List.of(item("宝钢", "HRB500"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of()))
                .doesNotThrowAnyException();
        assertThatCode(() -> guard.assertWithinProjectScope(9001L, null))
                .doesNotThrowAnyException();
        verifyNoInteractions(scopeQuery);
    }

    @Test
    void unknownProject_doesNotBlock() {
        when(scopeQuery.findProcurementScopeById(9001L)).thenReturn(Optional.empty());

        assertThatCode(() -> guard.assertWithinProjectScope(9001L, List.of(item("宝钢", "HRB500"))))
                .doesNotThrowAnyException();
    }
}
