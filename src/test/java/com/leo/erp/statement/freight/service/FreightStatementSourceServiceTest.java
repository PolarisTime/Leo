package com.leo.erp.statement.freight.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementCandidateResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightStatementSourceServiceTest {

    @Test
    void shouldReturnFreightBillCandidates() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBill();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
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
    }

    @Test
    void shouldCollectOccupiedBillNosWithCurrentStatementAndIgnoreBlankSources() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightStatement occupied = new FreightStatement();
        FreightStatementItem blankItem = new FreightStatementItem();
        blankItem.setSourceNo(" ");
        FreightStatementItem nullItem = new FreightStatementItem();
        nullItem.setSourceNo(null);
        FreightStatementItem sourceItem = new FreightStatementItem();
        sourceItem.setSourceNo(" FB-001 ");
        FreightStatementItem duplicateItem = new FreightStatementItem();
        duplicateItem.setSourceNo("FB-001");
        occupied.getItems().addAll(List.of(blankItem, nullItem, sourceItem, duplicateItem));
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(occupied));
        FreightStatementSourceService service = new FreightStatementSourceService(
                repository,
                mock(FreightBillRepository.class)
        );

        Set<String> occupiedBillNos = service.collectOccupiedBillNos(99L);

        assertThat(occupiedBillNos).containsExactly("FB-001");
        assertCurrentStatementExclusionPredicateWasBuilt(repository);
    }

    @Test
    void shouldApplySourceBillItemBySalesOutboundItemIdAndAllowBlankSettlementCompany() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.setSettlementCompanyId(null);
        sourceBill.setSettlementCompanyName(" ");
        FreightBillItem item = sourceBill.getItems().get(0);
        item.setSourceSalesOutboundItemId(21L);
        item.setMaterialCode("OTHER");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), 99L)).thenReturn(List.of());
        FreightStatement entity = new FreightStatement();
        entity.setId(99L);
        AtomicLong nextId = new AtomicLong(1000L);
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        FreightStatementSourceService.SourceApplyResult result = service.applyItems(
                entity,
                command(1L, null, null, itemBySourceSalesOutboundItemId(" FB-001 ", 21L)),
                nextId::getAndIncrement
        );

        assertThat(result.totalWeight()).isEqualByComparingTo("2.00000000");
        assertThat(result.totalFreight()).isEqualByComparingTo("100.00");
        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getId()).isEqualTo(1000L);
        assertThat(entity.getItems().get(0).getSourceNo()).isEqualTo("FB-001");
        assertThat(entity.getItems().get(0).getSourceSalesOutboundItemId()).isEqualTo(21L);
        assertThat(entity.getItems().get(0).getMaterialCode()).isEqualTo("M-001");
    }

    @Test
    void shouldRejectMissingSourceBillOnRequestedLineAfterBlankSourcesAreFiltered() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), null)).thenReturn(List.of());
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
                .hasMessageContaining("第2行来源物流单不存在");
    }

    @Test
    void shouldRejectMissingSourceBillOnRequestedLineAfterNullSourcesAreFiltered() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), null)).thenReturn(List.of());
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
                .hasMessageContaining("第2行来源物流单不存在");
    }

    @Test
    void shouldApplyWhenSettlementCompanyMatchesAndOccupiedSourcesAreBlankOrDifferent() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        FreightStatement occupied = new FreightStatement();
        FreightStatementItem nullItem = new FreightStatementItem();
        nullItem.setSourceNo(null);
        FreightStatementItem blankItem = new FreightStatementItem();
        blankItem.setSourceNo(" ");
        FreightStatementItem otherItem = new FreightStatementItem();
        otherItem.setSourceNo("FB-OTHER");
        occupied.getItems().addAll(List.of(nullItem, blankItem, otherItem));
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), null)).thenReturn(List.of(occupied));
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        FreightStatementSourceService.SourceApplyResult result = service.applyItems(
                new FreightStatement(),
                command(1L, "物流主体A", null, itemByFields("FB-001")),
                () -> 1000L
        );

        assertThat(result.totalFreight()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldMatchSourceBillItemWhenNullableTextFieldsAreEmptyOnBothSides() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        FreightBillItem sourceItem = sourceBill.getItems().get(0);
        sourceItem.setMaterialCode(null);
        sourceItem.setBatchNo(null);
        sourceItem.setWarehouseName(null);
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), null)).thenReturn(List.of());
        FreightStatement entity = new FreightStatement();
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        service.applyItems(
                entity,
                command(null, null, null, itemByFields("FB-001", null, null, null, 4)),
                () -> 1000L
        );

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getMaterialCode()).isNull();
    }

    @Test
    void shouldRejectMissingSourceBillItemBySalesOutboundItemId() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.getItems().get(0).setSourceSalesOutboundItemId(21L);
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), null)).thenReturn(List.of());
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, itemBySourceSalesOutboundItemId("FB-001", 404L)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单明细不存在");
    }

    @Test
    void shouldRejectMissingSourceBillItemByFieldMatch() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("FB-001"), null)).thenReturn(List.of());
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, itemByFields("FB-001", "UNKNOWN", "B-001", "仓库甲", 4)),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行来源物流单明细不存在");
    }

    @Test
    void shouldRejectSettlementCompanyConflictFromCommandAndSourceBill() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        FreightBill sourceBill = sourceBillWithItem("FB-001");
        sourceBill.setSettlementCompanyId(2L);
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(Set.of("FB-001"))).thenReturn(List.of(sourceBill));
        FreightStatementSourceService service = new FreightStatementSourceService(
                mock(FreightStatementRepository.class),
                freightBillRepository
        );

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(1L, "物流主体A", null, itemByFields("FB-001")),
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
        secondBill.setSettlementCompanyId(null);
        secondBill.setSettlementCompanyName("物流主体B");
        when(freightBillRepository.findByBillNoInAndDeletedFlagFalse(new LinkedHashSet<>(List.of("FB-001", "FB-002"))))
                .thenReturn(List.of(firstBill, secondBill));
        when(repository.findAllBySourceNosExcludingCurrentStatement(new LinkedHashSet<>(List.of("FB-001", "FB-002")), null))
                .thenReturn(List.of());
        FreightStatementSourceService service = new FreightStatementSourceService(repository, freightBillRepository);

        assertThatThrownBy(() -> service.applyItems(
                new FreightStatement(),
                command(null, null, null, itemByFields("FB-001"), itemByFields("FB-002")),
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

    @SuppressWarnings("unchecked")
    private void assertCurrentStatementExclusionPredicateWasBuilt(FreightStatementRepository repository) {
        ArgumentCaptor<Specification<FreightStatement>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture());
        Root<FreightStatement> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("deletedFlag")).thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(root.get("id")).thenReturn(mock(jakarta.persistence.criteria.Path.class));
        when(criteriaBuilder.isFalse(any())).thenReturn(predicate);
        when(criteriaBuilder.notEqual(any(), any())).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        assertThat(captor.getValue().toPredicate(root, query, criteriaBuilder)).isSameAs(predicate);
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
