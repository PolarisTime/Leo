package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightStatementSourceServiceTest {

    @Test
    void shouldReturnFreightBillCandidates() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBill();
        when(freightBillRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceBill)));

        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        List<FreightStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of("FB", "已审核", null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).billNo()).isEqualTo("FB-001");
        assertThat(candidates.get(0).carrierCode()).isEqualTo("C-001");
        assertThat(candidates.get(0).carrierName()).isEqualTo("物流甲");
        assertThat(candidates.get(0).customerName()).isEqualTo("客户甲");
        assertThat(candidates.get(0).status()).isEqualTo(StatusConstants.AUDITED);
        assertThat(invokedMethodNames(repository))
                .contains("findOccupiedSourceFreightBillIdsExcludingCurrentStatement")
                .doesNotContain("findAll");
    }

    @Test
    void shouldUseStableFreightBillIdWhenSourceNumberSnapshotChanged() {
        FreightStatementRepository repository = repositoryWithOccupiedFreightBillId(1L);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-RENAMED");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(Set.of(1L))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, directItem(1L, 11L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单FB-RENAMED已生成物流对账单");
    }

    @Test
    void shouldRejectSalesOutboundItemFallbackWithoutDirectFreightSourceIds() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.setSettlementCompanyId(null);
        sourceBill.setSettlementCompanyName(" ");
        FreightBillItem item = sourceBill.getItems().get(0);
        item.setSourceSalesOutboundItemId(21L);
        item.setMaterialCode("OTHER");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(1L, null, null, itemBySourceSalesOutboundItemId(" FB-001 ", 21L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单ID不能为空");
    }

    @Test
    void shouldResolveDirectSourceIdsAndCopyStableMasterIdentity() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.setCarrierId(5L);
        FreightBillItem sourceItem = sourceBill.getItems().get(0);
        sourceItem.setCustomerId(101L);
        sourceItem.setProjectId(102L);
        sourceItem.setMaterialId(103L);
        sourceItem.setWarehouseId(104L);
        sourceItem.setBatchNoNormalized("B-001");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(Set.of(1L))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);
        FreightStatementItemCommand commandItem = new FreightStatementItemCommand(
                null, "FB-OLD-SNAPSHOT", null, null, null, "客户甲", "项目甲", "M-001", "螺纹钢",
                "HRB400", "钢材", "钢", "10", "9m", 4, "件", new BigDecimal("0.5"), 2,
                "B-001", null, "仓库甲", 1L, 11L, 101L, 102L, 103L, 104L
        );

        FreightStatement entity = new FreightStatement();
        service.applyItems(entity, command(1L, "物流主体A", null, commandItem), () -> 1000L);

        assertThat(entity.getCarrierId()).isEqualTo(5L);
        assertThat(entity.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceFreightBillId()).isEqualTo(1L);
            assertThat(item.getSourceFreightBillItemId()).isEqualTo(11L);
            assertThat(item.getSourceNo()).isEqualTo("FB-001");
            assertThat(item.getCustomerId()).isEqualTo(101L);
            assertThat(item.getProjectId()).isEqualTo(102L);
            assertThat(item.getMaterialId()).isEqualTo(103L);
            assertThat(item.getWarehouseId()).isEqualTo(104L);
            assertThat(item.getBatchNo()).isEqualTo("B-001");
        });
    }

    @Test
    void shouldRejectMissingSourceBillOnRequestedLineAfterBlankSourcesAreFiltered() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(
                        null,
                        null,
                        null,
                        itemByFields("FB-001"),
                        itemByFields(" ")
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单ID不能为空");
    }

    @Test
    void shouldRejectMissingSourceBillOnRequestedLineAfterNullSourcesAreFiltered() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(
                        null,
                        null,
                        null,
                        itemByFields("FB-001"),
                        itemByFields(null)
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单ID不能为空");
    }

    @Test
    void shouldApplyWhenStableFreightBillIdIsNotOccupied() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(Set.of(1L))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        FreightStatementSourceService.SourceApplyResult result = service.applyItems(
                new FreightStatement(),
                command(1L, "物流主体A", null, directItem(1L, 11L)),
                () -> 1000L
        );

        assertThat(result.totalFreight()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectFieldGuessingWithoutDirectFreightSourceIds() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        FreightBillItem sourceItem = sourceBill.getItems().get(0);
        sourceItem.setMaterialCode(null);
        sourceItem.setBatchNo(null);
        sourceItem.setWarehouseName(null);
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, itemByFields("FB-001", null, null, null, 4)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单ID不能为空");
    }

    @Test
    void shouldRejectMissingSourceBillItemBySalesOutboundItemId() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.getItems().get(0).setSourceSalesOutboundItemId(21L);
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, itemBySourceSalesOutboundItemId("FB-001", 404L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单ID不能为空");
    }

    @Test
    void shouldRejectMissingSourceBillItemByFieldMatch() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, itemByFields("FB-001", "UNKNOWN", "B-001", "仓库甲", 4)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单ID不能为空");
    }

    @Test
    void shouldRejectMissingDirectFreightBillItemId() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(Set.of(1L))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, directItem(1L, null)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单明细ID不能为空");
    }

    @Test
    void shouldRejectDuplicateSourceFreightBillItemIds() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(Set.of(1L))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);
        AtomicLong nextId = new AtomicLong(1000L);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, directItem(1L, 11L), directItem(1L, 11L)),
                nextId::getAndIncrement
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单明细ID重复");
    }

    @Test
    void shouldRejectSettlementCompanyConflictFromCommandAndSourceBill() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.setSettlementCompanyId(2L);
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(Set.of(1L))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(
                mock(FreightStatementRepository.class),
                freightBillRepository
        );

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(1L, "物流主体A", null, directItem(1L, 11L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单存在不同物流结算主体");
    }

    @Test
    void shouldRejectSettlementCompanyConflictAcrossSourceBills() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill firstBill = sourceBillWithItem("FB-001");
        firstBill.setSettlementCompanyId(1L);
        firstBill.setSettlementCompanyName("物流主体A");
        FreightBill secondBill = sourceBillWithItem("FB-002");
        secondBill.setId(2L);
        secondBill.getItems().get(0).setId(12L);
        secondBill.setSettlementCompanyId(null);
        secondBill.setSettlementCompanyName("物流主体B");
        when(freightBillRepository.findByIdInAndDeletedFlagFalse(new LinkedHashSet<>(List.of(1L, 2L))))
                .thenReturn(List.of(firstBill, secondBill));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, directItem(1L, 11L), directItem(2L, 12L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单存在不同物流结算主体");
    }

    private FreightBill sourceBill() {
        FreightBill bill = new FreightBill();
        bill.setId(1L);
        bill.setBillNo("FB-001");
        bill.setCarrierCode("C-001");
        bill.setCarrierName("物流甲");
        bill.setCustomerName("客户甲");
        bill.setProjectName("项目A");
        bill.setBillTime(LocalDate.of(2026, 5, 6));
        bill.setTotalWeight(new BigDecimal("1.000"));
        bill.setTotalFreight(new BigDecimal("100.00"));
        bill.setStatus(StatusConstants.AUDITED);
        return bill;
    }

    private FreightStatementRepository repositoryWithOccupiedFreightBillId(Long occupiedFreightBillId) {
        return mock(FreightStatementRepository.class, invocation -> {
            if ("findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement"
                    .equals(invocation.getMethod().getName())) {
                return List.of(occupiedFreightBillId);
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private List<String> invokedMethodNames(FreightStatementRepository repository) {
        return mockingDetails(repository).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .toList();
    }

    private FreightStatementCommand command(Long settlementCompanyId,
                                            String settlementCompanyName,
                                            BigDecimal paidAmount,
                                            FreightStatementItemCommand... items) {
        return new FreightStatementCommand(
                "FS-001",
                null,
                "物流甲",
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                paidAmount == null ? BigDecimal.ZERO : paidAmount,
                null,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                "备注",
                List.of(items)
        );
    }

    private FreightStatementItemCommand itemByFields(String sourceNo) {
        return itemByFields(sourceNo, "M-001", "B-001", "仓库甲", 4);
    }

    private FreightStatementItemCommand itemByFields(String sourceNo,
                                                     String materialCode,
                                                     String batchNo,
                                                     String warehouseName,
                                                     Integer quantity) {
        return new FreightStatementItemCommand(
                null,
                sourceNo,
                null,
                null,
                null,
                "客户甲",
                "项目甲",
                materialCode,
                "螺纹钢",
                "HRB400",
                "钢材",
                "钢",
                "10",
                "9m",
                quantity,
                "件",
                new BigDecimal("0.5"),
                2,
                batchNo,
                null,
                warehouseName
        );
    }

    private FreightStatementItemCommand itemBySourceSalesOutboundItemId(String sourceNo, Long sourceSalesOutboundItemId) {
        return new FreightStatementItemCommand(
                null,
                sourceNo,
                sourceSalesOutboundItemId,
                null,
                null,
                "客户甲",
                "项目甲",
                "M-001",
                "螺纹钢",
                "HRB400",
                "钢材",
                "钢",
                "10",
                "9m",
                4,
                "件",
                new BigDecimal("0.5"),
                2,
                "B-001",
                null,
                "仓库甲"
        );
    }

    private FreightStatementItemCommand directItem(Long sourceFreightBillId, Long sourceFreightBillItemId) {
        return new FreightStatementItemCommand(
                null,
                "FB-SNAPSHOT",
                null,
                null,
                null,
                "客户甲",
                "项目甲",
                "M-001",
                "螺纹钢",
                "HRB400",
                "钢材",
                "钢",
                "10",
                "9m",
                4,
                "件",
                new BigDecimal("0.5"),
                2,
                "B-001",
                null,
                "仓库甲",
                sourceFreightBillId,
                sourceFreightBillItemId,
                null,
                null,
                null,
                null
        );
    }

    private FreightBill sourceBillWithItem(String billNo) {
        FreightBill bill = sourceBill();
        bill.setBillNo(billNo);
        bill.setSettlementCompanyId(1L);
        bill.setSettlementCompanyName("物流主体A");
        FreightBillItem item = new FreightBillItem();
        item.setId(11L);
        item.setFreightBill(bill);
        item.setLineNo(1);
        item.setSourceNo(billNo);
        item.setSourceSalesOutboundItemId(null);
        item.setSettlementCompanyId(7L);
        item.setSettlementCompanyName("主体A");
        item.setCustomerName("客户甲");
        item.setProjectName("项目甲");
        item.setMaterialCode("M-001");
        item.setMaterialName("螺纹钢");
        item.setBrand("HRB400");
        item.setCategory("钢材");
        item.setMaterial("钢");
        item.setSpec("10");
        item.setLength("9m");
        item.setQuantity(4);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.5"));
        item.setPiecesPerBundle(2);
        item.setBatchNo("B-001");
        item.setWeightTon(new BigDecimal("2.000"));
        item.setWarehouseName("仓库甲");
        bill.getItems().add(item);
        return bill;
    }
}
