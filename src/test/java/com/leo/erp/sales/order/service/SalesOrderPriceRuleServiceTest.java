package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.api.ProjectPriceRuleQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderPriceRuleServiceTest {

    @Mock
    private ProjectPriceRuleQuery priceRuleQuery;

    private SalesOrderPriceRuleService service() {
        return new SalesOrderPriceRuleService(priceRuleQuery);
    }

    private SalesOrder order(Long projectId) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setProjectId(projectId);
        return order;
    }

    @Test
    void apply_snapshotsRuleAndRemembersLastUsed() {
        SalesOrder order = order(9L);
        when(priceRuleQuery.findActiveById(50L)).thenReturn(Optional.of(
                new ProjectPriceRuleQuery.PriceRuleSnapshot(50L, 9L, "含税价", "ADD",
                        new BigDecimal("30.00"), "备注")));

        service().applyPriceRule(order, 50L);

        assertThat(order.getPriceRuleId()).isEqualTo(50L);
        assertThat(order.getPriceRuleName()).isEqualTo("含税价");
        assertThat(order.getPriceFloatMode()).isEqualTo("ADD");
        assertThat(order.getPriceFloatValue()).isEqualByComparingTo("30.00");
        verify(priceRuleQuery).rememberLastUsedRuleId(9L, 50L);
    }

    @Test
    void apply_rejectsRuleFromOtherProject() {
        SalesOrder order = order(9L);
        when(priceRuleQuery.findActiveById(50L)).thenReturn(Optional.of(
                new ProjectPriceRuleQuery.PriceRuleSnapshot(50L, 999L, "别项目", "ADD",
                        BigDecimal.ONE, null)));

        assertThatThrownBy(() -> service().applyPriceRule(order, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该单据项目");
        verify(priceRuleQuery, never()).rememberLastUsedRuleId(anyLong(), anyLong());
    }

    @Test
    void apply_rejectsMissingRule() {
        when(priceRuleQuery.findActiveById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().applyPriceRule(order(9L), 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格规定不存在或已失效");
    }

    @Test
    void apply_nullClearsSnapshot() {
        SalesOrder order = order(9L);
        order.setPriceRuleId(50L);
        order.setPriceRuleName("旧");
        order.setPriceFloatMode("ADD");
        order.setPriceFloatValue(BigDecimal.TEN);

        service().applyPriceRule(order, null);

        assertThat(order.getPriceRuleId()).isNull();
        assertThat(order.getPriceRuleName()).isNull();
        assertThat(order.getPriceFloatMode()).isNull();
        assertThat(order.getPriceFloatValue()).isNull();
        verify(priceRuleQuery, never()).rememberLastUsedRuleId(anyLong(), anyLong());
    }

    private static Long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
