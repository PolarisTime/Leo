package com.leo.erp.report.pendinginvoicereceipt.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.finance.invoicereceipt.repository.InvoiceReceiptRepository;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.report.pendinginvoicereceipt.web.dto.PendingInvoiceReceiptReportResponse;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingInvoiceReceiptReportServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private InvoiceReceiptRepository invoiceReceiptRepository;

    @InjectMocks
    private PendingInvoiceReceiptReportService service;

    @Test
    void pageQueriesProgressOnlyForAccessiblePurchaseOrderItems() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(201L, "2.000", "200.00"),
                purchaseOrderItem(202L, "1.000", "80.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of(summary(201L, "1.250", "100.00")));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "orderNo", "asc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().getFirst().pendingInvoiceWeightTon()).isEqualByComparingTo("0.750");
        assertThat(page.getContent().getFirst().pendingInvoiceAmount()).isEqualByComparingTo("100.00");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> sourceItemIds = ArgumentCaptor.forClass(Collection.class);
        verify(invoiceReceiptRepository).summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds.capture(), isNull());
        assertThat(sourceItemIds.getValue()).containsExactly(201L, 202L);
        verify(purchaseOrderRepository, never()).findAllByDeletedFlagFalse();
        verify(purchaseOrderRepository, never()).findAll(anySpecification(), any(Sort.class));
        verify(invoiceReceiptRepository, never()).findAllByDeletedFlagFalse();
    }

    @Test
    void pageUsesBoundedPurchaseOrderPageQuery() {
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.page(
                PageQuery.of(2, 50, "orderDate", "asc"),
                null,
                null,
                null,
                null
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(purchaseOrderRepository).findAll(anySpecification(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
        verify(purchaseOrderRepository, never()).findAll(anySpecification(), any(Sort.class));
    }

    @Test
    void pageCapsPurchaseOrderCandidateQuery() {
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.page(
                PageQuery.of(20, 200, null, null),
                null,
                null,
                null,
                null
        );

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(purchaseOrderRepository).findAll(anySpecification(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1_000);
        verify(purchaseOrderRepository, never()).findAll(anySpecification(), any(Sort.class));
    }

    @Test
    void pageSkipsProgressQueryWhenNoAccessiblePurchaseOrderItemsExist() {
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isZero();
        verify(invoiceReceiptRepository, never()).summarizeAllocatedBySourcePurchaseOrderItemIds(any(), any());
    }

    @Test
    void pageSkipsFullyInvoicedItems() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(301L, "2.000", "200.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of(summary(301L, "2.000", "200.00")));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void pageKeepsItemWhenWeightFullyInvoicedButAmountStillPending() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(302L, "2.000", "200.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of(summary(302L, "2.000", "50.00")));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        PendingInvoiceReceiptReportResponse row = page.getContent().getFirst();
        assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void pageFiltersByKeywordMatchingOrderNo() {
        PurchaseOrder orderA = purchaseOrder(
                purchaseOrderItem(401L, "1.000", "100.00")
        );
        orderA.setOrderNo("PO-MATCH");
        PurchaseOrder orderB = purchaseOrder(
                purchaseOrderItem(402L, "1.000", "100.00")
        );
        orderB.setOrderNo("PO-NOMATCH");
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orderA, orderB)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "PO-MATCH",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().orderNo()).isEqualTo("PO-MATCH");
    }

    @Test
    void pageFiltersByKeywordMatchingMaterialCode() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(501L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "m-501",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().materialCode()).isEqualTo("M-501");
    }

    @Test
    void pageSortsBySupplierNameAscending() {
        PurchaseOrder orderB = purchaseOrder(purchaseOrderItem(601L, "1.000", "100.00"));
        orderB.setSupplierName("乙供应商");
        PurchaseOrder orderA = purchaseOrder(purchaseOrderItem(602L, "1.000", "100.00"));
        orderA.setSupplierName("甲供应商");
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orderB, orderA)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "supplierName", "asc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().getFirst().supplierName()).isEqualTo("乙供应商");
        assertThat(page.getContent().get(1).supplierName()).isEqualTo("甲供应商");
    }

    @Test
    void pageSortsByPendingInvoiceWeightTonDescending() {
        PurchaseOrder orderLight = purchaseOrder(purchaseOrderItem(701L, "0.500", "50.00"));
        PurchaseOrder orderHeavy = purchaseOrder(purchaseOrderItem(702L, "3.000", "300.00"));
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orderLight, orderHeavy)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "pendingInvoiceWeightTon", "desc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().getFirst().pendingInvoiceWeightTon()).isEqualByComparingTo("3.000");
    }

    @Test
    void pageReturnsZeroPendingWhenFullyReceived() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(801L, "2.000", "200.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of(summary(801L, "2.000", "200.00")));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void pageFiltersByKeywordMatchingSupplierName() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(901L, "1.000", "100.00")
        );
        order.setSupplierName("特殊供应商");
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "特殊供应商",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().supplierName()).isEqualTo("特殊供应商");
    }

    @Test
    void pageFiltersByKeywordMatchingBrand() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(902L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "品牌A",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pageFiltersByKeywordMatchingMaterial() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(903L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "材质A",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pageFiltersByKeywordMatchingCategory() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(904L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "品类A",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pageFiltersByKeywordMatchingSpec() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(905L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "规格A",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pageFiltersByKeywordMatchingInvoiceTitle() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(906L, "1.000", "100.00")
        );
        order.setSupplierName("发票抬头匹配");
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "发票抬头匹配",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pageSkipsWhenKeywordDoesNotMatch() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(907L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "不存在的关键字",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void pageReturnsEmptyWhenKeywordDoesNotMatchNullSearchableFields() {
        PurchaseOrderItem item = purchaseOrderItem(909L, "1.000", "100.00");
        item.setMaterialCode(null);
        item.setBrand(null);
        item.setMaterial(null);
        item.setCategory(null);
        item.setSpec(null);
        PurchaseOrder order = purchaseOrder(item);
        order.setOrderNo(null);
        order.setSupplierName(null);
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "不存在的关键字",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void matchesKeywordAllowsInvoiceTitleOnlyMatch() {
        PendingInvoiceReceiptReportResponse row = new PendingInvoiceReceiptReportResponse(
                1L,
                "PO-001",
                "供应商A",
                "专用发票抬头",
                LocalDateTime.of(2026, 4, 26, 0, 0),
                "M-001",
                "品牌A",
                "材质A",
                "品类A",
                "规格A",
                "9m",
                1,
                "件",
                new BigDecimal("1.000"),
                BigDecimal.ZERO,
                new BigDecimal("1.000"),
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                new BigDecimal("100.00"),
                "未收票"
        );

        Boolean matches = ReflectionTestUtils.invokeMethod(service, "matchesKeyword", row, "专用发票");

        assertThat(matches).isTrue();
    }

    @Test
    void pageTreatsBlankKeywordAsNull() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(908L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                "   ",
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void pageSortsByOrderDateAscending() {
        PurchaseOrder orderA = purchaseOrder(purchaseOrderItem(1001L, "1.000", "100.00"));
        orderA.setOrderDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        PurchaseOrder orderB = purchaseOrder(purchaseOrderItem(1002L, "1.000", "100.00"));
        orderB.setOrderDate(LocalDateTime.of(2026, 6, 1, 0, 0));
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orderA, orderB)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "orderDate", "asc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void pageSortsByMaterialCodeDescending() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(1003L, "1.000", "100.00"),
                purchaseOrderItem(1004L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "materialCode", "desc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void pageSortsByPendingInvoiceAmountAscending() {
        PurchaseOrder orderLight = purchaseOrder(purchaseOrderItem(1005L, "0.500", "50.00"));
        PurchaseOrder orderHeavy = purchaseOrder(purchaseOrderItem(1006L, "3.000", "300.00"));
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orderLight, orderHeavy)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, "pendingInvoiceAmount", "asc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().getFirst().pendingInvoiceAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void pageSortsByDefaultOrderNoWhenSortByIsNull() {
        PurchaseOrder orderA = purchaseOrder(purchaseOrderItem(1007L, "1.000", "100.00"));
        orderA.setOrderNo("PO-BBB");
        PurchaseOrder orderB = purchaseOrder(purchaseOrderItem(1008L, "1.000", "100.00"));
        orderB.setOrderNo("PO-AAA");
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(orderA, orderB)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, "asc"),
                null,
                null,
                null,
                null
        );

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void pageAppliesPagination() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(1101L, "1.000", "100.00"),
                purchaseOrderItem(1102L, "1.000", "100.00"),
                purchaseOrderItem(1103L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 2, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void pageReturnsSecondPage() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(1201L, "1.000", "100.00"),
                purchaseOrderItem(1202L, "1.000", "100.00"),
                purchaseOrderItem(1203L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(1, 2, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void pageFiltersNullItemIdsFromProgressQuery() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(null, "1.000", "100.00"),
                purchaseOrderItem(1300L, "2.000", "200.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of());

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> sourceItemIds = ArgumentCaptor.forClass(Collection.class);
        verify(invoiceReceiptRepository).summarizeAllocatedBySourcePurchaseOrderItemIds(sourceItemIds.capture(), isNull());
        assertThat(sourceItemIds.getValue()).containsExactly(1300L);
    }

    @Test
    void pageTreatsNullProgressTotalsAsZero() {
        PurchaseOrder order = purchaseOrder(
                purchaseOrderItem(1301L, "1.000", "100.00")
        );
        when(purchaseOrderRepository.findAll(anySpecification(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(order)));
        when(invoiceReceiptRepository.summarizeAllocatedBySourcePurchaseOrderItemIds(any(), isNull()))
                .thenReturn(List.of(summary(1301L, null, null)));

        Page<PendingInvoiceReceiptReportResponse> page = service.page(
                PageQuery.of(0, 20, null, null),
                null,
                null,
                null,
                null
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        PendingInvoiceReceiptReportResponse row = page.getContent().getFirst();
        assertThat(row.receivedInvoiceWeightTon()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.receivedInvoiceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row.pendingInvoiceWeightTon()).isEqualByComparingTo("1.000");
        assertThat(row.pendingInvoiceAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void supplierNameSpecUsesConjunctionForMissingSupplierName() {
        assertConjunction(supplierNameSpec(null));
        assertConjunction(supplierNameSpec("   "));
    }

    @Test
    void supplierNameSpecTrimsAndMatchesSupplierName() {
        Root<PurchaseOrder> root = mockRoot();
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        var supplierNamePath = mock(jakarta.persistence.criteria.Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("supplierName")).thenReturn(supplierNamePath);
        when(criteriaBuilder.equal(supplierNamePath, "供应商A")).thenReturn(predicate);

        Predicate result = supplierNameSpec("  供应商A  ").toPredicate(root, criteriaQuery, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(root).get("supplierName");
        verify(criteriaBuilder).equal(supplierNamePath, "供应商A");
    }

    @Test
    void dateSpecsUseConjunctionForMissingDates() {
        assertConjunction(startDateSpec(null));
        assertConjunction(endDateSpec(null));
    }

    @Test
    void startDateSpecUsesStartOfDayBoundary() {
        Root<PurchaseOrder> root = mockRoot();
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        var orderDatePath = mock(jakarta.persistence.criteria.Path.class);
        Predicate predicate = mock(Predicate.class);
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDateTime startInclusive = LocalDateTime.of(2026, 1, 1, 0, 0);
        when(root.get("orderDate")).thenReturn(orderDatePath);
        when(criteriaBuilder.greaterThanOrEqualTo(orderDatePath, startInclusive)).thenReturn(predicate);

        Predicate result = startDateSpec(startDate).toPredicate(root, criteriaQuery, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(root).get("orderDate");
        verify(criteriaBuilder).greaterThanOrEqualTo(orderDatePath, startInclusive);
    }

    @Test
    void endDateSpecUsesExclusiveNextDayBoundary() {
        Root<PurchaseOrder> root = mockRoot();
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        var orderDatePath = mock(jakarta.persistence.criteria.Path.class);
        Predicate predicate = mock(Predicate.class);
        LocalDate endDate = LocalDate.of(2026, 6, 30);
        LocalDateTime endExclusive = LocalDateTime.of(2026, 7, 1, 0, 0);
        when(root.get("orderDate")).thenReturn(orderDatePath);
        when(criteriaBuilder.lessThan(orderDatePath, endExclusive)).thenReturn(predicate);

        Predicate result = endDateSpec(endDate).toPredicate(root, criteriaQuery, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(root).get("orderDate");
        verify(criteriaBuilder).lessThan(orderDatePath, endExclusive);
    }

    @SuppressWarnings("unchecked")
    private Specification<PurchaseOrder> anySpecification() {
        return any(Specification.class);
    }

    private PurchaseOrder purchaseOrder(PurchaseOrderItem... items) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(101L);
        order.setOrderNo("PO-001");
        order.setSupplierName("供应商A");
        order.setOrderDate(LocalDateTime.of(2026, 4, 26, 0, 0));
        order.setCreatedBy(1L);
        order.setDeletedFlag(false);
        order.getItems().addAll(List.of(items));
        for (PurchaseOrderItem item : items) {
            item.setPurchaseOrder(order);
        }
        return order;
    }

    private PurchaseOrderItem purchaseOrderItem(Long id, String weightTon, String amount) {
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(id);
        item.setMaterialCode("M-" + id);
        item.setBrand("品牌A");
        item.setMaterial("材质A");
        item.setCategory("品类A");
        item.setSpec("规格A");
        item.setLength("9m");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setWeightTon(new BigDecimal(weightTon));
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setAmount(new BigDecimal(amount));
        return item;
    }

    private InvoiceReceiptRepository.SourceAllocationSummary summary(Long itemId, String weightTon, String amount) {
        return new InvoiceReceiptRepository.SourceAllocationSummary() {
            @Override
            public Long getSourcePurchaseOrderItemId() {
                return itemId;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return weightTon == null ? null : new BigDecimal(weightTon);
            }

            @Override
            public BigDecimal getTotalAmount() {
                return amount == null ? null : new BigDecimal(amount);
            }
        };
    }

    private void assertConjunction(Specification<PurchaseOrder> spec) {
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        Predicate predicate = mock(Predicate.class);
        when(criteriaBuilder.conjunction()).thenReturn(predicate);

        Predicate result = spec.toPredicate(mockRoot(), criteriaQuery, criteriaBuilder);

        assertThat(result).isSameAs(predicate);
        verify(criteriaBuilder).conjunction();
    }

    @SuppressWarnings("unchecked")
    private Root<PurchaseOrder> mockRoot() {
        return mock(Root.class);
    }

    @SuppressWarnings("unchecked")
    private Specification<PurchaseOrder> supplierNameSpec(String supplierName) {
        return (Specification<PurchaseOrder>) ReflectionTestUtils.invokeMethod(
                service,
                "supplierNameSpec",
                (Object) supplierName
        );
    }

    @SuppressWarnings("unchecked")
    private Specification<PurchaseOrder> startDateSpec(LocalDate startDate) {
        return (Specification<PurchaseOrder>) ReflectionTestUtils.invokeMethod(
                service,
                "startDateSpec",
                (Object) startDate
        );
    }

    @SuppressWarnings("unchecked")
    private Specification<PurchaseOrder> endDateSpec(LocalDate endDate) {
        return (Specification<PurchaseOrder>) ReflectionTestUtils.invokeMethod(
                service,
                "endDateSpec",
                (Object) endDate
        );
    }
}
