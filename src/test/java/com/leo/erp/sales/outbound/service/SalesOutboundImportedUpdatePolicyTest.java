package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SalesOutboundImportedUpdatePolicy 边界测试：
 * 状态锁定、导入识别与导入出库的字段限制。
 */
class SalesOutboundImportedUpdatePolicyTest {

    private final SalesOutboundImportedUpdatePolicy policy = new SalesOutboundImportedUpdatePolicy();

    private SalesOutboundRequest request(String status, List<SalesOutboundItemRequest> items) {
        return new SalesOutboundRequest(
                "OB001", null, 99L, "新客户", 21L, "新项目", 9L, "新库房",
                LocalDate.of(2026, 8, 2), status, "备注", items, false);
    }

    private SalesOutboundItem importedItem(Long id, Integer lineNo) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setLineNo(lineNo);
        item.setSourceSalesOrderItemId(11L);
        item.setMaterialId(500L);
        item.setMaterialCode("M001");
        item.setWeightTon(new BigDecimal("12.500"));
        item.setQuantity(5);
        return item;
    }

    @Test
    void normalizeUpdateRequest_shouldRejectStatusChange() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);

        assertThatThrownBy(() -> policy.normalizeUpdateRequest(entity, request(StatusConstants.AUDITED, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能通过审核或反审核操作变更");
    }

    @Test
    void normalizeUpdateRequest_shouldAcceptNullStatus() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity, request(null, List.of()));

        assertThat(normalized.status()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void normalizeUpdateRequest_shouldAcceptWhitespaceStatus() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity, request("  ", List.of()));

        assertThat(normalized.status()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void normalizeUpdateRequest_shouldKeepEntityFieldsWhenNotImported() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setCustomerId(10L);
        entity.setProjectId(20L);
        entity.setWarehouseId(1L);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity, request(StatusConstants.DRAFT, List.of()));

        assertThat(normalized.outboundNo()).isEqualTo("OB001");
        assertThat(normalized.customerId()).isEqualTo(99L); // 未导入时允许修改
        assertThat(normalized.projectId()).isEqualTo(21L);
        assertThat(normalized.warehouseId()).isEqualTo(9L);
    }

    @Test
    void normalizeUpdateRequest_shouldMergeNullFieldsFromEntityWhenNotImported() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setCustomerId(10L);
        SalesOutboundRequest nullFieldRequest = new SalesOutboundRequest(
                "OB001", null, null, "新客户", null, "新项目", null, "新库房",
                LocalDate.of(2026, 8, 2), StatusConstants.DRAFT, "备注", List.of(), false);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity, nullFieldRequest);

        assertThat(normalized.customerId()).isEqualTo(10L); // 请求为 null 时回退实体值
        assertThat(normalized.projectId()).isNull(); // 请求与实体均为 null → 保持 null
    }

    @Test
    void normalizeUpdateRequest_shouldRestrictImportedBySalesOrderNo() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setSalesOrderNo("SO001");
        entity.setCustomerId(10L);
        SalesOutboundItem item = importedItem(100L, 1);
        entity.setItems(List.of(item));

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity, request(StatusConstants.DRAFT, List.of()));

        assertThat(normalized.salesOrderNo()).isEqualTo("SO001");
        assertThat(normalized.customerId()).isEqualTo(10L);
        assertThat(normalized.items()).hasSize(1);
        assertThat(normalized.items().get(0).id()).isEqualTo(100L);
        assertThat(normalized.items().get(0).sourceSalesOrderItemId()).isEqualTo(11L);
    }

    @Test
    void normalizeUpdateRequest_shouldRestrictImportedByItemSourceId() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setItems(List.of(importedItem(100L, 1)));

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity, request(StatusConstants.DRAFT, List.of()));

        assertThat(normalized.items()).hasSize(1);
        assertThat(normalized.items().get(0).sourceSalesOrderItemId()).isEqualTo(11L);
    }

    @Test
    void restrictItem_shouldFallbackToEntityWhenRequestItemMissing() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setSalesOrderNo("SO001");
        SalesOutboundItem item = importedItem(100L, 1);
        entity.setItems(List.of(item));
        SalesOutboundItemRequest reqItem = new SalesOutboundItemRequest(
                100L, null, 11L, 500L, "M001", null, null, null, null, null, null,
                null, null, null, 99, null, null, null, new BigDecimal("9.999"),
                null, null);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity,
                request(StatusConstants.DRAFT, List.of(reqItem)));

        SalesOutboundItemRequest restricted = normalized.items().get(0);
        assertThat(restricted.weightTon()).isEqualByComparingTo("9.999"); // 仅重量允许请求覆盖
        assertThat(restricted.quantity()).isEqualTo(5); // 其余字段锁定实体
        assertThat(restricted.materialCode()).isEqualTo("M001");
    }

    @Test
    void restrictItem_shouldUseEntityWeightWhenRequestWeightNull() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        SalesOutboundItem item = importedItem(100L, 1);
        entity.setItems(List.of(item));
        SalesOutboundItemRequest reqItem = new SalesOutboundItemRequest(
                100L, null, 11L, 500L, "M001", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity,
                request(StatusConstants.DRAFT, List.of(reqItem)));

        assertThat(normalized.items().get(0).weightTon()).isEqualByComparingTo("12.500");
    }

    @Test
    void restrictItems_shouldSortNullLineNoLastAndResolveDuplicateRequestIdsByFirst() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        entity.setStatus(StatusConstants.DRAFT);
        SalesOutboundItem first = importedItem(100L, 1);
        SalesOutboundItem second = importedItem(101L, null);
        entity.setItems(List.of(second, first));
        SalesOutboundItemRequest duplicateA = new SalesOutboundItemRequest(
                100L, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, new BigDecimal("1.000"), null, null);
        SalesOutboundItemRequest duplicateB = new SalesOutboundItemRequest(
                100L, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, new BigDecimal("2.000"), null, null);

        SalesOutboundRequest normalized = policy.normalizeUpdateRequest(entity,
                request(StatusConstants.DRAFT, List.of(duplicateA, duplicateB)));

        assertThat(normalized.items()).hasSize(2);
        assertThat(normalized.items().get(0).id()).isEqualTo(100L);
        assertThat(normalized.items().get(0).weightTon()).isEqualByComparingTo("1.000"); // 重复 ID 取第一个
        assertThat(normalized.items().get(1).id()).isEqualTo(101L); // null lineNo 排在最后
    }
}
