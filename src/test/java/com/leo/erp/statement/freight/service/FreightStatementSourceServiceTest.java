package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.BillSnapshot;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidatePage;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.CandidateSnapshot;
import com.leo.erp.logistics.api.FreightBillStatementSourceQuery.ItemSnapshot;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FreightStatementSourceService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class FreightStatementSourceServiceTest {

    @Mock
    private FreightStatementRepository repository;

    @Mock
    private FreightBillStatementSourceQuery sourceQuery;

    @InjectMocks
    private FreightStatementSourceService service;

    private ItemSnapshot item(Long id) {
        return new ItemSnapshot(id, 30L, "结算公司A", 10L, "客户A", 20L, "项目A", 500L, "M001",
                "螺纹钢", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", 10, "件", new BigDecimal("1.250"),
                100, "B001", new BigDecimal("12.500"), 1L, "库房A");
    }

    private BillSnapshot bill(Long id, String billNo, Long carrierId, String carrierCode, String carrierName,
                              Long settlementCompanyId, String settlementCompanyName, String status,
                              List<ItemSnapshot> items) {
        return new BillSnapshot(id, billNo, carrierId, carrierCode, carrierName, settlementCompanyId,
                settlementCompanyName, LocalDate.of(2026, 8, 1), new BigDecimal("100"),
                new BigDecimal("5000"), status, items);
    }

    private FreightStatementItemCommand itemCommand(Long billId, Long billItemId, Long customerId) {
        return new FreightStatementItemCommand(
                null, "FB001", 30L, "结算公司A", "客户A", "项目A", "M001", "螺纹钢", "品牌A",
                "型钢", "螺纹钢", "HRB400", "12m", 10, "件", new BigDecimal("1.250"), 100, "B001",
                new BigDecimal("12.500"), "库房A", billId, billItemId, customerId, 20L, 500L, 1L);
    }

    private FreightStatementCommand command(Long carrierId, String carrierCode, Long settlementCompanyId,
                                            List<FreightStatementItemCommand> items) {
        return new FreightStatementCommand(
                "FS001", carrierCode, "承运商A", settlementCompanyId, "结算公司A",
                null, null, null, null, null, null, "DRAFT", null, null, items, carrierId);
    }

    private void stubSingleBill(BillSnapshot bill, FreightStatementCommand cmd, Long currentStatementId) {
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));
        when(repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
    }

    // ---------- candidatePage ----------

    @Test
    void candidatePage_shouldMapCandidates() {
        when(repository.findOccupiedSourceFreightBillIdsExcludingCurrentStatement(any())).thenReturn(List.of());
        CandidateSnapshot candidate = new CandidateSnapshot(1L, "FB001", "C001", "承运商A", 30L, "结算公司A",
                "客户A", "项目A", LocalDate.of(2026, 8, 1), new BigDecimal("100"), new BigDecimal("5000"),
                StatusConstants.AUDITED, 100L);
        when(sourceQuery.findCandidates(any())).thenReturn(new CandidatePage(List.of(candidate), 1, 1, 0, 10));
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));

        Page<FreightStatementCandidateResponse> result = service.candidatePage(query, mock(PageFilter.class));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).billNo()).isEqualTo("FB001");
    }

    // ---------- applyItems 来源 ID 校验 ----------

    @Test
    void applyItems_shouldRejectNullBillId() {
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(null, 11L, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单ID不能为空");
    }

    @Test
    void applyItems_shouldRejectNullBillItemId() {
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, null, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectDuplicateBillItemId() {
        FreightStatementCommand cmd = command(null, null, null,
                List.of(itemCommand(1L, 11L, null), itemCommand(1L, 11L, null)));

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("明细ID重复");
    }

    // ---------- applyItems 来源物流单校验 ----------

    @Test
    void applyItems_shouldRejectEmptySourceBills() {
        FreightStatementCommand cmd = command(null, null, null, List.of());

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单不能为空");
    }

    @Test
    void applyItems_shouldRejectMissingSourceBill() {
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(999L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void applyItems_shouldRejectUnauditedBill() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.DRAFT, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未审核");
    }

    @Test
    void applyItems_shouldRejectMismatchedSettlementCompany() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, 99L, List.of(itemCommand(1L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同物流结算主体");
    }

    @Test
    void applyItems_shouldRejectOccupiedBill() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));
        when(repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of(1L));

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已生成物流对账单");
    }

    @Test
    void applyItems_shouldRejectIncompleteImport() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L), item(12L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));
        when(repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("全部明细");
    }

    // ---------- applyItems 物流商一致性 ----------

    @Test
    void applyItems_shouldRejectCarrierCodeMissing() {
        BillSnapshot bill = bill(1L, "FB001", 100L, null, "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));
        when(repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商编码缺失");
    }

    @Test
    void applyItems_shouldRejectDifferentCarrierCodes() {
        BillSnapshot billA = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        BillSnapshot billB = bill(2L, "FB002", 100L, "C002", "承运商B", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(21L)));
        FreightStatementCommand cmd = command(null, null, null,
                List.of(itemCommand(1L, 11L, null), itemCommand(2L, 21L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(billA, billB));
        when(repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不同物流商编码");
    }

    @Test
    void applyItems_shouldRejectCarrierIdMismatch() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(999L, null, null, List.of(itemCommand(1L, 11L, null)));
        when(sourceQuery.findByBillIds(any())).thenReturn(List.of(bill));
        when(repository.findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商ID与来源物流单不一致");
    }

    // ---------- applyItems 正常路径 ----------

    @Test
    void applyItems_shouldApplySingleBill() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        stubSingleBill(bill, cmd, null);
        FreightStatement entity = new FreightStatement();

        FreightStatementSourceService.SourceApplyResult result = service.applyItems(entity, cmd, () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getSourceFreightBillItemId()).isEqualTo(11L);
        assertThat(entity.getCarrierId()).isEqualTo(100L);
        assertThat(entity.getCarrierCode()).isEqualTo("C001");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(result.totalWeight()).isEqualByComparingTo("12.500");
        assertThat(result.totalFreight()).isEqualByComparingTo("5000");
        assertThat(result.startDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    // ---------- 补充边界 ----------

    @Test
    void applyItems_shouldRejectMissingBillTime() {
        BillSnapshot bill = new BillSnapshot(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                null, new BigDecimal("100"), new BigDecimal("5000"), StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        stubSingleBill(bill, cmd, null);

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("日期不能为空");
    }

    @Test
    void applyItems_shouldRejectMissingSettlementCompany() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", null, null,
                StatusConstants.AUDITED, List.of(item(11L)));
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, null)));
        stubSingleBill(bill, cmd, null);

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体缺失");
    }

    @Test
    void applyItems_shouldRejectLineIdentityMismatch() {
        BillSnapshot bill = bill(1L, "FB001", 100L, "C001", "承运商A", 30L, "结算公司A",
                StatusConstants.AUDITED, List.of(item(11L)));
        // 明细行客户 ID 99 与来源明细客户 ID 10 不一致
        FreightStatementCommand cmd = command(null, null, null, List.of(itemCommand(1L, 11L, 99L)));
        stubSingleBill(bill, cmd, null);

        assertThatThrownBy(() -> service.applyItems(new FreightStatement(), cmd, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID");
    }
}
