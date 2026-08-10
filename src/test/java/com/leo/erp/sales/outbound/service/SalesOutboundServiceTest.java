package com.leo.erp.sales.outbound.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderDownstreamMutationGuard;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundItemRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundRequest;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOutboundService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOutboundServiceTest {

    @Mock
    private SalesOutboundRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private SalesOutboundApplyService salesOutboundApplyService;

    @Mock
    private SalesOutboundResponseAssembler responseAssembler;

    @Mock
    private SalesOutboundSaveService saveService;

    @Mock
    private SalesOutboundPurchaseInboundGuard purchaseInboundGuard;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private SalesOutboundDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Mock
    private SalesOutboundCoverageValidator coverageValidator;

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOrderDownstreamMutationGuard salesOrderDownstreamMutationGuard;

    @InjectMocks
    private SalesOutboundService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // @InjectMocks 不自动调用包级 setter 注入，手动补齐
        service.setCoverageValidator(coverageValidator);
        service.setSalesOrderRepository(salesOrderRepository);
        service.setSalesOrderDownstreamMutationGuard(salesOrderDownstreamMutationGuard);
    }

    private SalesOutboundRequest request(String outboundNo, String salesOrderNo, String status) {
        return new SalesOutboundRequest(
                outboundNo, salesOrderNo, 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 8, 1), status, null, List.of());
    }

    private SalesOutbound entity(String status) {
        SalesOutbound entity = new SalesOutbound();
        entity.setId(5L);
        entity.setOutboundNo("OB001");
        entity.setStatus(status);
        return entity;
    }

    // ---------- 单号/导入校验 ----------

    @Test
    void validateCreate_shouldRejectDuplicateOutboundNo() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request("OB001", "SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("出库单号已存在");
    }

    @Test
    void validateCreate_shouldRejectNonDraftStatus() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("OB001", "SO001", StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void validateCreate_shouldRejectMissingSalesOrderNo() {
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("OB001", "", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须从已审核销售订单导入");
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(repository.existsByOutboundNoAndDeletedFlagFalse("OB999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, request("OB999", "SO001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- apply ----------

    @Test
    void apply_shouldSetFieldsAndValidateCoverage() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");

        service.apply(entity, request("OB001", "SO001", StatusConstants.DRAFT));

        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getProjectName()).isEqualTo("项目A");
        verify(coverageValidator).assertExactCoverage(any());
        verify(salesOutboundApplyService).applyItems(any(), any(), any());
    }

    @Test
    void apply_shouldGuardPurchaseInboundWhenAudited() {
        SalesOutbound entity = new SalesOutbound();
        entity.setOutboundNo("OB001");
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "采购未完成"))
                .when(purchaseInboundGuard).assertPurchaseInboundCompletedBeforeAudit(any());

        assertThatThrownBy(() -> service.apply(entity, request("OB001", "SO001", StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购未完成");
    }

    // ---------- 删除 ----------

    @Test
    void beforeDelete_shouldRejectAudited() {
        SalesOutbound entity = entity(StatusConstants.AUDITED);

        assertThatThrownBy(() -> service.beforeDelete(entity))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须先反审核");
    }

    @Test
    void beforeDelete_shouldAllowForDraft() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.beforeDelete(entity); // 不抛（items 空 → 无来源回滚）
    }

    @Test
    void afterDelete_shouldPublishEvent() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);

        service.afterDelete(entity);

        verify(businessOperationEventPublisher).publish(
                org.mockito.ArgumentMatchers.eq("SALES_OUTBOUND_DELETED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    // ---------- 状态守卫 ----------

    @Test
    void beforeStatusUpdate_shouldGuardReverseAudit() {
        SalesOutbound entity = entity(StatusConstants.AUDITED);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(downstreamMutationGuard).assertReverseAuditAllowed(any());

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.AUDITED, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void beforeStatusUpdate_shouldGuardPurchaseInboundOnAudit() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "采购未完成"))
                .when(purchaseInboundGuard).assertPurchaseInboundCompletedBeforeAudit(any());

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.DRAFT, StatusConstants.AUDITED))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 导入出库更新限制 ----------

    @Test
    void normalizeUpdateRequest_shouldRestrictImportedOutbound() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        entity.setSalesOrderNo("SO001");
        entity.setCustomerId(10L);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(100L);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(11L);
        item.setWeightTon(new BigDecimal("12.500"));
        item.setUnitPrice(new BigDecimal("4000"));
        entity.setItems(List.of(item));
        SalesOutboundItemRequest reqItem = new SalesOutboundItemRequest(
                100L, null, 11L, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, "库房A", "B001", 5, "件", new BigDecimal("1.250"), 100, null,
                new BigDecimal("4000"), null);

        SalesOutboundRequest normalized = service.normalizeUpdateRequest(entity,
                request("OB001", "SO001", StatusConstants.DRAFT));

        assertThat(normalized.items()).hasSize(1);
        assertThat(normalized.items().get(0).weightTon()).isEqualByComparingTo("12.500"); // 保留实体重量
        assertThat(normalized.customerId()).isEqualTo(10L); // 保留导入时的客户
    }

    // ---------- updateStatus 事件 ----------

    @Test
    void updateStatus_shouldPublishEventWhenChanged() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(java.util.Optional.of(entity));
        when(saveService.save(entity)).thenReturn(entity);
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(responseAssembler.toDetailResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(
                org.mockito.ArgumentMatchers.eq("SALES_OUTBOUND_STATUS_CHANGED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    // ---------- save 事件与回滚 ----------

    @Test
    void saveCreatedEntity_shouldPublishEvent() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        service.saveCreatedEntity(entity, request("OB001", "SO001", StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(
                org.mockito.ArgumentMatchers.eq("SALES_OUTBOUND_CREATED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void saveUpdatedEntity_shouldPublishEvent() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        when(saveService.save(entity)).thenReturn(entity);

        service.saveUpdatedEntity(entity, request("OB001", "SO001", StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(
                org.mockito.ArgumentMatchers.eq("SALES_OUTBOUND_UPDATED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(5L), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void beforeDelete_shouldRollbackSourceOrder() {
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setSourceSalesOrderItemId(11L);
        entity.setItems(List.of(item));
        // sourceSalesOrderIds 解析：salesOutboundApplyService 返回来源订单 id
        org.mockito.Mockito.doReturn(List.of(1L))
                .when(salesOutboundApplyService).sourceSalesOrderIds(entity);
        com.leo.erp.sales.order.domain.entity.SalesOrder order =
                new com.leo.erp.sales.order.domain.entity.SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO001");
        order.setStatus(StatusConstants.AUDITED);
        order.setItems(List.of());
        when(salesOrderRepository.findForUpdateByIdAndDeletedFlagFalse(1L))
                .thenReturn(java.util.Optional.of(order));
        when(salesOrderRepository.save(order)).thenReturn(order);

        service.beforeDelete(entity);

        assertThat(order.getStatus()).isEqualTo(StatusConstants.DRAFT); // 来源订单回退草稿
        verify(salesOrderRepository).save(order);
    }

    @Test
    void page_shouldMapEntities() {
        com.leo.erp.common.api.PageQuery query = mock(com.leo.erp.common.api.PageQuery.class);
        when(query.toPageable("id")).thenReturn(org.springframework.data.domain.PageRequest.of(0, 10));
        SalesOutbound entity = entity(StatusConstants.DRAFT);
        SalesOutboundResponse response = mock(SalesOutboundResponse.class);
        when(responseAssembler.toSummaryResponse(entity)).thenReturn(response);
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(entity)));

        org.springframework.data.domain.Page<SalesOutboundResponse> result =
                service.page(query, mock(com.leo.erp.common.api.PageFilter.class), null);

        assertThat(result.getContent()).containsExactly(response);
    }
}
