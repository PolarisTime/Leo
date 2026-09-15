package com.leo.erp.inventory.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.inventory.service.InventoryBackfillService;
import com.leo.erp.inventory.service.InventoryBalanceQueryService;
import com.leo.erp.inventory.service.InventoryTransactionQueryService;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2InventoryController 委托与雪花 ID 字符串化测试。
 * <p>
 * 校验 balances 的 materialId/warehouseId 参数换序不会传错、transactions 全参数透传、
 * backfills 返回 201、以及对外 JSON 中雪花 ID 必须为十进制字符串。
 */
@ExtendWith(MockitoExtension.class)
class V2InventoryControllerTest {

    @Mock
    private InventoryBalanceQueryService balanceQueryService;

    @Mock
    private InventoryTransactionQueryService transactionQueryService;

    @Mock
    private InventoryBackfillService backfillService;

    @InjectMocks
    private V2InventoryController controller;

    @Test
    void balances_shouldSwapMaterialAndWarehouseForServiceCall() {
        PageQuery query = mock(PageQuery.class);
        @SuppressWarnings("unchecked")
        PageResponse<InventoryBalanceResponse> expected = mock(PageResponse.class);
        when(balanceQueryService.page(query, "m001", 20L, 10L)).thenReturn(expected);

        assertThat(controller.balances(query, "m001", 10L, 20L)).isSameAs(expected);

        verify(balanceQueryService).page(query, "m001", 20L, 10L);
    }

    @Test
    void transactions_shouldPassAllFiltersInOrder() {
        PageQuery query = mock(PageQuery.class);
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        @SuppressWarnings("unchecked")
        PageResponse<InventoryTransactionResponse> expected = mock(PageResponse.class);
        when(transactionQueryService.page(query, "kw", 10L, 20L, "IN", start, end)).thenReturn(expected);

        assertThat(controller.transactions(query, "kw", 10L, 20L, "IN", start, end)).isSameAs(expected);

        verify(transactionQueryService).page(query, "kw", 10L, 20L, "IN", start, end);
    }

    @Test
    void createBackfill_shouldReturnCreated() {
        InventoryBackfillResponse response = new InventoryBackfillResponse(1, 2, 3, 4);
        when(backfillService.backfill()).thenReturn(response);

        ResponseEntity<InventoryBackfillResponse> result = controller.createBackfill();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
        verify(backfillService).backfill();
    }

    @Test
    void inventoryBalance_shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        long materialId = 9007199254740993L;
        long warehouseId = 9007199254740995L;
        InventoryBalanceResponse row = new InventoryBalanceResponse(
                materialId, "M001", "宝钢", "钢", "Φ20", "12m", "吨",
                warehouseId, "库房B", "B001", 12L, new BigDecimal("36000.00"), new BigDecimal("3000.00"));

        String json = mapper().writeValueAsString(row);

        assertThat(json).contains("\"materialId\":\"" + materialId + "\"");
        assertThat(json).contains("\"warehouseId\":\"" + warehouseId + "\"");
    }

    @Test
    void inventoryTransaction_shouldSerializeAllSnowflakeIdsAsStrings() throws Exception {
        long id = 9223372036854775807L;
        long materialId = 9007199254740993L;
        long warehouseId = 9007199254740995L;
        long sourceDocumentId = 9007199254740997L;
        long sourceItemId = 9007199254740999L;
        InventoryTransactionResponse row = new InventoryTransactionResponse(
                id, "T-1", "IN", materialId, "M001", "宝钢", "钢", "Φ20", "12m", "吨",
                warehouseId, "库房B", "B001", (short) 1, 12, "根",
                new BigDecimal("3000.00"), new BigDecimal("36000.00"),
                "PURCHASE_INBOUND", sourceDocumentId, "PI-1", sourceItemId,
                LocalDate.of(2026, 8, 1), LocalDateTime.of(2026, 8, 1, 10, 0));

        String json = mapper().writeValueAsString(row);

        assertThat(json).contains("\"id\":\"" + id + "\"");
        assertThat(json).contains("\"materialId\":\"" + materialId + "\"");
        assertThat(json).contains("\"warehouseId\":\"" + warehouseId + "\"");
        assertThat(json).contains("\"sourceDocumentId\":\"" + sourceDocumentId + "\"");
        assertThat(json).contains("\"sourceItemId\":\"" + sourceItemId + "\"");
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }
}
