package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SupplierStatementSourceServiceTest {

    @Test
    void shouldReturnPurchaseInboundCandidates() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundRepository purchaseInboundRepository = mock(PurchaseInboundRepository.class);
        PurchaseInbound sourceInbound = sourceInbound();
        when(purchaseInboundRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceInbound)));

        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                purchaseInboundRepository,
                mock(PurchaseInboundItemQueryService.class),
                null
        );

        List<SupplierStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of("RK", "完成采购", null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).inboundNo()).isEqualTo("RK-001");
        assertThat(candidates.get(0).supplierName()).isEqualTo("供应商甲");
        assertThat(candidates.get(0).warehouseName()).isEqualTo("一号仓");
        assertThat(candidates.get(0).status()).isEqualTo(StatusConstants.INBOUND_COMPLETED);
        assertThat(invokedMethodNames(repository))
                .contains("findOccupiedSourceInboundIdsExcludingCurrentStatement")
                .doesNotContain("findAll");
    }

    @Test
    void candidatePageShouldRequireCompletedInboundStatus() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundRepository purchaseInboundRepository = mock(PurchaseInboundRepository.class);
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(purchaseInboundRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                purchaseInboundRepository,
                mock(PurchaseInboundItemQueryService.class),
                null
        );

        service.candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of(null, null, null, null));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Specification<PurchaseInbound>> captor =
                org.mockito.ArgumentCaptor.forClass(Specification.class);
        verify(purchaseInboundRepository).findAll(captor.capture(), any(Pageable.class));
        Root<PurchaseInbound> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        @SuppressWarnings("unchecked")
        Path<Boolean> deletedFlagPath = mock(Path.class);
        @SuppressWarnings("unchecked")
        Path<String> statusPath = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.<Boolean>get("deletedFlag")).thenReturn(deletedFlagPath);
        when(root.<String>get("status")).thenReturn(statusPath);
        when(criteriaBuilder.isFalse(deletedFlagPath)).thenReturn(predicate);
        when(criteriaBuilder.conjunction()).thenReturn(predicate);
        when(criteriaBuilder.equal(statusPath, StatusConstants.INBOUND_COMPLETED)).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        captor.getValue().toPredicate(root, query, criteriaBuilder);

        verify(criteriaBuilder).equal(statusPath, StatusConstants.INBOUND_COMPLETED);
    }

    @Test
    void shouldApplySourceInboundItemsAndResolveSupplierCode() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound());
        Supplier supplier = new Supplier();
        supplier.setSupplierCode("SUP-001");
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(supplierRepository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc("供应商甲"))
                .thenReturn(java.util.Optional.of(supplier));
        SupplierStatement entity = new SupplierStatement();
        entity.setId(99L);
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                supplierRepository
        );

        SupplierStatementSourceService.SourceApplyResult result = service.applyItems(
                entity,
                supplierRequest(null, "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.purchaseAmount()).isEqualByComparingTo("123.55");
        assertThat(result.settlementCompanyId()).isEqualTo(1L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体A");
        assertThat(entity.getSupplierCode()).isEqualTo("SUP");
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getId()).isEqualTo(1000L);
        assertThat(entity.getItems().get(0).getSourceNo()).isEqualTo("RK-001");
        assertThat(entity.getItems().get(0).getAmount()).isEqualByComparingTo("123.45");
    }

    @Test
    void shouldRejectOccupiedSourceInbound() {
        SupplierStatementRepository repository = repositoryWithOccupiedInboundId(1L);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound sourceInbound = sourceInbound();
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatement entity = new SupplierStatement();
        entity.setId(99L);
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(entity, supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L), () -> 1000L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库单RK-001已生成供应商对账单");
    }

    @Test
    void shouldRejectInboundThatHasNotCompletedInbound() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound inbound = sourceInbound();
        inbound.setStatus(StatusConstants.AUDITED);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L)))
                .thenReturn(List.of(sourceInboundItem(10L, inbound)));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库单RK-001未完成入库");
    }

    @Test
    void shouldRejectMissingSourceInboundItem() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of());
        SupplierStatement entity = new SupplierStatement();
        entity.setId(99L);
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                mock(SupplierStatementRepository.class),
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(entity, supplierRequest("SUP", "供应商甲", null, null, 10L), () -> 1000L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单来源采购入库单不能为空");
    }

    @Test
    void shouldUseStableInboundIdWhenSourceNumberSnapshotChanged() {
        SupplierStatementRepository repository = repositoryWithOccupiedInboundId(1L);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound inbound = sourceInbound();
        inbound.setInboundNo("RK-RENAMED");
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, inbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库单RK-RENAMED已生成供应商对账单");
    }

    @Test
    void shouldAllowSameSourceNoWhenStableInboundIdIsDifferent() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound());
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        SupplierStatementSourceService.SourceApplyResult result = service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.purchaseAmount()).isEqualByComparingTo("123.55");
    }

    @Test
    void shouldRejectEmptySourceInboundItemIdsWithoutQueryingSourceItems() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                mock(SupplierStatementRepository.class),
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );
        Long sourceItemId = null;

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", null, null, sourceItemId),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单来源采购入库单不能为空");
        verifyNoInteractions(itemQueryService);
    }

    @Test
    void shouldRejectDuplicateSourceInboundItemIds() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound());
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );
        AtomicLong nextId = new AtomicLong(1000L);

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest(
                        "SUP",
                        "供应商甲",
                        1L,
                        "结算主体A",
                        List.of(supplierItemRequest(10L), supplierItemRequest(10L))
                ),
                nextId::getAndIncrement
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库明细ID重复");
    }

    @Test
    void shouldRejectDifferentSettlementCompanyFromSourceInbound() {
        PurchaseInbound sourceInbound = sourceInbound();
        sourceInbound.setSettlementCompanyId(2L);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                mock(SupplierStatementRepository.class),
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库单存在不同采购结算主体");
    }

    @Test
    void shouldAllowRequestSettlementCompanyWhenSourceInboundIdIsMissing() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound sourceInbound = sourceInbound();
        sourceInbound.setSettlementCompanyId(null);
        sourceInbound.setSettlementCompanyName(null);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        SupplierStatementSourceService.SourceApplyResult result = service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isNull();
    }

    @Test
    void shouldUseEmptySettlementCompanyWhenSourceInboundsHaveNoSettlementCompany() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound sourceInbound = sourceInbound();
        sourceInbound.setSettlementCompanyId(null);
        sourceInbound.setSettlementCompanyName(" ");
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        SupplierStatementSourceService.SourceApplyResult result = service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", null, null, 10L),
                () -> 1000L
        );

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isNull();
    }

    @Test
    void shouldUseSettlementCompanyNameWhenSourceInboundHasNameOnly() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound sourceInbound = sourceInbound();
        sourceInbound.setSettlementCompanyId(null);
        sourceInbound.setSettlementCompanyName("  结算主体名称  ");
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        SupplierStatementSourceService.SourceApplyResult result = service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", null, null, 10L),
                () -> 1000L
        );

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体名称");
    }

    @Test
    void shouldRejectConflictingSourceSettlementCompaniesWithoutRequestSettlementCompany() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound firstInbound = sourceInbound();
        PurchaseInbound secondInbound = sourceInbound();
        secondInbound.setInboundNo("RK-002");
        secondInbound.setSettlementCompanyId(2L);
        secondInbound.setSettlementCompanyName("结算主体B");
        PurchaseInboundItem firstItem = sourceInboundItem(10L, firstInbound);
        PurchaseInboundItem secondItem = sourceInboundItem(20L, secondInbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L, 20L))).thenReturn(List.of(firstItem, secondItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest(
                        "SUP",
                        "供应商甲",
                        null,
                        null,
                        List.of(supplierItemRequest(10L), supplierItemRequest(20L))
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库单存在不同采购结算主体");
    }

    @Test
    void shouldRejectBlankSourceInboundItemIdOnRequestedLine() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound());
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest(
                        "SUP",
                        "供应商甲",
                        1L,
                        "结算主体A",
                        List.of(supplierItemRequest(10L), supplierItemRequest(null))
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源采购入库明细不能为空");
    }

    @Test
    void shouldRejectMissingSourceInboundItemOnRequestedLine() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound());
        when(itemQueryService.findAllActiveByIdIn(List.of(10L, 20L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest(
                        "SUP",
                        "供应商甲",
                        1L,
                        "结算主体A",
                        List.of(supplierItemRequest(10L), supplierItemRequest(20L))
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源采购入库明细不存在");
    }

    @Test
    void shouldIgnoreLegacySourceNoOccupancyWhenStableInboundIdDoesNotConflict() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound());
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        SupplierStatementSourceService.SourceApplyResult result = service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.purchaseAmount()).isEqualByComparingTo("123.55");
    }

    @Test
    void shouldLeaveSupplierCodeEmptyWhenRequestCodeAndSupplierNameAreBlank() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        PurchaseInbound sourceInbound = sourceInbound();
        sourceInbound.setSupplierName(" ");
        PurchaseInboundItem sourceItem = sourceInboundItem(10L, sourceInbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        SupplierStatement entity = new SupplierStatement();
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                supplierRepository
        );

        service.applyItems(entity, supplierRequest(" ", " ", 1L, "结算主体A", 10L), () -> 1000L);

        assertThat(entity.getSupplierCode()).isEqualTo("SUP");
        verifyNoInteractions(supplierRepository);
    }

    @Test
    void shouldRejectPartialPurchaseInboundItemCoverage() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInbound inbound = sourceInbound();
        PurchaseInboundItem requestedItem = sourceInboundItem(10L, inbound);
        sourceInboundItem(20L, inbound);
        when(itemQueryService.findAllActiveByIdIn(List.of(10L))).thenReturn(List.of(requestedItem));
        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                mock(PurchaseInboundRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new SupplierStatement(),
                supplierRequest("SUP", "供应商甲", 1L, "结算主体A", 10L),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源采购入库单RK-001必须导入全部有效明细");
    }

    private SupplierStatementRepository repositoryWithOccupiedInboundId(Long occupiedInboundId) {
        return mock(SupplierStatementRepository.class, invocation -> {
            if ("findMatchingOccupiedSourceInboundIdsExcludingCurrentStatement"
                    .equals(invocation.getMethod().getName())) {
                return List.of(occupiedInboundId);
            }
            return org.mockito.Mockito.RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private List<String> invokedMethodNames(SupplierStatementRepository repository) {
        return mockingDetails(repository).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .toList();
    }

    private PurchaseInbound sourceInbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("RK-001");
        inbound.setSupplierCode("SUP");
        inbound.setSupplierName("供应商甲");
        inbound.setWarehouseName("一号仓");
        inbound.setInboundDate(LocalDate.of(2026, 5, 6));
        inbound.setSettlementCompanyId(1L);
        inbound.setSettlementCompanyName("结算主体A");
        inbound.setSettlementMode("按重量");
        inbound.setTotalWeight(new BigDecimal("1.000"));
        inbound.setTotalAmount(new BigDecimal("1000.00"));
        inbound.setStatus(StatusConstants.INBOUND_COMPLETED);
        return inbound;
    }

    private SupplierStatementRequest supplierRequest(String supplierCode,
                                                     String supplierName,
                                                     Long settlementCompanyId,
                                                     String settlementCompanyName,
                                                     Long sourceInboundItemId) {
        return new SupplierStatementRequest(
                "ST-001",
                supplierCode,
                supplierName,
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "草稿",
                null,
                List.of(supplierItemRequest(sourceInboundItemId))
        );
    }

    private SupplierStatementRequest supplierRequest(String supplierCode,
                                                     String supplierName,
                                                     Long settlementCompanyId,
                                                     String settlementCompanyName,
                                                     List<SupplierStatementItemRequest> items) {
        return new SupplierStatementRequest(
                "ST-001",
                supplierCode,
                supplierName,
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "草稿",
                null,
                items
        );
    }

    private SupplierStatementItemRequest supplierItemRequest(Long sourceInboundItemId) {
        return new SupplierStatementItemRequest(
                "RK-001",
                sourceInboundItemId,
                "M001",
                "HRB",
                "螺纹钢",
                "钢材",
                "12",
                "9m",
                "吨",
                "B001",
                1,
                "件",
                new BigDecimal("1.234"),
                1,
                new BigDecimal("1.234"),
                new BigDecimal("100.00"),
                new BigDecimal("123.45")
        );
    }

    private PurchaseInboundItem sourceInboundItem(Long id, PurchaseInbound inbound) {
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(id);
        item.setPurchaseInbound(inbound);
        item.setMaterialCode("M001");
        item.setBrand("HRB");
        item.setCategory("螺纹钢");
        item.setMaterial("钢材");
        item.setSpec("12");
        item.setLength("9m");
        item.setUnit("吨");
        item.setBatchNo("B001");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.234"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.234"));
        item.setWeighWeightTon(new BigDecimal("1.235"));
        item.setWeightAdjustmentTon(new BigDecimal("0.001"));
        item.setWeightAdjustmentAmount(new BigDecimal("0.10"));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal("123.45"));
        inbound.getItems().add(item);
        return item;
    }
}
