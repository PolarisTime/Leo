package com.leo.erp.finance.invoiceissue.service;

import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssueItem;
import com.leo.erp.finance.invoiceissue.repository.InvoiceIssueRepository;
import com.leo.erp.finance.invoiceissue.mapper.InvoiceIssueMapper;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueItemRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueRequest;
import com.leo.erp.finance.invoiceissue.web.dto.InvoiceIssueResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceIssueServiceTest {

    @Mock
    private InvoiceIssueRepository repository;

    @Mock
    private SalesOrderItemQueryService salesOrderItemQueryService;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private InvoiceIssueMapper mapper;

    @Mock
    private CompanySettingService companySettingService;

    @Mock
    private WorkflowTransitionGuard workflowTransitionGuard;

    @Mock
    private SourceAllocationLockService sourceAllocationLockService;

    private InvoiceIssueService service;

    @BeforeEach
    void setUpService() {
        service = service(
                repository,
                idGenerator,
                mapper,
                companySettingService,
                workflowTransitionGuard,
                salesOrderItemQueryService,
                sourceAllocationLockService
        );
    }

    @Test
    void shouldRequireSourceAllocationLockServiceAsConstructorDependency() {
        boolean hasRequiredDependency = java.util.Arrays.stream(InvoiceIssueService.class.getConstructors())
                .anyMatch(constructor -> java.util.Arrays.asList(constructor.getParameterTypes())
                        .contains(SourceAllocationLockService.class));

        assertThat(hasRequiredDependency).isTrue();
    }

    @Test
    void createRejectsOverAllocatedSalesOrderItem() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("2.000"), new BigDecimal("6000.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-NEW")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of(buildSourceAllocationSummary(101L, "1.500", "4500.00")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-NEW",
                101L,
                new BigDecimal("0.600"),
                new BigDecimal("3000.00"),
                new BigDecimal("1800.00")
        )));

        assertEquals("第1行来源销售订单明细可开票吨位不足", exception.getMessage());
        InOrder lockBeforeSummary = inOrder(sourceAllocationLockService, repository);
        lockBeforeSummary.verify(sourceAllocationLockService)
                .lockTradeItemSources(List.of(), List.of(), List.of(101L));
        lockBeforeSummary.verify(repository)
                .summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class));
    }

    @Test
    void createRecalculatesAmountFromRoundedWeight() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-ROUND")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(mapper.toResponse(any(InvoiceIssue.class))).thenAnswer(invocation -> {
            InvoiceIssue entity = invocation.getArgument(0);
            return new InvoiceIssueResponse(
                    entity.getId(),
                    entity.getIssueNo(),
                    entity.getInvoiceNo(),
                    entity.getCustomerName(),
                    entity.getProjectName(),
                    entity.getInvoiceDate(),
                    entity.getInvoiceType(),
                    entity.getAmount(),
                    entity.getTaxAmount(),
                    entity.getStatus(),
                    entity.getOperatorName(),
                    entity.getRemark(),
                    List.of()
            );
        });

        ArgumentCaptor<InvoiceIssue> captor = ArgumentCaptor.forClass(InvoiceIssue.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(buildRequest(
                "KP-ROUND",
                101L,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("1000.00")
        ));

        assertEquals(new BigDecimal("1000.00"), captor.getValue().getAmount());
        assertEquals(new BigDecimal("130.00"), captor.getValue().getTaxAmount());
    }

    @Test
    void createRejectsMissingSourceSalesOrderItemId() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-MISSING")).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-MISSING",
                null,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("1000.01")
        )));

        assertEquals("第1行来源销售订单明细不能为空", exception.getMessage());
    }

    @Test
    void createRejectsDeclaredAmountMismatch() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("2.000"), new BigDecimal("6666.66"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-AMOUNT")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-AMOUNT",
                101L,
                new BigDecimal("0.300"),
                new BigDecimal("3333.33"),
                new BigDecimal("999.99")
        )));

        assertEquals("开票与明细计算结果不一致", exception.getMessage());
    }

    @Test
    void createRejectsDuplicateIssueNo() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-DUP")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-DUP", 101L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("开票单号已存在", exception.getMessage());
    }

    @Test
    void createRejectsSourceSalesOrderItemNotFound() {
        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-NOTFOUND")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-NOTFOUND", 999L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单明细不存在", exception.getMessage());
    }

    @Test
    void createRejectsUnauditedSourceSalesOrder() {
        SalesOrderItem sourceItem = buildSalesOrderItem(102L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));
        sourceItem.getSalesOrder().setStatus(StatusConstants.DRAFT);

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-DRAFT-SO")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-DRAFT-SO", 102L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单未审核，不能开票", exception.getMessage());
    }

    @Test
    void createRejectsSourceSalesOrderCustomerMismatch() {
        SalesOrderItem sourceItem = buildSalesOrderItem(103L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));
        sourceItem.getSalesOrder().setCustomerName("客户B");

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-CUSTOMER")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-CUSTOMER", 103L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单客户与开票单不一致", exception.getMessage());
    }

    @Test
    void createRejectsSourceSalesOrderProjectMismatch() {
        SalesOrderItem sourceItem = buildSalesOrderItem(105L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));
        sourceItem.getSalesOrder().setProjectName("项目B");

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-PROJECT")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-PROJECT", 105L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单项目与开票单不一致", exception.getMessage());
    }

    @Test
    void createRejectsSourceSalesOrderWithBlankOrderNo() {
        SalesOrderItem sourceItem = new SalesOrderItem();
        sourceItem.setId(102L);
        SalesOrder order = new SalesOrder();
        order.setId(1102L);
        order.setOrderNo("  ");
        sourceItem.setSalesOrder(order);
        sourceItem.setMaterialCode("M-1");
        sourceItem.setBrand("品牌A");
        sourceItem.setCategory("品类A");
        sourceItem.setMaterial("材质A");
        sourceItem.setSpec("规格A");
        sourceItem.setUnit("吨");
        sourceItem.setQuantity(1);
        sourceItem.setQuantityUnit("件");
        sourceItem.setPieceWeightTon(new BigDecimal("0.300"));
        sourceItem.setPiecesPerBundle(1);
        sourceItem.setWeightTon(new BigDecimal("0.300"));
        sourceItem.setUnitPrice(new BigDecimal("3333.33"));
        sourceItem.setAmount(new BigDecimal("1000.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-BLANK")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-BLANK", 102L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单不存在", exception.getMessage());
    }

    @Test
    void createRejectsSourceSalesOrderWithNullOrderNo() {
        SalesOrderItem sourceItem = new SalesOrderItem();
        sourceItem.setId(103L);
        SalesOrder order = new SalesOrder();
        order.setId(1103L);
        order.setOrderNo(null);
        sourceItem.setSalesOrder(order);
        sourceItem.setMaterialCode("M-1");
        sourceItem.setBrand("品牌A");
        sourceItem.setCategory("品类A");
        sourceItem.setMaterial("材质A");
        sourceItem.setSpec("规格A");
        sourceItem.setUnit("吨");
        sourceItem.setQuantity(1);
        sourceItem.setQuantityUnit("件");
        sourceItem.setPieceWeightTon(new BigDecimal("0.300"));
        sourceItem.setPiecesPerBundle(1);
        sourceItem.setWeightTon(new BigDecimal("0.300"));
        sourceItem.setUnitPrice(new BigDecimal("3333.33"));
        sourceItem.setAmount(new BigDecimal("1000.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-NULL")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-NULL", 103L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单不存在", exception.getMessage());
    }

    @Test
    void createRejectsSourceSalesOrderItemAmountExceeded() {
        SalesOrderItem sourceItem = buildSalesOrderItem(104L, "M-1", new BigDecimal("1.100"), new BigDecimal("6000.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-AMT-EXCEED")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of(buildSourceAllocationSummary(104L, "0.000", "5500.00")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(buildRequest(
                "KP-AMT-EXCEED", 104L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        )));

        assertEquals("第1行来源销售订单明细可开票金额不足", exception.getMessage());
    }

    @Test
    void updateRejectsDuplicateIssueNoWhenChanged() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setIssueNo("KP-OLD");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(repository.save(any(InvoiceIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceIssue.class))).thenAnswer(invocation -> {
            InvoiceIssue entity = invocation.getArgument(0);
            return new InvoiceIssueResponse(
                    entity.getId(), entity.getIssueNo(), entity.getInvoiceNo(),
                    entity.getCustomerName(), entity.getProjectName(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceIssueRequest request = buildRequest(
                "KP-DUP", 101L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        );

        service.update(1L, request);

        assertThat(existing.getIssueNo()).isEqualTo("KP-OLD");
    }

    @Test
    void validateUpdateRejectsDuplicateIssueNoWhenChanged() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setIssueNo("KP-OLD");
        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-DUP")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.validateUpdate(
                existing,
                buildRequest("KP-DUP", 101L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00"))
        ));

        assertEquals("开票单号已存在", exception.getMessage());
    }

    @Test
    void validateUpdateAllowsChangedUniqueIssueNo() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setIssueNo("KP-OLD");
        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-NEW")).thenReturn(false);

        service.validateUpdate(
                existing,
                buildRequest("KP-NEW", 101L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00"))
        );
    }

    @Test
    void updateAllowsSameIssueNo() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setIssueNo("KP-SAME");
        existing.setInvoiceNo("INV-001");
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(BigDecimal.ZERO);
        existing.setTaxAmount(BigDecimal.ZERO);
        existing.setStatus("草稿");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);
        InvoiceIssueItem existingItem = new InvoiceIssueItem();
        existingItem.setId(100L);
        existingItem.setSourceSalesOrderItemId(100L);
        existing.setItems(new ArrayList<>(List.of(existingItem)));

        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L);
        when(repository.save(any(InvoiceIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceIssue.class))).thenAnswer(invocation -> {
            InvoiceIssue entity = invocation.getArgument(0);
            return new InvoiceIssueResponse(
                    entity.getId(), entity.getIssueNo(), entity.getInvoiceNo(),
                    entity.getCustomerName(), entity.getProjectName(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceIssueRequest request = buildRequest(
                "KP-SAME", 101L, new BigDecimal("0.300"), new BigDecimal("3333.33"), new BigDecimal("1000.00")
        );

        InvoiceIssueResponse result = service.update(1L, request);

        assertThat(result).isNotNull();
        assertThat(result.issueNo()).isEqualTo("KP-SAME");
        InOrder lockBeforeSummary = inOrder(sourceAllocationLockService, repository);
        lockBeforeSummary.verify(sourceAllocationLockService)
                .lockTradeItemSources(List.of(), List.of(), List.of(100L, 101L));
        lockBeforeSummary.verify(repository)
                .summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong());
    }

    @Test
    void shouldReturnDetailForExistingInvoiceIssue() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setIssueNo("KP-001");
        existing.setInvoiceNo("INV-001");
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setStatus("草稿");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(mapper.toResponse(existing)).thenReturn(
                new InvoiceIssueResponse(1L, "KP-001", "INV-001", "客户A", "项目A",
                        LocalDate.of(2026, 4, 26), "增值税专票", new BigDecimal("1000.00"),
                        new BigDecimal("130.00"), "草稿", "财务A", null, null)
        );

        InvoiceIssueResponse result = service.detail(1L);

        assertThat(result).isNotNull();
        assertThat(result.issueNo()).isEqualTo("KP-001");
    }

    @Test
    void findVisibleEntityShouldUseRepositoryFindById() {
        InvoiceIssue deleted = new InvoiceIssue();
        deleted.setId(7L);
        deleted.setIssueNo("KP-DELETED");
        deleted.setDeletedFlag(true);
        when(repository.findById(7L)).thenReturn(Optional.of(deleted));

        Optional<InvoiceIssue> result = service.findVisibleEntity(7L);

        assertThat(result).containsSame(deleted);
    }

    @Test
    void shouldReturnPageResults() {
        InvoiceIssue issue = new InvoiceIssue();
        issue.setId(1L);
        issue.setIssueNo("KP-001");
        Page<InvoiceIssue> page = new PageImpl<>(List.of(issue));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toResponse(issue)).thenReturn(
                new InvoiceIssueResponse(1L, "KP-001", "INV-001", "客户A", "项目A",
                        LocalDate.of(2026, 4, 26), "增值税专票", BigDecimal.ZERO,
                        BigDecimal.ZERO, "草稿", "财务A", null, null)
        );

        Page<InvoiceIssueResponse> result = service.page(
                new com.leo.erp.common.api.PageQuery(0, 10, "id", "desc"),
                new com.leo.erp.common.api.PageFilter(null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null)
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void shouldReturnSearchResults() {
        InvoiceIssue issue = new InvoiceIssue();
        issue.setId(1L);
        issue.setIssueNo("KP-001");
        Page<InvoiceIssue> page = new PageImpl<>(List.of(issue));
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(mapper.toResponse(issue)).thenReturn(
                new InvoiceIssueResponse(1L, "KP-001", "INV-001", "客户A", "项目A",
                        LocalDate.of(2026, 4, 26), "增值税专票", BigDecimal.ZERO,
                        BigDecimal.ZERO, "草稿", "财务A", null, null)
        );

        List<InvoiceIssueResponse> results = service.search("KP", 10);

        assertThat(results).isNotNull();
    }

    @Test
    void shouldAllowValidStatusTransitionDraftToIssued() {
        SalesOrderItem sourceItem = buildSalesOrderItem(101L, "M-1", new BigDecimal("2.000"), new BigDecimal("6000.00"));

        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setIssueNo("KP-001");
        existing.setInvoiceNo("INV-001");
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setStatus("草稿");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);

        InvoiceIssueItem item = new InvoiceIssueItem();
        item.setId(100L);
        item.setLineNo(1);
        item.setSourceSalesOrderItemId(101L);
        item.setMaterialCode("M-1");
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setUnit("吨");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.300"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("0.300"));
        item.setUnitPrice(new BigDecimal("3333.33"));
        item.setAmount(new BigDecimal("1000.00"));
        existing.setItems(new ArrayList<>(List.of(item)));

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong()))
                .thenReturn(List.of());
        when(repository.save(any(InvoiceIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceIssue.class))).thenReturn(
                new InvoiceIssueResponse(1L, "KP-001", "INV-001", "客户A", "项目A",
                        LocalDate.of(2026, 4, 26), "增值税专票", new BigDecimal("1000.00"),
                        new BigDecimal("130.00"), "已开票", "财务A", null, null)
        );

        InvoiceIssueResponse result = service.updateStatus(1L, "已开票");

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo("已开票");
        InOrder lockBeforeSummary = inOrder(sourceAllocationLockService, repository);
        lockBeforeSummary.verify(sourceAllocationLockService)
                .lockTradeItemSources(List.of(), List.of(), List.of(101L));
        lockBeforeSummary.verify(repository)
                .summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), anyLong());
    }

    @Test
    void shouldRejectInvalidStatusTransition() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setStatus("草稿");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.updateStatus(1L, "已审核"));
    }

    @Test
    void shouldHandleBeforeStatusUpdateForNonIssuedStatus() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setIssueNo("KP-001");
        existing.setInvoiceNo("INV-001");
        existing.setCustomerName("客户A");
        existing.setProjectName("项目A");
        existing.setInvoiceDate(LocalDate.of(2026, 4, 26));
        existing.setInvoiceType("增值税专票");
        existing.setAmount(new BigDecimal("1000.00"));
        existing.setTaxAmount(new BigDecimal("130.00"));
        existing.setStatus("已开票");
        existing.setOperatorName("财务A");
        existing.setDeletedFlag(false);
        existing.setItems(new ArrayList<>());

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(InvoiceIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceIssue.class))).thenReturn(
                new InvoiceIssueResponse(1L, "KP-001", "INV-001", "客户A", "项目A",
                        LocalDate.of(2026, 4, 26), "增值税专票", new BigDecimal("1000.00"),
                        new BigDecimal("130.00"), "草稿", "财务A", null, null)
        );

        InvoiceIssueResponse result = service.updateStatus(1L, "草稿");

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDeleteSuccessfully() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setStatus("草稿");
        existing.setDeletedFlag(false);
        InvoiceIssueItem item = new InvoiceIssueItem();
        item.setSourceSalesOrderItemId(101L);
        existing.setItems(new ArrayList<>(List.of(item)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(InvoiceIssue.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);

        InOrder lockBeforeMutation = inOrder(sourceAllocationLockService, repository);
        lockBeforeMutation.verify(sourceAllocationLockService)
                .lockTradeItemSources(List.of(), List.of(), List.of(101L));
        lockBeforeMutation.verify(repository).save(any(InvoiceIssue.class));
        assertThat(existing.isDeletedFlag()).isTrue();
    }

    @Test
    void shouldRejectDeleteWhenStatusIsProtected() {
        InvoiceIssue existing = new InvoiceIssue();
        existing.setId(1L);
        existing.setStatus(StatusConstants.AUDITED);
        existing.setDeletedFlag(false);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.delete(1L));
    }

    @Test
    void shouldRejectWhenEntityNotFound() {
        when(repository.findByIdAndDeletedFlagFalse(999L)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.detail(999L));
    }

    @Test
    void shouldResolveSourceOrderFromMultipleItems() {
        SalesOrderItem sourceItem1 = buildSalesOrderItem(101L, "M-1", new BigDecimal("0.300"), new BigDecimal("1000.00"));
        SalesOrderItem sourceItem2 = buildSalesOrderItem(102L, "M-2", new BigDecimal("0.300"), new BigDecimal("900.00"));

        when(repository.existsByIssueNoAndDeletedFlagFalse("KP-MULTI")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(anyCollection())).thenReturn(List.of(sourceItem1, sourceItem2));
        when(repository.summarizeAllocatedBySourceSalesOrderItemIds(anyCollection(), nullable(Long.class)))
                .thenReturn(List.of());
        when(companySettingService.resolveCurrentTaxRate()).thenReturn(new BigDecimal("0.13"));
        when(idGenerator.nextId()).thenReturn(1L, 2L, 3L);
        when(repository.save(any(InvoiceIssue.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any(InvoiceIssue.class))).thenAnswer(invocation -> {
            InvoiceIssue entity = invocation.getArgument(0);
            return new InvoiceIssueResponse(
                    entity.getId(), entity.getIssueNo(), entity.getInvoiceNo(),
                    entity.getCustomerName(), entity.getProjectName(), entity.getInvoiceDate(),
                    entity.getInvoiceType(), entity.getAmount(), entity.getTaxAmount(),
                    entity.getStatus(), entity.getOperatorName(), entity.getRemark(), List.of()
            );
        });

        InvoiceIssueRequest request = new InvoiceIssueRequest(
                "KP-MULTI", "INV-001", "客户A", "项目A",
                LocalDate.of(2026, 4, 26), "增值税专票",
                new BigDecimal("1900.00"), BigDecimal.ZERO,
                "草稿", "财务A", null,
                List.of(
                        new InvoiceIssueItemRequest(
                                "SO-001", 101L, "M-1", "品牌A", "品类A", "材质A", "规格A",
                                null, "吨", "仓库A", null, 1, "件",
                                new BigDecimal("0.300"), 1, new BigDecimal("0.300"),
                                new BigDecimal("3333.33"), new BigDecimal("1000.00")
                        ),
                        new InvoiceIssueItemRequest(
                                "SO-001", 102L, "M-2", "品牌A", "品类A", "材质A", "规格A",
                                null, "吨", "仓库A", null, 1, "件",
                                new BigDecimal("0.300"), 1, new BigDecimal("0.300"),
                                new BigDecimal("3000.00"), new BigDecimal("900.00")
                        )
                )
        );

        InvoiceIssueResponse result = service.create(request);

        assertThat(result).isNotNull();
    }

    private InvoiceIssueRepository.SourceAllocationSummary buildSourceAllocationSummary(Long itemId, String weightTon, String amount) {
        return new InvoiceIssueRepository.SourceAllocationSummary() {
            @Override
            public Long getSourceSalesOrderItemId() {
                return itemId;
            }

            @Override
            public Long getTotalQuantity() {
                return 0L;
            }

            @Override
            public BigDecimal getTotalWeightTon() {
                return new BigDecimal(weightTon);
            }

            @Override
            public BigDecimal getTotalAmount() {
                return new BigDecimal(amount);
            }
        };
    }

    private SalesOrderItem buildSalesOrderItem(Long id, String materialCode, BigDecimal weightTon, BigDecimal amount) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        SalesOrder order = new SalesOrder();
        order.setId(1000L + id);
        order.setOrderNo("SO-001");
        order.setStatus(StatusConstants.AUDITED);
        order.setCustomerName("客户A");
        order.setProjectName("项目A");
        item.setSalesOrder(order);
        item.setMaterialCode(materialCode);
        item.setBrand("品牌A");
        item.setCategory("品类A");
        item.setMaterial("材质A");
        item.setSpec("规格A");
        item.setUnit("吨");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(weightTon);
        item.setPiecesPerBundle(1);
        item.setWeightTon(weightTon);
        item.setUnitPrice(amount.divide(weightTon, 2, RoundingMode.HALF_UP));
        item.setAmount(amount);
        return item;
    }

    private InvoiceIssueService service(InvoiceIssueRepository repository,
                                        SnowflakeIdGenerator idGenerator,
                                        InvoiceIssueMapper mapper,
                                        CompanySettingService companySettingService,
                                        WorkflowTransitionGuard workflowTransitionGuard,
                                        SalesOrderItemQueryService salesOrderItemQueryService,
                                        SourceAllocationLockService sourceAllocationLockService) {
        InvoiceIssueSourceService sourceService = new InvoiceIssueSourceService(repository, salesOrderItemQueryService);
        com.leo.erp.finance.common.service.InvoiceAmountCalculator amountCalculator =
                new com.leo.erp.finance.common.service.InvoiceAmountCalculator(
                        new com.leo.erp.finance.common.service.TaxAmountCalculator(companySettingService)
                );
        return new InvoiceIssueService(
                repository,
                idGenerator,
                sourceAllocationLockService,
                new InvoiceIssueApplyService(workflowTransitionGuard, sourceService, amountCalculator),
                sourceService,
                new InvoiceIssueResponseAssembler(mapper)
        );
    }

    private InvoiceIssueRequest buildRequest(String issueNo,
                                             Long sourceSalesOrderItemId,
                                             BigDecimal weightTon,
                                             BigDecimal unitPrice,
                                             BigDecimal amount) {
        return new InvoiceIssueRequest(
                issueNo,
                "INV-001",
                "客户A",
                "项目A",
                LocalDate.of(2026, 4, 26),
                "增值税专票",
                amount,
                BigDecimal.ZERO,
                "草稿",
                "财务A",
                null,
                List.of(new InvoiceIssueItemRequest(
                        "SO-001",
                        sourceSalesOrderItemId,
                        "M-1",
                        "品牌A",
                        "品类A",
                        "材质A",
                        "规格A",
                        null,
                        "吨",
                        "仓库A",
                        null,
                        1,
                        "件",
                        weightTon,
                        1,
                        weightTon,
                        unitPrice,
                        amount
                ))
        );
    }
}
