package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceIssueSourceServiceTest {

    @Test
    void shouldRejectMissingSourceSalesOrderItemId() {
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(
                mock(InvoiceIssueRepository.class),
                mock(SalesOrderItemQueryService.class)
        );
        InvoiceIssue entity = issue();

        assertThatThrownBy(() -> service.applyItems(
                entity,
                List.of(request(null, 1)),
                "客户A",
                "项目A",
                new AtomicLong(100L)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
    }

    @Test
    void shouldRejectMissingSourceSalesOrderItem() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of());
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);

        assertThatThrownBy(() -> service.applyItems(
                issue(),
                List.of(request(1L, 1)),
                "客户A",
                "项目A",
                new AtomicLong(100L)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不存在");
    }

    @Test
    void shouldRejectSourceSalesOrderWithoutOrderNo() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceItem(1L, " ");
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);

        assertThatThrownBy(() -> service.applyItems(
                issue(),
                List.of(request(1L, 1)),
                "客户A",
                "项目A",
                new AtomicLong(100L)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单不存在");
    }

    @Test
    void shouldRejectSourceSalesOrderItemWithoutOrder() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceItem(1L, "SO-001");
        sourceItem.setSalesOrder(null);
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);

        assertThatThrownBy(() -> service.applyItems(
                issue(),
                List.of(request(1L, 1)),
                "客户A",
                "项目A",
                new AtomicLong(100L)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单不存在");
    }

    @Test
    void validateSourceSalesOrderAllocationRejectsMissingSourceId() {
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(
                mock(InvoiceIssueRepository.class),
                mock(SalesOrderItemQueryService.class)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "validateSourceSalesOrderAllocation",
                request(null, 1),
                1,
                null,
                Map.of(),
                Map.of(),
                new java.util.HashMap<>()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不能为空");
    }

    @Test
    void validateSourceSalesOrderAllocationRejectsMissingSourceItem() {
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(
                mock(InvoiceIssueRepository.class),
                mock(SalesOrderItemQueryService.class)
        );

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                service,
                "validateSourceSalesOrderAllocation",
                request(99L, 1),
                1,
                null,
                Map.of(),
                Map.of(),
                new java.util.HashMap<>()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售订单明细不存在");
    }

    @Test
    void shouldMergeSettlementCompanyAndPersistResolvedItems() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem first = sourceItem(1L, "SO-001");
        first.getSalesOrder().setSettlementCompanyId(88L);
        first.getSalesOrder().setSettlementCompanyName("  结算公司A  ");
        SalesOrderItem second = sourceItem(2L, "SO-002");
        second.getSalesOrder().setSettlementCompanyId(88L);
        second.getSalesOrder().setSettlementCompanyName("结算公司A");
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of(first, second));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);
        InvoiceIssue entity = issue();
        AtomicLong ids = new AtomicLong(100L);

        InvoiceIssueSourceService.SourceApplyResult result = service.applyItems(
                entity,
                List.of(request(1L, 1), request(2L, 2)),
                "客户A",
                "项目A",
                ids::incrementAndGet
        );

        assertThat(result.amount()).isEqualByComparingTo("300.00");
        assertThat(result.settlementCompanyId()).isEqualTo(88L);
        assertThat(result.settlementCompanyName()).isEqualTo("结算公司A");
        assertThat(entity.getItems()).extracting(InvoiceIssueItem::getLineNo).containsExactly(1, 2);
        assertThat(entity.getItems()).extracting(InvoiceIssueItem::getSourceNo).containsExactly("SO-001", "SO-002");
        assertThat(entity.getItems()).extracting(InvoiceIssueItem::getInvoiceIssue).containsOnly(entity);
    }

    @Test
    void shouldKeepSettlementCompanyNameWhenIdIsMissing() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceItem(1L, "SO-001");
        sourceItem.getSalesOrder().setSettlementCompanyId(null);
        sourceItem.getSalesOrder().setSettlementCompanyName("  结算主体名称  ");
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);

        InvoiceIssueSourceService.SourceApplyResult result = service.applyItems(
                issue(),
                List.of(request(1L, 1)),
                "客户A",
                "项目A",
                new AtomicLong(100L)::incrementAndGet
        );

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isEqualTo("结算主体名称");
    }

    @Test
    void shouldRejectConflictingSettlementCompany() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem first = sourceItem(1L, "SO-001");
        first.getSalesOrder().setSettlementCompanyId(88L);
        first.getSalesOrder().setSettlementCompanyName("结算公司A");
        SalesOrderItem second = sourceItem(2L, "SO-002");
        second.getSalesOrder().setSettlementCompanyId(99L);
        second.getSalesOrder().setSettlementCompanyName("结算公司B");
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of(first, second));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);

        assertThatThrownBy(() -> service.applyItems(
                issue(),
                List.of(request(1L, 1), request(2L, 1)),
                "客户A",
                "项目A",
                new AtomicLong(100L)::incrementAndGet
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体与开票单不一致");
    }

    @Test
    void shouldValidateExistingItemsAndKeepEmptySettlementCompany() {
        InvoiceIssueRepository repository = mock(InvoiceIssueRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SalesOrderItem sourceItem = sourceItem(1L, "SO-001");
        sourceItem.getSalesOrder().setSettlementCompanyId(null);
        sourceItem.getSalesOrder().setSettlementCompanyName(null);
        when(itemQueryService.findActiveByIdIn(any(Collection.class))).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L))).thenReturn(List.of());
        InvoiceIssueSourceService service = new InvoiceIssueSourceService(repository, itemQueryService);
        InvoiceIssue entity = issue();
        InvoiceIssueItem item = new InvoiceIssueItem();
        item.setId(101L);
        item.setSourceNo("SO-001");
        item.setSourceSalesOrderItemId(1L);
        item.setMaterialCode("M-1");
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setUnit("吨");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal("100.00"));
        entity.getItems().add(item);

        service.validateExistingItemsForIssue(entity);

        assertThat(entity.getSettlementCompanyId()).isNull();
        assertThat(entity.getSettlementCompanyName()).isNull();
        verify(repository).summarizeAllocatedBySourceSalesOrderItemIds(any(Collection.class), eq(10L));
    }

    private InvoiceIssue issue() {
        InvoiceIssue issue = new InvoiceIssue();
        issue.setId(10L);
        issue.setCustomerName("客户A");
        issue.setProjectName("项目A");
        return issue;
    }

    private InvoiceIssueItemRequest request(Long sourceItemId, int quantity) {
        return new InvoiceIssueItemRequest(
                "SO",
                sourceItemId,
                "M-1",
                "品牌A",
                "品类A",
                "材质A",
                "规格A",
                null,
                "吨",
                "仓库A",
                null,
                quantity,
                "件",
                new BigDecimal("1.000"),
                1,
                new BigDecimal(quantity),
                new BigDecimal("100.00"),
                new BigDecimal(quantity * 100)
        );
    }

    private SalesOrderItem sourceItem(Long id, String orderNo) {
        SalesOrder order = new SalesOrder();
        order.setId(1000L + id);
        order.setOrderNo(orderNo);
        order.setStatus(StatusConstants.AUDITED);
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
        item.setMaterialCode("M-1");
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setUnit("吨");
        item.setWarehouseName("仓库A");
        item.setQuantity(10);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("10.000"));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }
}
