package com.leo.erp.inventory.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.PageSortFieldCatalog;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.inventory.service.InventoryBackfillService;
import com.leo.erp.inventory.service.InventoryBalanceMaintenanceService;
import com.leo.erp.inventory.service.InventoryBalanceQueryService;
import com.leo.erp.inventory.service.InventoryTransactionQueryService;
import com.leo.erp.inventory.web.dto.InventoryBackfillResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceMismatchResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceRebuildResponse;
import com.leo.erp.inventory.web.dto.InventoryBalanceResponse;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V2InventoryController 委托、状态码、权限/幂等契约与雪花 ID 字符串化测试。
 * <p>
 * 校验 balances 的 materialId/warehouseId 参数换序不会传错、transactions 全参数透传、
 * backfills 与 balance-rebuilds 作为同步命令返回 200 且不发 Location、balance-mismatches 分页白名单，
 * 以及对外 JSON 中雪花 ID 必须为十进制字符串。
 */
@ExtendWith(MockitoExtension.class)
class V2InventoryControllerTest {

    @Mock
    private InventoryBalanceQueryService balanceQueryService;

    @Mock
    private InventoryTransactionQueryService transactionQueryService;

    @Mock
    private InventoryBackfillService backfillService;

    @Mock
    private InventoryBalanceMaintenanceService maintenanceService;

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
    void createBackfill_shouldReturnOk() {
        InventoryBackfillResponse response = new InventoryBackfillResponse(1, 2, 3, 4);
        when(backfillService.backfill()).thenReturn(response);

        ResponseEntity<InventoryBackfillResponse> result = controller.createBackfill();

        // 期初回填是同步命令, 不是创建可回读资源: 200 + 统计, 不发 Location
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getLocation()).isNull();
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

    @Test
    void createBalanceRebuild_shouldReturnOkWithRebuiltRows() {
        when(maintenanceService.rebuild()).thenReturn(7);

        ResponseEntity<InventoryBalanceRebuildResponse> result = controller.createBalanceRebuild();

        // 余额重建是同步命令, 不是创建可回读资源: 200 + 统计, 不发 Location
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getLocation()).isNull();
        assertThat(result.getBody()).isEqualTo(new InventoryBalanceRebuildResponse(7));
        verify(maintenanceService).rebuild();
    }

    @Test
    void balanceMismatches_shouldDelegatePagedQuery() {
        PageQuery query = PageQuery.of(1, 50, "ledgerQuantity", "desc",
                PageSortFieldCatalog.fields("inventory-balance-mismatch"));
        @SuppressWarnings("unchecked")
        PageResponse<InventoryBalanceMismatchResponse> expected = mock(PageResponse.class);
        when(maintenanceService.reconcile(query)).thenReturn(expected);

        assertThat(controller.balanceMismatches(query)).isSameAs(expected);

        verify(maintenanceService).reconcile(query);
    }

    @Test
    void balanceMismatch_shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        long materialId = 9007199254740993L;
        long warehouseId = 9007199254740995L;
        InventoryBalanceMismatchResponse row = new InventoryBalanceMismatchResponse(
                materialId, warehouseId, 5L, 4L, new BigDecimal("20000.00"), new BigDecimal("16000.00"));

        String json = mapper().writeValueAsString(row);

        assertThat(json).contains("\"materialId\":\"" + materialId + "\"");
        assertThat(json).contains("\"warehouseId\":\"" + warehouseId + "\"");
    }

    @Test
    void balanceMismatch_shouldSerializeNoWarehouseSentinelAsString() throws Exception {
        InventoryBalanceMismatchResponse row = new InventoryBalanceMismatchResponse(
                9007199254740993L, 0L, 5L, 5L, BigDecimal.ZERO, BigDecimal.ZERO);

        String json = mapper().writeValueAsString(row);

        assertThat(json).contains("\"warehouseId\":\"0\"");
    }

    @Test
    void balanceRebuilds_shouldRequireBackfillPermissionAndIdempotencyKey() throws Exception {
        Method rebuild = V2InventoryController.class.getMethod("createBalanceRebuild");
        Method mismatches = V2InventoryController.class.getMethod("balanceMismatches", PageQuery.class);

        assertThat(V2InventoryController.class.isAnnotationPresent(IdempotencyRequired.class)).isTrue();
        assertThat(rebuild.getAnnotation(RequirePermission.class).value())
                .containsExactly(PermissionCodes.INVENTORY_BACKFILL);
        assertThat(mismatches.getAnnotation(RequirePermission.class).value())
                .containsExactly(PermissionCodes.INVENTORY_READ);
        assertThat(rebuild.getAnnotation(Operation.class)).isNotNull();
        assertThat(mismatches.getAnnotation(Operation.class)).isNotNull();
    }

    @Test
    void balanceMismatches_shouldRejectSortFieldOutsideWhitelist() {
        Set<String> allowed = PageSortFieldCatalog.fields("inventory-balance-mismatch");
        assertThat(allowed).contains("materialId", "warehouseId", "ledgerQuantity", "snapshotQuantity");

        assertThatThrownBy(() -> PageQuery.of(0, 20, "quantity", "asc", allowed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sortBy 不支持当前列表");
        assertThatThrownBy(() -> PageQuery.of(0, 201, null, "asc", allowed))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("size 必须在1到200之间");
    }

    private ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        return builder.build();
    }
}
