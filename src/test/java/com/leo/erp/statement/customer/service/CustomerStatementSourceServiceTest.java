package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerStatementSourceServiceTest {

    @Test
    void shouldReturnSalesOrderCandidates() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);
        SalesOrder sourceOrder = sourceOrder();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceOrder)));

        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                salesOrderRepository,
                mock(SalesOrderItemQueryService.class),
                null
        );

        List<CustomerStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of("SO", "完成销售", null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).orderNo()).isEqualTo("SO-001");
        assertThat(candidates.get(0).customerName()).isEqualTo("客户甲");
        assertThat(candidates.get(0).projectName()).isEqualTo("项目A");
        assertThat(candidates.get(0).status()).isEqualTo(StatusConstants.SALES_COMPLETED);
    }

    @Test
    void shouldApplySourceOrderItemsAndResolveCustomerCode() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder());
        Customer customer = new Customer();
        customer.setCustomerCode("CUS-001");
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), 99L)).thenReturn(List.of());
        when(customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc("客户甲", "项目A"))
                .thenReturn(java.util.Optional.of(customer));
        CustomerStatement entity = new CustomerStatement();
        entity.setId(99L);
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                customerRepository
        );

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(
                entity,
                customerRequest(null, "客户甲", "项目A", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.salesAmount()).isEqualByComparingTo("123.45");
        assertThat(result.settlementCompanyId()).isEqualTo(1L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体A");
        assertThat(entity.getCustomerCode()).isEqualTo("CUS-001");
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getId()).isEqualTo(1000L);
        assertThat(entity.getItems().get(0).getSourceNo()).isEqualTo("SO-001");
        assertThat(entity.getItems().get(0).getAmount()).isEqualByComparingTo("123.45");
    }

    @Test
    void shouldUseAuditedOutboundActualTotalsForUnderFulfilledCompletedOrder() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOutboundRepository outboundRepository = mock(SalesOutboundRepository.class);
        SalesOrder sourceOrder = sourceOrder();
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        sourceItem.setQuantity(100);
        sourceItem.setPieceWeightTon(new BigDecimal("1.00000000"));
        sourceItem.setWeightTon(new BigDecimal("100.00000000"));
        sourceItem.setUnitPrice(new BigDecimal("10.00"));
        sourceItem.setAmount(new BigDecimal("1000.00"));
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(20L);
        outbound.setStatus(StatusConstants.AUDITED);
        SalesOutboundItem outboundItem = new SalesOutboundItem();
        outboundItem.setId(21L);
        outboundItem.setSalesOutbound(outbound);
        outboundItem.setSourceSalesOrderItemId(10L);
        outboundItem.setQuantity(96);
        outboundItem.setQuantityUnit("件");
        outboundItem.setPieceWeightTon(new BigDecimal("1.00000000"));
        outboundItem.setPiecesPerBundle(1);
        outboundItem.setWeightTon(new BigDecimal("96.00000000"));
        outboundItem.setUnitPrice(new BigDecimal("10.00"));
        outboundItem.setAmount(new BigDecimal("960.00"));
        outbound.setItems(new java.util.ArrayList<>(List.of(outboundItem)));
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), 99L))
                .thenReturn(List.of());
        when(outboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(
                StatusConstants.AUDITED,
                List.of(10L)
        )).thenReturn(List.of(outbound));
        CustomerStatement entity = new CustomerStatement();
        entity.setId(99L);
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null,
                outboundRepository
        );

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(
                entity,
                customerRequest(null, "客户甲", "项目A", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.salesAmount()).isEqualByComparingTo("960.00");
        assertThat(entity.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getQuantity()).isEqualTo(96);
            assertThat(item.getWeightTon()).isEqualByComparingTo("96.00000000");
            assertThat(item.getAmount()).isEqualByComparingTo("960.00");
        });
    }

    @Test
    void shouldRejectOccupiedSourceOrder() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder());
        CustomerStatement occupied = new CustomerStatement();
        CustomerStatementItem occupiedItem = new CustomerStatementItem();
        occupiedItem.setSourceNo("SO-001");
        occupied.getItems().add(occupiedItem);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), 99L)).thenReturn(List.of(occupied));
        CustomerStatement entity = new CustomerStatement();
        entity.setId(99L);
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(entity, customerRequest("CUS", "客户甲", "项目A", 1L, "结算主体A", 10L), () -> 1000L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单SO-001已生成客户对账单");
    }

    @Test
    void shouldIgnoreBlankOccupiedSourceNosWhenApplyingItems() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder());
        CustomerStatement occupied = new CustomerStatement();
        CustomerStatementItem blankItem = new CustomerStatementItem();
        blankItem.setSourceNo(" ");
        CustomerStatementItem nullItem = new CustomerStatementItem();
        nullItem.setSourceNo(null);
        occupied.getItems().addAll(List.of(blankItem, nullItem));
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), 99L)).thenReturn(List.of(occupied));
        CustomerStatement entity = new CustomerStatement();
        entity.setId(99L);
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(
                entity,
                customerRequest("CUS", "客户甲", "项目A", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.salesAmount()).isEqualByComparingTo("123.45");
        assertThat(entity.getItems()).hasSize(1);
    }

    @Test
    void shouldRejectDifferentCustomerCodeFromSourceOrder() {
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setCustomerCode("CUS-002");
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), 99L)).thenReturn(List.of());
        CustomerStatement entity = new CustomerStatement();
        entity.setId(99L);
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(entity, customerRequest("CUS-001", "客户甲", "项目A", 1L, "结算主体A", 10L), () -> 1000L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码与客户对账单客户编码不一致");
    }

    @Test
    void shouldKeepCustomerCodeWhenSourceOrderCodeMatchesRequestCode() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setCustomerCode("CUS-001");
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatement entity = new CustomerStatement();
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        service.applyItems(entity, customerRequest("CUS-001", "客户甲", "项目A", 1L, "结算主体A", 10L), () -> 1000L);

        assertThat(entity.getCustomerCode()).isEqualTo("CUS-001");
    }

    @Test
    void shouldCollectOccupiedOrderNosWithCurrentStatementAndIgnoreBlankSources() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement occupied = new CustomerStatement();
        CustomerStatementItem blankItem = new CustomerStatementItem();
        blankItem.setSourceNo(" ");
        CustomerStatementItem nullItem = new CustomerStatementItem();
        nullItem.setSourceNo(null);
        CustomerStatementItem sourceItem = new CustomerStatementItem();
        sourceItem.setSourceNo(" SO-001 ");
        occupied.getItems().addAll(List.of(blankItem, nullItem, sourceItem));
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(occupied));
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                null
        );

        Set<String> occupiedOrderNos = service.collectOccupiedOrderNos(99L);

        assertThat(occupiedOrderNos).containsExactly("SO-001");
        assertCurrentStatementExclusionPredicateWasBuilt(repository);
    }

    @Test
    void shouldRejectEmptySourceSalesOrderItemIdsWithoutQueryingSourceItems() {
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                mock(CustomerStatementRepository.class),
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );
        Long sourceItemId = null;

        assertThatThrownBy(() -> service.applyItems(
                new CustomerStatement(),
                customerRequest("CUS", "客户甲", "项目A", null, null, sourceItemId),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单来源销售订单不能为空");
        verifyNoInteractions(itemQueryService);
    }

    @Test
    void shouldRejectDifferentSettlementCompanyFromSourceOrder() {
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setSettlementCompanyId(2L);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                mock(CustomerStatementRepository.class),
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new CustomerStatement(),
                customerRequest("CUS", "客户甲", "项目A", 1L, "结算主体A", 10L),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单存在不同客户结算主体");
    }

    @Test
    void shouldAllowRequestSettlementCompanyWhenSourceOrderIdIsMissing() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setSettlementCompanyId(null);
        sourceOrder.setSettlementCompanyName(null);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(
                new CustomerStatement(),
                customerRequest("CUS", "客户甲", "项目A", 1L, "结算主体A", 10L),
                () -> 1000L
        );

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isNull();
    }

    @Test
    void shouldRejectBlankSourceSalesOrderItemIdOnRequestedLine() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder());
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new CustomerStatement(),
                customerRequest(
                        "CUS",
                        "客户甲",
                        "项目A",
                        1L,
                        "结算主体A",
                        List.of(customerItemRequest(10L), customerItemRequest(null))
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源销售订单明细不能为空");
    }

    @Test
    void shouldRejectMissingSourceSalesOrderItemOnRequestedLine() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder());
        when(itemQueryService.findActiveByIdIn(List.of(10L, 20L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new CustomerStatement(),
                customerRequest(
                        "CUS",
                        "客户甲",
                        "项目A",
                        1L,
                        "结算主体A",
                        List.of(customerItemRequest(10L), customerItemRequest(20L))
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行来源销售订单明细不存在");
    }

    @Test
    void shouldLeaveCustomerCodeEmptyWhenRequestAndSourceCodesAreBlankAndCustomerIsBlank() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setCustomerName(" ");
        sourceOrder.setProjectName(" ");
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatement entity = new CustomerStatement();
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                customerRepository
        );

        service.applyItems(entity, customerRequest(" ", " ", " ", 1L, "结算主体A", 10L), () -> 1000L);

        assertThat(entity.getCustomerCode()).isNull();
        verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldLeaveCustomerCodeEmptyWhenCustomerRepositoryIsUnavailable() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setCustomerCode(null);
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatement entity = new CustomerStatement();
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        service.applyItems(entity, customerRequest(null, "客户甲", "项目A", 1L, "结算主体A", 10L), () -> 1000L);

        assertThat(entity.getCustomerCode()).isNull();
    }

    @Test
    void shouldNotResolveCustomerCodeWhenCustomerNamePresentButProjectNameBlank() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrder sourceOrder = sourceOrder();
        sourceOrder.setCustomerCode(null);
        sourceOrder.setProjectName(" ");
        SalesOrderItem sourceItem = sourceOrderItem(10L, sourceOrder);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(sourceItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001"), null)).thenReturn(List.of());
        CustomerStatement entity = new CustomerStatement();
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                customerRepository
        );

        service.applyItems(entity, customerRequest(null, "客户甲", " ", 1L, "结算主体A", 10L), () -> 1000L);

        assertThat(entity.getCustomerCode()).isNull();
        verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldRejectDifferentSettlementCompanyNamesWhenIdsAreMissing() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrder firstOrder = sourceOrder();
        firstOrder.setSettlementCompanyId(null);
        firstOrder.setSettlementCompanyName("结算主体A");
        SalesOrder secondOrder = sourceOrder();
        secondOrder.setOrderNo("SO-002");
        secondOrder.setSettlementCompanyId(null);
        secondOrder.setSettlementCompanyName("结算主体B");
        SalesOrderItem firstItem = sourceOrderItem(10L, firstOrder);
        SalesOrderItem secondItem = sourceOrderItem(20L, secondOrder);
        when(itemQueryService.findActiveByIdIn(List.of(10L, 20L))).thenReturn(List.of(firstItem, secondItem));
        when(repository.findAllBySourceNosExcludingCurrentStatement(Set.of("SO-001", "SO-002"), null)).thenReturn(List.of());
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new CustomerStatement(),
                customerRequest(
                        null,
                        "客户甲",
                        "项目A",
                        null,
                        null,
                        List.of(customerItemRequest(10L), customerItemRequest(20L))
                ),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单存在不同客户结算主体");
    }

    @Test
    void shouldRejectPartialSalesOrderItemCoverage() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrder order = sourceOrder();
        SalesOrderItem requestedItem = sourceOrderItem(10L, order);
        sourceOrderItem(20L, order);
        when(itemQueryService.findActiveByIdIn(List.of(10L))).thenReturn(List.of(requestedItem));
        CustomerStatementSourceService service = new CustomerStatementSourceService(
                repository,
                mock(SalesOrderRepository.class),
                itemQueryService,
                null
        );

        assertThatThrownBy(() -> service.applyItems(
                new CustomerStatement(),
                customerRequest(null, "客户甲", "项目A", 1L, "结算主体A", 10L),
                () -> 1000L
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单SO-001必须导入全部有效明细");
    }

    private SalesOrder sourceOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setOrderNo("SO-001");
        order.setCustomerName("客户甲");
        order.setProjectName("项目A");
        order.setSettlementCompanyId(1L);
        order.setSettlementCompanyName("结算主体A");
        order.setDeliveryDate(LocalDate.of(2026, 5, 6));
        order.setSalesName("张三");
        order.setTotalWeight(new BigDecimal("1.000"));
        order.setTotalAmount(new BigDecimal("1000.00"));
        order.setStatus(StatusConstants.SALES_COMPLETED);
        return order;
    }

    @SuppressWarnings("unchecked")
    private void assertCurrentStatementExclusionPredicateWasBuilt(CustomerStatementRepository repository) {
        ArgumentCaptor<Specification<CustomerStatement>> captor = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(captor.capture());
        Root<CustomerStatement> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Predicate predicate = mock(Predicate.class);
        Path<Object> deletedFlagPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        when(root.get("deletedFlag")).thenReturn(deletedFlagPath);
        when(root.get("id")).thenReturn(idPath);
        when(criteriaBuilder.isFalse(any())).thenReturn(predicate);
        when(criteriaBuilder.notEqual(idPath, 99L)).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate.class), any(Predicate.class))).thenReturn(predicate);

        assertThat(captor.getValue().toPredicate(root, query, criteriaBuilder)).isSameAs(predicate);
        verify(criteriaBuilder).notEqual(idPath, 99L);
    }

    private CustomerStatementRequest customerRequest(String customerCode,
                                                     String customerName,
                                                     String projectName,
                                                     Long settlementCompanyId,
                                                     String settlementCompanyName,
                                                     Long sourceSalesOrderItemId) {
        return new CustomerStatementRequest(
                "ST-001",
                customerCode,
                customerName,
                null,
                projectName,
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "草稿",
                null,
                List.of(customerItemRequest(sourceSalesOrderItemId))
        );
    }

    private CustomerStatementRequest customerRequest(String customerCode,
                                                     String customerName,
                                                     String projectName,
                                                     Long settlementCompanyId,
                                                     String settlementCompanyName,
                                                     List<CustomerStatementItemRequest> items) {
        return new CustomerStatementRequest(
                "ST-001",
                customerCode,
                customerName,
                null,
                projectName,
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

    private CustomerStatementItemRequest customerItemRequest(Long sourceSalesOrderItemId) {
        return new CustomerStatementItemRequest(
                "SO-001",
                sourceSalesOrderItemId,
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

    private SalesOrderItem sourceOrderItem(Long id, SalesOrder order) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
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
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal("123.45"));
        order.getItems().add(item);
        return item;
    }
}
