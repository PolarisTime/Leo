package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
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
import static org.mockito.Mockito.mock;
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

    @InjectMocks
    private FreightBillService service;

    private FreightBillRequest request(String billNo, Long carrierId, String status) {
        return new FreightBillRequest(
                billNo, carrierId, "C001", "承运商A", null, null, null, null,
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), status, null, List.of());
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
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), StatusConstants.DRAFT, null, List.of());
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
                LocalDate.of(2026, 8, 1), new BigDecimal("100"), StatusConstants.DRAFT, null, List.of());
        when(carrierQuery.findActiveById(1L)).thenReturn(Optional.of(carrier(1L, 30L)));
        when(vehicleQuery.findById(5L)).thenReturn(Optional.of(new VehicleQuery.VehicleSnapshot(5L, 1L, "沪A123")));

        assertThatThrownBy(() -> service.apply(entity, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("车辆ID与车牌号不一致");
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
}
