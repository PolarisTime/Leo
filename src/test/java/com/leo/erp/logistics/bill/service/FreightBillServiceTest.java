package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.logistics.bill.domain.enums.FreightSettlementMode;
import com.leo.erp.logistics.bill.mapper.FreightBillMapper;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillResponse;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.VehicleQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.operationlog.event.BusinessOperationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FreightBillService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillServiceTest {

    @Mock
    private FreightBillRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private FreightBillMapper mapper;

    @Mock
    private FreightBillApplyService applyService;

    @Mock
    private CarrierQuery carrierQuery;

    @Mock
    private CompanySettingService companySettingService;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    @Mock
    private FreightBillDownstreamMutationGuard downstreamMutationGuard;

    @Mock
    private VehicleQuery vehicleQuery;

    @Mock
    private BusinessOperationEventPublisher businessOperationEventPublisher;

    @Mock
    private DocumentChargeItemService documentChargeItemService;

    @Mock
    private com.leo.erp.logistics.bill.repository.FreightBillItemRepository freightBillItemRepository;

    @InjectMocks
    private FreightBillService service;

    private FreightBillRequest request(String billNo, Long carrierId, String status) {
        return request(billNo, carrierId, status, null, null);
    }

    /** 带结算口径的请求: settlementMode/fixedFreightAmount 为 null 即"请求里没有该字段"。 */
    private FreightBillRequest request(String billNo, Long carrierId, String status,
                                       FreightSettlementMode settlementMode, BigDecimal fixedFreightAmount) {
        return new FreightBillRequest(
                billNo, carrierId, "C001", "承运商A", null, null, null, null,
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), settlementMode, fixedFreightAmount,
                status, null, List.of(), List.of(), false);
    }

    private CarrierQuery.CarrierSnapshot carrier(Long id, Long defaultSettlementId) {
        return new CarrierQuery.CarrierSnapshot(id, "C001", "承运商A", defaultSettlementId);
    }

    // ---------- 查询 ----------

    @Test
    void page_shouldMapEntities() {
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));
        FreightBill entity = new FreightBill();
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(mapper.toResponse(entity)).thenReturn(response);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        Page<FreightBillResponse> result = service.page(query, mock(PageFilter.class), null);

        assertThat(result.getContent()).containsExactly(response);
    }

    // ---------- 单号唯一性 ----------

    @Test
    void validateCreate_shouldRejectDuplicateBillNo() {
        when(repository.existsByBillNoAndDeletedFlagFalse("FB001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request("FB001", 1L, StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流单号已存在");
    }

    @Test
    void validateCreate_shouldRejectNonDraftStatus() {
        when(repository.existsByBillNoAndDeletedFlagFalse("FB001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("FB001", 1L, StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        FreightBill entity = new FreightBill();
        entity.setBillNo("FB001");
        when(repository.existsByBillNoAndDeletedFlagFalse("FB999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, request("FB999", 1L, StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- apply ----------

    @Test
    void apply_shouldResolveCarrierVehicleAndSettlement() {
        FreightBill entity = new FreightBill();
        FreightBillRequest request = request("FB001", 1L, StatusConstants.DRAFT);
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, 30L)));
        CompanySetting company = new CompanySetting();
        company.setId(30L);
        company.setCompanyName("结算公司A");
        when(companySettingService.requireActiveSettlementCompany(30L)).thenReturn(company);

        service.apply(entity, request);

        assertThat(entity.getCarrierId()).isEqualTo(1L);
        assertThat(entity.getCarrierCode()).isEqualTo("C001");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算公司A");
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
        verify(applyService).applyItems(any(), any(), any());
    }

    @Test
    void apply_shouldRejectCarrierChange() {
        FreightBill entity = new FreightBill();
        entity.setCarrierId(99L); // 已有物流商
        FreightBillRequest request = request("FB001", 1L, StatusConstants.DRAFT);
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, 30L)));

        assertThatThrownBy(() -> service.apply(entity, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能更换物流商");
    }

    @Test
    void apply_shouldRejectWhenCarrierHasNoDefaultSettlement() {
        FreightBill entity = new FreightBill();
        FreightBillRequest request = request("FB001", 1L, StatusConstants.DRAFT);
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, null)));

        assertThatThrownBy(() -> service.apply(entity, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置默认结算主体");
    }

    @Test
    void apply_shouldRejectVehicleBelongingToOtherCarrier() {
        FreightBill entity = new FreightBill();
        FreightBillRequest request = new FreightBillRequest(
                "FB001", 1L, "C001", "承运商A", null, null, 5L, "沪A123",
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), null, null,
                StatusConstants.DRAFT, null, List.of(), List.of(), false);
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, 30L)));
        // 车辆 carrierId=99 与物流商 1 不一致
        when(vehicleQuery.findById(5L)).thenReturn(Optional.of(new VehicleQuery.VehicleSnapshot(5L, 99L, "沪A123")));

        assertThatThrownBy(() -> service.apply(entity, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("车辆不属于所选物流商");
    }

    @Test
    void apply_shouldRejectVehicleIdPlateMismatch() {
        FreightBill entity = new FreightBill();
        FreightBillRequest request = new FreightBillRequest(
                "FB001", 1L, "C001", "承运商A", null, null, 5L, "沪A999",
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), null, null,
                StatusConstants.DRAFT, null, List.of(), List.of(), false);
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, 30L)));
        when(vehicleQuery.findById(5L)).thenReturn(Optional.of(new VehicleQuery.VehicleSnapshot(5L, 1L, "沪A123")));

        assertThatThrownBy(() -> service.apply(entity, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("车辆ID与车牌号不一致");
    }

    // ---------- 结算方式: 按吨位 / 固定运费 ----------

    private void stubCarrier() {
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, 30L)));
    }

    private void stubSettlementCompany() {
        CompanySetting company = new CompanySetting();
        company.setId(30L);
        company.setCompanyName("结算公司A");
        when(companySettingService.requireActiveSettlementCompany(30L)).thenReturn(company);
    }

    /** 新建时未携带结算方式: 默认按吨位结算, 固定运费金额为空。 */
    @Test
    void apply_withoutSettlementMode_defaultsToByTonnage() {
        FreightBill entity = new FreightBill();
        stubCarrier();
        stubSettlementCompany();

        service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT));

        assertThat(entity.getSettlementMode()).isEqualTo(FreightSettlementMode.BY_TONNAGE);
        assertThat(entity.getFixedFreightAmount()).isNull();
    }

    /** 显式切回按吨位: 库里遗留的固定运费金额必须被清空, 不残留无意义数值。 */
    @Test
    void apply_byTonnageMode_clearsStaleFixedAmount() {
        FreightBill entity = new FreightBill();
        entity.setSettlementMode(FreightSettlementMode.FIXED);
        entity.setFixedFreightAmount(new BigDecimal("800.00"));
        stubCarrier();
        stubSettlementCompany();

        service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT,
                FreightSettlementMode.BY_TONNAGE, null));

        assertThat(entity.getSettlementMode()).isEqualTo(FreightSettlementMode.BY_TONNAGE);
        assertThat(entity.getFixedFreightAmount()).isNull();
    }

    /** 互斥: 按吨位结算时显式给固定运费金额 → 422, 消息可读。 */
    @Test
    void apply_byTonnageMode_rejectsExplicitFixedAmount() {
        FreightBill entity = new FreightBill();
        stubCarrier();

        assertThatThrownBy(() -> service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT,
                FreightSettlementMode.BY_TONNAGE, new BigDecimal("800.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("按吨位结算时不需要填写固定运费金额")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
        verify(applyService, never()).applyItems(any(), any(), any());
    }

    /** FIXED 但没给固定运费金额(新建或原值为空) → 422。 */
    @Test
    void apply_fixedMode_withoutAmount_isRejected() {
        FreightBill entity = new FreightBill();
        stubCarrier();

        assertThatThrownBy(() -> service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT,
                FreightSettlementMode.FIXED, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("固定运费结算必须填写大于0的固定运费金额")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    /** FIXED 金额为 0 / 负数 → 422(服务层兜底, 直接调用绕过 @DecimalMin 时也要挡住)。 */
    @Test
    void apply_fixedMode_withNonPositiveAmount_isRejected() {
        FreightBill entity = new FreightBill();
        stubCarrier();

        assertThatThrownBy(() -> service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT,
                FreightSettlementMode.FIXED, BigDecimal.ZERO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("固定运费结算必须填写大于0的固定运费金额");
        assertThatThrownBy(() -> service.apply(new FreightBill(), request("FB001", 1L, StatusConstants.DRAFT,
                FreightSettlementMode.FIXED, new BigDecimal("-1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("固定运费结算必须填写大于0的固定运费金额");
    }

    /** FIXED + 正金额: 落库前按 2 位小数归一。 */
    @Test
    void apply_fixedMode_acceptsPositiveAmountAndScalesToTwoDecimals() {
        FreightBill entity = new FreightBill();
        stubCarrier();
        stubSettlementCompany();

        service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT,
                FreightSettlementMode.FIXED, new BigDecimal("1500.5")));

        assertThat(entity.getSettlementMode()).isEqualTo(FreightSettlementMode.FIXED);
        assertThat(entity.getFixedFreightAmount()).isEqualByComparingTo("1500.50");
    }

    /**
     * 回归: 编辑既有固定运费单据时请求未携带结算方式与金额(null = 请求里没有该字段),
     * 必须保持原结算口径, 不得静默改回按吨位结算。
     */
    @Test
    void apply_omittedModeAndAmount_onFixedBill_keepsExistingTerms() {
        FreightBill entity = new FreightBill();
        entity.setSettlementMode(FreightSettlementMode.FIXED);
        entity.setFixedFreightAmount(new BigDecimal("800.00"));
        stubCarrier();
        stubSettlementCompany();

        service.apply(entity, request("FB001", 1L, StatusConstants.DRAFT));

        assertThat(entity.getSettlementMode()).isEqualTo(FreightSettlementMode.FIXED);
        assertThat(entity.getFixedFreightAmount()).isEqualByComparingTo("800.00");
    }

    // ---------- 删除/状态守卫与事件 ----------

    @Test
    void beforeDelete_shouldGuard() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已使用"))
                .when(downstreamMutationGuard).assertDeleteAllowed(any());

        assertThatThrownBy(() -> service.beforeDelete(entity))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void afterDelete_shouldDeactivateSourcesAndPublish() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        FreightBillSourceOrder source = new FreightBillSourceOrder();
        source.setActiveFlag(true);
        entity.getSourceOrders().add(source);

        service.afterDelete(entity);

        assertThat(source.isActiveFlag()).isFalse();
        // 软删除后解绑本单明细的来源销售订单明细引用(与 V204 同口径)
        verify(freightBillItemRepository).detachSourceReferencesOfDeletedBill(5L);
        verify(businessOperationEventPublisher).publish(eq("FREIGHT_BILL_DELETED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void beforeStatusUpdate_shouldGuardReverseAudit() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        doThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "已结算"))
                .when(downstreamMutationGuard).assertReverseAuditAllowed(any());

        assertThatThrownBy(() -> service.beforeStatusUpdate(
                entity, StatusConstants.AUDITED, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateStatus_shouldPublishEventWhenStatusChanged() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(mapper.toResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        verify(businessOperationEventPublisher).publish(eq("FREIGHT_BILL_STATUS_CHANGED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    // ---------- save 事件与 normalize ----------

    @Test
    void saveCreatedEntity_shouldPublishEvent() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        when(repository.save(entity)).thenReturn(entity);

        service.saveCreatedEntity(entity, request("FB001", 1L, StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(eq("FREIGHT_BILL_CREATED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void saveUpdatedEntity_shouldPublishEvent() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        when(repository.save(entity)).thenReturn(entity);

        service.saveUpdatedEntity(entity, request("FB001", 1L, StatusConstants.DRAFT));

        verify(businessOperationEventPublisher).publish(eq("FREIGHT_BILL_UPDATED"), anyString(), anyString(),
                anyString(), anyString(), eq(5L), anyString(), anyString());
    }

    @Test
    void normalizeUpdateRequest_shouldRejectStatusChange() {
        FreightBill entity = new FreightBill();
        entity.setBillNo("FB001");
        entity.setStatus(StatusConstants.AUDITED);
        FreightBillRequest request = request("FB001", 1L, StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.normalizeUpdateRequest(entity, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能通过审核或反审核操作变更");
    }

    // ---------- 显式状态断言序列（原基类内联） ----------

    @Test
    void update_shouldRejectEditInProtectedStatus() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        entity.setStatus(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(5L, request("FB001", 1L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");

        verify(repository, never()).save(any());
    }

    @Test
    void update_shouldRejectRequestedStatusChange() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        entity.setStatus(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(5L, request("FB001", 1L, StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能通过审核或反审核操作变更");

        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(sourceAllocationLockService, never()).lockDocumentSources(any(), any(), any(), any());
        verify(repository, never()).save(any());
        verify(businessOperationEventPublisher, never()).publish(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_shouldRejectFinalStatusOutsideTransitionTable() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setStatus(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「已完成」");

        verify(sourceAllocationLockService, never()).lockDocumentSources(any(), any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_shouldShortCircuitWhenStatusUnchanged() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        entity.setStatus(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(mapper.toResponse(entity)).thenReturn(response);

        FreightBillResponse result = service.updateStatus(5L, StatusConstants.AUDITED);

        assertThat(result.status()).isEqualTo(StatusConstants.AUDITED);
        verify(repository, never()).save(any());
        verify(businessOperationEventPublisher, never()).publish(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_shouldLockGuardSavePublishInOrder() {
        FreightBill entity = new FreightBill();
        entity.setId(5L);
        entity.setBillNo("FB001");
        entity.setStatus(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        FreightBillResponse response = mock(FreightBillResponse.class);
        when(response.status()).thenReturn(StatusConstants.DRAFT);
        when(mapper.toResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.DRAFT);

        InOrder inOrder = inOrder(sourceAllocationLockService, downstreamMutationGuard, repository,
                businessOperationEventPublisher);
        inOrder.verify(sourceAllocationLockService).lockDocumentSources(any(), any(), any(), any());
        inOrder.verify(downstreamMutationGuard).assertReverseAuditAllowed(entity);
        inOrder.verify(repository).save(entity);
        inOrder.verify(businessOperationEventPublisher).publish(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
