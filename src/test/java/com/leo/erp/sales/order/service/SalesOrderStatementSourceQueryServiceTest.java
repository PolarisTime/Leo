package com.leo.erp.sales.order.service;

import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.AuditedOutboundActualSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidateCriteria;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidatePage;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidateSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.ItemSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.OrderSnapshot;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyChar;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SalesOrderStatementSourceQueryService 极端情况测试。
 * <p>
 * 覆盖：null/空/脏集合、重复与去重、雪花 ID 精度边界、非法分页与排序、
 * outbound 实际值聚合（null 过滤、跨项合并）、Math.addExact 溢出、Spec lambda 分支。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderStatementSourceQueryServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private SalesOutboundRepository salesOutboundRepository;

    @InjectMocks
    private SalesOrderStatementSourceQueryService service;

    // ---------- 测试数据 ----------

    private SalesOrder order(Long id, String orderNo, List<SalesOrderItem> items) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setCustomerCode("CUST001");
        order.setCustomerId(10L);
        order.setCustomerName("客户A");
        order.setProjectId(20L);
        order.setProjectName("项目A");
        order.setSettlementCompanyId(30L);
        order.setSettlementCompanyName("结算公司A");
        order.setDeliveryDate(LocalDate.of(2026, 8, 1));
        order.setSalesName("销售员A");
        order.setTotalWeight(new BigDecimal("1000.50"));
        order.setTotalAmount(new BigDecimal("50500.00"));
        order.setStatus("SALES_COMPLETED");
        order.setItems(items);
        return order;
    }

    private SalesOrderItem orderItem(Long id, String materialCode) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setMaterialId(500L);
        item.setMaterialCode(materialCode);
        item.setBrand("品牌A");
        item.setCategory("型钢");
        item.setMaterial("螺纹钢");
        item.setSpec("HRB400");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseId(1L);
        item.setBatchNo("B001");
        item.setQuantity(10);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.250"));
        item.setPiecesPerBundle(100);
        item.setWeightTon(new BigDecimal("12.500"));
        item.setUnitPrice(new BigDecimal("4000.00"));
        item.setAmount(new BigDecimal("50000.00"));
        return item;
    }

    private SalesOutboundItem outboundItem(Long sourceSalesOrderItemId, Integer quantity,
                                           String weightTon, String amount) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(sourceSalesOrderItemId);
        item.setSourceSalesOrderItemId(sourceSalesOrderItemId);
        item.setQuantity(quantity);
        item.setWeightTon(weightTon == null ? null : new BigDecimal(weightTon));
        item.setAmount(amount == null ? null : new BigDecimal(amount));
        return item;
    }

    private SalesOutbound outbound(List<SalesOutboundItem> items) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setStatus("AUDITED");
        outbound.setItems(items);
        return outbound;
    }

    private CandidateCriteria criteria(int page, int size) {
        return new CandidateCriteria(page, size, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    // ---------- findCandidates 极端情况 ----------

    @Test
    void findCandidates_shouldReturnEmptyPageWhenRepositoryEmpty() {
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        CandidatePage page = service.findCandidates(criteria(0, 20));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void findCandidates_shouldMapCandidateSnapshot() {
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(order(1L, "SO001", List.of())), PageRequest.of(0, 20), 1L));

        CandidatePage page = service.findCandidates(criteria(0, 20));

        assertThat(page.content()).hasSize(1);
        CandidateSnapshot snapshot = page.content().get(0);
        assertThat(snapshot.id()).isEqualTo(1L);
        assertThat(snapshot.orderNo()).isEqualTo("SO001");
        assertThat(snapshot.status()).isEqualTo("SALES_COMPLETED");
        assertThat(snapshot.totalAmount()).isEqualByComparingTo("50500.00");
    }

    @Test
    void findCandidates_shouldSkipExcludedResolutionWhenExclusionEmpty() {
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(criteria(0, 20));

        verify(salesOrderRepository, never()).findAllWithItemsBySourceItemIds(any());
    }

    @Test
    void findCandidates_shouldResolveExcludedSourceItemIdsToOrderIds() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(10L, "SO010", List.of())));
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        CandidateCriteria c = new CandidateCriteria(0, 20, null, null, null, null, null,
                null, null, null, null, null, List.of(1L, 2L));
        CandidatePage page = service.findCandidates(c);

        assertThat(page.content()).isEmpty();
        verify(salesOrderRepository).findAllWithItemsBySourceItemIds(any());
    }

    @Test
    void findCandidates_shouldRejectZeroSize() {
        assertThatThrownBy(() -> service.findCandidates(criteria(0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCandidates_shouldRejectNegativeSize() {
        assertThatThrownBy(() -> service.findCandidates(criteria(0, -5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCandidates_shouldRejectNegativePage() {
        assertThatThrownBy(() -> service.findCandidates(criteria(-1, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findCandidates_shouldDefaultSortToIdDescendingWhenDirectionInvalid() {
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(new CandidateCriteria(
                0, 20, null, "weird", null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(salesOrderRepository).findAll(any(Specification.class), captor.capture());
        Sort.Order order = captor.getValue().getSort().getOrderFor("id");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void findCandidates_shouldDefaultSortToIdWhenSortByBlankString() {
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(new CandidateCriteria(
                0, 20, "", null, null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(salesOrderRepository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void findCandidates_shouldHonorAscDirectionIgnoringCase() {
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(new CandidateCriteria(
                1, 10, "deliveryDate", "ASC", null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(salesOrderRepository).findAll(any(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getSort().getOrderFor("deliveryDate").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void findCandidates_shouldRejectNullCriteriaAsKnownResidualRisk() {
        assertThatThrownBy(() -> service.findCandidates(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- findBySourceItemIds 极端情况 ----------

    @Test
    void findBySourceItemIds_shouldReturnEmptyForNullInput() {
        assertThat(service.findBySourceItemIds(null)).isEmpty();
        verifyNoInteractions(salesOrderRepository, salesOutboundRepository);
    }

    @Test
    void findBySourceItemIds_shouldReturnEmptyForEmptyInput() {
        assertThat(service.findBySourceItemIds(List.of())).isEmpty();
        verifyNoInteractions(salesOrderRepository, salesOutboundRepository);
    }

    @Test
    void findBySourceItemIds_shouldFilterNullIds() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of());

        service.findBySourceItemIds(Arrays.asList(11L, null, 12L));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(salesOrderRepository).findAllWithItemsBySourceItemIds(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void findBySourceItemIds_shouldDeduplicateRepeatedIds() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of());

        service.findBySourceItemIds(List.of(11L, 11L, 12L, 12L));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(salesOrderRepository).findAllWithItemsBySourceItemIds(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(11L, 12L);
    }

    @Test
    void findBySourceItemIds_shouldReturnEmptyWhenRepositoryReturnsNone() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any())).thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of());

        assertThat(service.findBySourceItemIds(List.of(999L))).isEmpty();
    }

    @Test
    void findBySourceItemIds_shouldMapOrderItemAndOutboundActual() {
        SalesOrderItem orderItem = orderItem(11L, "M001");
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(1L, "SO001", List.of(orderItem))));
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of(outbound(List.of(outboundItem(11L, 100, "100.00", "5000.00")))));

        List<OrderSnapshot> result = service.findBySourceItemIds(List.of(11L));

        assertThat(result).hasSize(1);
        OrderSnapshot snapshot = result.get(0);
        assertThat(snapshot.orderNo()).isEqualTo("SO001");
        assertThat(snapshot.items()).hasSize(1);
        ItemSnapshot item = snapshot.items().get(0);
        assertThat(item.materialCode()).isEqualTo("M001");
        AuditedOutboundActualSnapshot actual = item.auditedOutboundActual();
        assertThat(actual.quantity()).isEqualTo(100L);
        assertThat(actual.amount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void findBySourceItemIds_shouldHandleMaxSnowflakeId() {
        Long max = Long.MAX_VALUE;
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(max, "SO-MAX", List.of(orderItem(max, "M001")))));
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of());

        List<OrderSnapshot> result = service.findBySourceItemIds(List.of(max));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(max);
        assertThat(result.get(0).items().get(0).id()).isEqualTo(max);
    }

    // ---------- outbound 实际值聚合极端情况 ----------

    @Test
    void findBySourceItemIds_shouldIgnoreOutboundItemWithNullSourceId() {
        SalesOrderItem orderItem = orderItem(11L, "M001");
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(1L, "SO001", List.of(orderItem))));
        // 一个 item 的 sourceSalesOrderItemId 为 null，一个不在请求范围内
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of(outbound(List.of(
                        outboundItem(11L, 50, "50.00", "2500.00"),
                        outboundItem(null, 30, "30.00", "1500.00"),
                        outboundItem(999L, 20, "20.00", "1000.00")))));

        List<OrderSnapshot> result = service.findBySourceItemIds(List.of(11L));

        AuditedOutboundActualSnapshot actual = result.get(0).items().get(0).auditedOutboundActual();
        assertThat(actual.quantity()).isEqualTo(50L); // 仅 sourceId 命中且非 null 的项参与
    }

    @Test
    void findBySourceItemIds_shouldMergeOutboundActualsAcrossItems() {
        SalesOrderItem orderItem = orderItem(11L, "M001");
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(1L, "SO001", List.of(orderItem))));
        // 同一 sourceSalesOrderItemId 的两条出库明细 → mergeActuals 聚合
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of(outbound(List.of(
                        outboundItem(11L, 100, "100.00", "5000.00"),
                        outboundItem(11L, 200, "200.00", "10000.00")))));

        List<OrderSnapshot> result = service.findBySourceItemIds(List.of(11L));

        AuditedOutboundActualSnapshot actual = result.get(0).items().get(0).auditedOutboundActual();
        assertThat(actual.quantity()).isEqualTo(300L);
        assertThat(actual.weightTon()).isEqualByComparingTo("300.00");
        assertThat(actual.amount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void findBySourceItemIds_shouldTreatNullQuantityAndAmountAsZero() {
        SalesOrderItem orderItem = orderItem(11L, "M001");
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(1L, "SO001", List.of(orderItem))));
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of(outbound(List.of(
                        outboundItem(11L, null, null, null)))));

        List<OrderSnapshot> result = service.findBySourceItemIds(List.of(11L));

        AuditedOutboundActualSnapshot actual = result.get(0).items().get(0).auditedOutboundActual();
        assertThat(actual.quantity()).isZero();
        assertThat(actual.weightTon()).isEqualByComparingTo("0");
        assertThat(actual.amount()).isEqualByComparingTo("0");
    }

    @Test
    void findBySourceItemIds_shouldMergeLargeQuantityPrecisely() {
        SalesOrderItem orderItem = orderItem(11L, "M001");
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(1L, "SO001", List.of(orderItem))));
        // quantity 为 Integer，最大 int×2 在 long 内安全（Math.addExact 不会溢出），验证精确累加
        when(salesOutboundRepository.findAllWithItemsByStatusAndSourceSalesOrderItemIds(anyString(), any()))
                .thenReturn(List.of(outbound(List.of(
                        outboundItem(11L, Integer.MAX_VALUE, "1.00", "1.00"),
                        outboundItem(11L, Integer.MAX_VALUE, "1.00", "1.00")))));

        List<OrderSnapshot> result = service.findBySourceItemIds(List.of(11L));

        assertThat(result.get(0).items().get(0).auditedOutboundActual().quantity())
                .isEqualTo(2L * Integer.MAX_VALUE);
    }

    // ---------- Specification 执行级 ----------

    @Test
    void findCandidates_shouldExecuteSpecificationWithExcludedIdsBranch() {
        when(salesOrderRepository.findAllWithItemsBySourceItemIds(any()))
                .thenReturn(List.of(order(10L, "SO010", List.of())));
        ArgumentCaptor<Specification<SalesOrder>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        CandidateCriteria c = new CandidateCriteria(0, 20, null, null, "钢材", 1L, 2L, "客户",
                "项目", 3L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), List.of(1L, 2L));
        service.findCandidates(c);

        verify(salesOrderRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertThatCode(() -> executeSpec(specCaptor.getValue())).doesNotThrowAnyException();
        verify(criteriaBuilder).not(any(Expression.class));
        verify(criteriaBuilder, atLeastOnce()).like(any(), anyString(), anyChar());
    }

    @Test
    void findCandidates_shouldExecuteSpecificationWithEmptyExclusion() {
        ArgumentCaptor<Specification<SalesOrder>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0L));

        service.findCandidates(criteria(0, 20));

        verify(salesOrderRepository).findAll(specCaptor.capture(), any(Pageable.class));
        assertThatCode(() -> executeSpec(specCaptor.getValue())).doesNotThrowAnyException();
        verify(criteriaBuilder, never()).not(any(Expression.class));
        verify(criteriaBuilder, atLeastOnce()).conjunction();
    }

    @Mock(lenient = true)
    private CriteriaBuilder criteriaBuilder;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeSpec(Specification<SalesOrder> spec) {
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        Root<SalesOrder> root = mock(Root.class);
        Path<Object> idPath = mock(Path.class);
        when(criteriaBuilder.conjunction()).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.isFalse(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.like(any(), anyString(), anyChar())).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.equal(any(Expression.class), any())).thenReturn(mock(Predicate.class));
        when(criteriaBuilder.<LocalDate>greaterThanOrEqualTo(any(Expression.class), any(LocalDate.class)))
                .thenReturn(mock(Predicate.class));
        when(criteriaBuilder.<LocalDate>lessThanOrEqualTo(any(Expression.class), any(LocalDate.class)))
                .thenReturn(mock(Predicate.class));
        when(criteriaBuilder.not(any(Expression.class))).thenReturn(mock(Predicate.class));
        when(root.get(anyString())).thenReturn(mock(Path.class));
        lenient().when(root.get("id")).thenReturn(idPath);
        lenient().when(idPath.in(any(Collection.class))).thenReturn(mock(Predicate.class));
        spec.toPredicate(root, query, criteriaBuilder);
    }
}
