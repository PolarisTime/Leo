package com.leo.erp.common.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import com.leo.erp.system.operationlog.web.dto.OperationLogSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数值字段序列化契约：非 ID 的 long（分页计数、首页计数、库存数量、操作日志统计）
 * 必须是 JSON number；雪花 ID 仍由全局规则输出字符串。
 */
class NumericLongFieldsContractTest {

    record SampleItem(String id, String name) {
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }

    @Test
    void pageResponse_totalElementsShouldBeJsonNumber() throws Exception {
        PageResponse<SampleItem> page =
                new PageResponse<>(List.of(new SampleItem("42", "客户")), 413L, 21, 0, 20, true);

        String json = mapper().writeValueAsString(page);

        assertThat(json).contains("\"totalElements\":413");
        assertThat(json).doesNotContain("\"totalElements\":\"413\"");
        // 雪花 ID 仍必须是字符串，不能被数值化规则误伤。
        assertThat(json).contains("\"id\":\"42\"");
    }

    @Test
    void dashboardCountsShouldBeJsonNumbers() throws Exception {
        DashboardSummaryResponse response = new DashboardSummaryResponse(
                "leo", "公司", "管理员", "admin",
                3L, null, null, 413L, 42L, 15L);

        String json = mapper().writeValueAsString(response);

        assertThat(json).contains("\"activeSessionCount\":3");
        assertThat(json).contains("\"materialCount\":413");
        assertThat(json).contains("\"supplierCount\":42");
        assertThat(json).contains("\"customerCount\":15");
        assertThat(json).doesNotContain("\"materialCount\":\"413\"");
    }

    @Test
    void inventoryQuantitiesShouldBeJsonNumbers() throws Exception {
        InventoryBalanceResponse balance = new InventoryBalanceResponse(
                null, "M001", "萍钢", "HRB400E", "10", "-", "吨",
                null, "钟管库", "B1", 7L, null, null);

        String json = mapper().writeValueAsString(balance);

        assertThat(json).contains("\"quantity\":7");
        assertThat(json).doesNotContain("\"quantity\":\"7\"");
    }

    @Test
    void operationLogCountsShouldBeJsonNumbers() throws Exception {
        OperationLogSummaryResponse response = new OperationLogSummaryResponse(
                10L, 9L, 1L, 12L, List.of(), List.of());

        String json = mapper().writeValueAsString(response);

        assertThat(json).contains("\"total\":10");
        assertThat(json).contains("\"successCount\":9");
        assertThat(json).contains("\"failedCount\":1");
        assertThat(json).contains("\"avgDurationMs\":12");
        assertThat(json).doesNotContain("\"total\":\"10\"");
    }
}
