package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerStatementServiceTest {

    @Test
    void shouldIgnoreRequestedReceiptAmountWhenNoSettledAllocationExists() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(buildSalesOrderItem()));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement statement = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getCustomerCode(),
                    statement.getCustomerName(),
                    statement.getProjectId(),
                    statement.getProjectName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getSalesAmount(),
                    statement.getReceiptAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        CustomerStatementResponse response = service.create(buildRequest(new BigDecimal("1000.00")));

        assertThat(response.salesAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.receiptAmount()).isEqualByComparingTo("0.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.customerCode()).isEqualTo("C-001");
    }

    @Test
    void shouldResolveCustomerCodeFromMasterDataWhenSourceOrderHasNoCode() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderItem sourceItem = buildSalesOrderItem();
        sourceItem.getSalesOrder().setCustomerCode(null);
        Customer customer = new Customer();
        customer.setCustomerCode("C-MD-001");
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                "客户甲",
                "项目A"
        )).thenReturn(Optional.of(customer));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement statement = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getCustomerCode(),
                    statement.getCustomerName(),
                    statement.getProjectId(),
                    statement.getProjectName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getSalesAmount(),
                    statement.getReceiptAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class),
                customerRepository
        );

        CustomerStatementResponse response = service.create(buildRequest(new BigDecimal("0.00")));

        assertThat(response.customerCode()).isEqualTo("C-MD-001");
    }

    @Test
    void shouldRejectCustomerCodeMismatchWithSourceOrder() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(buildSalesOrderItem()));

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest("C-OTHER", new BigDecimal("0.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码与客户对账单客户编码不一致");
    }

    @Test
    void shouldRejectSettledReceiptAmountThatExceedsSourceAmount() {
        CustomerStatementSourceService sourceService = mock(CustomerStatementSourceService.class);
        com.leo.erp.statement.service.StatementSettlementSyncService settlementSyncService =
                mock(com.leo.erp.statement.service.StatementSettlementSyncService.class);
        when(sourceService.applyItems(
                any(CustomerStatement.class),
                any(CustomerStatementRequest.class),
                any(java.util.function.LongSupplier.class)
        )).thenReturn(new CustomerStatementSourceService.SourceApplyResult(
                new BigDecimal("1000.00"),
                null,
                null
        ));
        when(settlementSyncService.resolveCustomerReceiptAmount(1L)).thenReturn(new BigDecimal("1200.00"));
        CustomerStatementApplyService applyService = new CustomerStatementApplyService(
                mock(WorkflowTransitionGuard.class),
                sourceService,
                settlementSyncService
        );
        CustomerStatement entity = new CustomerStatement();
        entity.setId(1L);

        assertThatThrownBy(() -> applyService.apply(entity, buildRequest(BigDecimal.ZERO), () -> 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售金额不能低于已收款金额");
    }

    @Test
    void shouldMarkDeletedStatementStatusWhenDeleting() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        statement.setStatementNo("KHDZ-DELETE-001");
        statement.setStatus("待确认");
        statement.setDeletedFlag(false);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        assertThat(statement.isDeletedFlag()).isTrue();
        assertThat(statement.getStatus()).isEqualTo("待确认");
        verify(repository).save(argThat(saved ->
                saved.isDeletedFlag() && "待确认".equals(saved.getStatus())
        ));
    }

    @Test
    void shouldExposePendingFinalizeSalesOrdersAsStatementCandidates() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(1L);
        salesOrder.setOrderNo("SO-001");
        salesOrder.setCustomerName("客户甲");
        salesOrder.setProjectName("项目A");
        salesOrder.setDeliveryDate(LocalDate.of(2026, 5, 6));
        salesOrder.setSalesName("张三");
        salesOrder.setStatus("待完善");
        salesOrder.setTotalWeight(new BigDecimal("1.000"));
        salesOrder.setTotalAmount(new BigDecimal("1000.00"));

        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(salesOrder))
        );

        CustomerStatementService service = service(
                repository,
                mapper,
                salesOrderRepository,
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        List<CustomerStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), new PageFilter("", null, null, null, null, null, null, null, null, null, null, null, null, null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).orderNo()).isEqualTo("SO-001");
        assertThat(candidates.get(0).status()).isEqualTo("待完善");
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(statement))
        );
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement s = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getCustomerCode(),
                    s.getCustomerName(),
                    s.getProjectId(),
                    s.getProjectName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getSalesAmount(),
                    s.getReceiptAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(statement)));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement s = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getCustomerCode(),
                    s.getCustomerName(),
                    s.getProjectId(),
                    s.getProjectName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getSalesAmount(),
                    s.getReceiptAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.search("KHDZ", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(true);

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单号已存在");
    }

    @Test
    void validateUpdateShouldRejectDuplicateChangedStatementNo() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement entity = createCustomerStatement(1L, "KHDZ-OLD");
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(true);
        TestableCustomerStatementService service = testableService(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.validateUpdate(entity, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单号已存在");
    }

    @Test
    void validateUpdateShouldAllowChangedStatementNoWhenUnique() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement entity = createCustomerStatement(1L, "KHDZ-OLD");
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        TestableCustomerStatementService service = testableService(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.validateUpdate(entity, buildRequest(new BigDecimal("1000.00")));

        verify(repository).existsByStatementNoAndDeletedFlagFalse("KHDZ-001");
    }

    @Test
    void findVisibleEntityShouldUseRepositoryFindById() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement entity = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        TestableCustomerStatementService service = testableService(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        Optional<CustomerStatement> result = service.findVisibleEntity(1L);

        assertThat(result).containsSame(entity);
        verify(repository).findById(1L);
    }

    @Test
    void notFoundMessageShouldReturnCustomerStatementMissingMessage() {
        TestableCustomerStatementService service = testableService(
                mock(CustomerStatementRepository.class),
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThat(service.notFoundMessage()).isEqualTo("客户对账单不存在");
    }

    @Test
    void shouldReturnException_whenUpdateWithDuplicateStatementNo() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-OLD");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(true);

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.update(1L, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单来源销售订单不能为空");
    }

    @Test
    void shouldRejectNegativeSettledReceiptAmount() {
        CustomerStatementSourceService sourceService = mock(CustomerStatementSourceService.class);
        com.leo.erp.statement.service.StatementSettlementSyncService settlementSyncService =
                mock(com.leo.erp.statement.service.StatementSettlementSyncService.class);
        when(sourceService.applyItems(
                any(CustomerStatement.class),
                any(CustomerStatementRequest.class),
                any(java.util.function.LongSupplier.class)
        )).thenReturn(new CustomerStatementSourceService.SourceApplyResult(
                new BigDecimal("1000.00"),
                null,
                null
        ));
        when(settlementSyncService.resolveCustomerReceiptAmount(1L)).thenReturn(new BigDecimal("-100.00"));
        CustomerStatementApplyService applyService = new CustomerStatementApplyService(
                mock(WorkflowTransitionGuard.class),
                sourceService,
                settlementSyncService
        );
        CustomerStatement entity = new CustomerStatement();
        entity.setId(1L);

        assertThatThrownBy(() -> applyService.apply(entity, buildRequest(BigDecimal.ZERO), () -> 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单收款金额不能为负数");
    }

    @Test
    void shouldUpdateStatusToConfirmedWithAuditGuard() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement s = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getCustomerCode(),
                    s.getCustomerName(),
                    s.getProjectId(),
                    s.getProjectName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getSalesAmount(),
                    s.getReceiptAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                workflowTransitionGuard
        );

        CustomerStatementResponse response = service.updateStatus(1L, StatusConstants.CONFIRMED);

        assertThat(response.status()).isEqualTo(StatusConstants.CONFIRMED);
        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "customer-statement",
                StatusConstants.PENDING_CONFIRM,
                StatusConstants.CONFIRMED,
                StatusConstants.CONFIRMED
        );
        verify(repository).save(argThat(saved -> StatusConstants.CONFIRMED.equals(saved.getStatus())));
    }

    @Test
    void shouldLockActualSalesOrdersBeforeCreateApply() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerStatementApplyService applyService = mock(CustomerStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(itemQueryService.findActiveByIdIn(List.of(201L)))
                .thenReturn(List.of(sourceSalesOrderItem(201L, 30L)));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerStatementService service = lockingService(
                repository,
                itemQueryService,
                mock(WorkflowTransitionGuard.class),
                applyService,
                lockService
        );

        service.create(buildRequest(BigDecimal.ZERO));

        InOrder inOrder = inOrder(lockService, applyService);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(30L), List.of(), List.of());
        inOrder.verify(applyService).apply(any(CustomerStatement.class), any(CustomerStatementRequest.class), any());
    }

    @Test
    void shouldLockOldAndNewActualSalesOrdersBeforeUpdateApply() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerStatementApplyService applyService = mock(CustomerStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        CustomerStatementItem existingItem = new CustomerStatementItem();
        existingItem.setSourceSalesOrderItemId(301L);
        statement.setItems(new java.util.ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemQueryService.findActiveByIdIn(List.of(201L, 301L))).thenReturn(List.of(
                sourceSalesOrderItem(301L, 40L),
                sourceSalesOrderItem(201L, 20L),
                sourceSalesOrderItem(201L, 20L)
        ));
        CustomerStatementService service = lockingService(
                repository,
                itemQueryService,
                mock(WorkflowTransitionGuard.class),
                applyService,
                lockService
        );

        service.update(1L, buildRequest(BigDecimal.ZERO));

        InOrder inOrder = inOrder(lockService, applyService);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(20L, 40L), List.of(), List.of());
        inOrder.verify(applyService).apply(any(CustomerStatement.class), any(CustomerStatementRequest.class), any());
    }

    @Test
    void shouldLockExistingActualSalesOrdersBeforeStatusMutation() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-STATUS");
        CustomerStatementItem existingItem = new CustomerStatementItem();
        existingItem.setSourceSalesOrderItemId(301L);
        statement.setItems(new java.util.ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemQueryService.findActiveByIdIn(List.of(301L)))
                .thenReturn(List.of(sourceSalesOrderItem(301L, 40L)));
        CustomerStatementService service = lockingService(
                repository,
                itemQueryService,
                workflowTransitionGuard,
                mock(CustomerStatementApplyService.class),
                lockService,
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );

        service.updateStatus(1L, StatusConstants.CONFIRMED);

        InOrder inOrder = inOrder(lockService, workflowTransitionGuard);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(40L), List.of(), List.of());
        inOrder.verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "customer-statement",
                StatusConstants.PENDING_CONFIRM,
                StatusConstants.CONFIRMED,
                StatusConstants.CONFIRMED
        );
    }

    @Test
    void shouldLockExistingActualSalesOrdersBeforeDeleteMutation() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService itemQueryService = mock(SalesOrderItemQueryService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-DELETE");
        CustomerStatementItem existingItem = new CustomerStatementItem();
        existingItem.setSourceSalesOrderItemId(301L);
        statement.setItems(new java.util.ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemQueryService.findActiveByIdIn(List.of(301L)))
                .thenReturn(List.of(sourceSalesOrderItem(301L, 40L)));
        CustomerStatementService service = lockingService(
                repository,
                itemQueryService,
                mock(WorkflowTransitionGuard.class),
                mock(CustomerStatementApplyService.class),
                lockService
        );

        service.delete(1L);

        InOrder inOrder = inOrder(lockService, repository);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(40L), List.of(), List.of());
        inOrder.verify(repository).save(statement);
    }

    @Test
    void shouldBlockReverseConfirmationWhenSettledAllocationExists() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-SETTLED-STATUS");
        statement.setStatus(StatusConstants.CONFIRMED);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已收款核销"
        )).when(guard).assertNoSettledAllocations(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.CUSTOMER,
                1L,
                "反确认"
        );

        assertThatThrownBy(() -> mutationGuardService(repository, guard).updateStatus(
                1L,
                StatusConstants.PENDING_CONFIRM
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已收款核销");
    }

    @Test
    void shouldBlockDeleteWhenSettledAllocationExists() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-SETTLED-DELETE");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已收款核销"
        )).when(guard).assertNoSettledAllocations(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.CUSTOMER,
                1L,
                "删除"
        );

        assertThatThrownBy(() -> mutationGuardService(repository, guard).delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已收款核销");
    }

    @Test
    void shouldBlockFinancialLinkageUpdateWhenSettledAllocationExists() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已收款核销"
        )).when(guard).assertFinancialLinkageMutationAllowed(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.CUSTOMER,
                1L,
                true
        );

        assertThatThrownBy(() -> mutationGuardService(repository, guard).update(
                1L,
                buildRequest(BigDecimal.ZERO)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已收款核销");
    }

    private CustomerStatementService mutationGuardService(
            CustomerStatementRepository repository,
            com.leo.erp.statement.service.StatementSettlementMutationGuard guard) {
        return new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(CustomerStatementResponseAssembler.class),
                mock(WorkflowTransitionGuard.class),
                mock(CustomerStatementSourceService.class),
                mock(CustomerStatementApplyService.class),
                mock(SalesOrderItemQueryService.class),
                mock(SourceAllocationLockService.class),
                guard
        );
    }

    private CustomerStatementService lockingService(CustomerStatementRepository repository,
                                                     SalesOrderItemQueryService itemQueryService,
                                                     WorkflowTransitionGuard workflowTransitionGuard,
                                                     CustomerStatementApplyService applyService,
                                                     SourceAllocationLockService lockService) {
        return lockingService(
                repository,
                itemQueryService,
                workflowTransitionGuard,
                applyService,
                lockService,
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );
    }

    private CustomerStatementService lockingService(CustomerStatementRepository repository,
                                                     SalesOrderItemQueryService itemQueryService,
                                                     WorkflowTransitionGuard workflowTransitionGuard,
                                                     CustomerStatementApplyService applyService,
                                                     SourceAllocationLockService lockService,
                                                     com.leo.erp.statement.service.StatementSettlementMutationGuard guard) {
        return new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(CustomerStatementResponseAssembler.class),
                workflowTransitionGuard,
                mock(CustomerStatementSourceService.class),
                applyService,
                itemQueryService,
                lockService,
                guard
        );
    }

    private CustomerStatementService service(CustomerStatementRepository repository,
                                             CustomerStatementMapper mapper,
                                             SalesOrderRepository salesOrderRepository,
                                             SalesOrderItemQueryService salesOrderItemQueryService,
                                             WorkflowTransitionGuard workflowTransitionGuard) {
        return service(
                repository,
                mapper,
                salesOrderRepository,
                salesOrderItemQueryService,
                workflowTransitionGuard,
                null
        );
    }

    private TestableCustomerStatementService testableService(CustomerStatementRepository repository,
                                                            CustomerStatementMapper mapper,
                                                            SalesOrderRepository salesOrderRepository,
                                                            SalesOrderItemQueryService salesOrderItemQueryService,
                                                            WorkflowTransitionGuard workflowTransitionGuard) {
        CustomerStatementSourceService sourceService =
                new CustomerStatementSourceService(repository, salesOrderRepository, salesOrderItemQueryService, null);
        return new TestableCustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                new CustomerStatementResponseAssembler(mapper),
                workflowTransitionGuard,
                sourceService,
                new CustomerStatementApplyService(
                        workflowTransitionGuard,
                        sourceService,
                        mock(com.leo.erp.statement.service.StatementSettlementSyncService.class)
                ),
                salesOrderItemQueryService,
                mock(SourceAllocationLockService.class),
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );
    }

    private CustomerStatementService service(CustomerStatementRepository repository,
                                             CustomerStatementMapper mapper,
                                             SalesOrderRepository salesOrderRepository,
                                             SalesOrderItemQueryService salesOrderItemQueryService,
                                             WorkflowTransitionGuard workflowTransitionGuard,
                                             CustomerRepository customerRepository) {
        CustomerStatementSourceService sourceService =
                new CustomerStatementSourceService(repository, salesOrderRepository, salesOrderItemQueryService, customerRepository);
        return new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                new CustomerStatementResponseAssembler(mapper),
                workflowTransitionGuard,
                sourceService,
                new CustomerStatementApplyService(
                        workflowTransitionGuard,
                        sourceService,
                        mock(com.leo.erp.statement.service.StatementSettlementSyncService.class)
                ),
                salesOrderItemQueryService,
                mock(SourceAllocationLockService.class),
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );
    }

    private CustomerStatement createCustomerStatement(Long id, String statementNo) {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(id);
        statement.setStatementNo(statementNo);
        statement.setCustomerName("客户甲");
        statement.setProjectName("项目A");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 6));
        statement.setSalesAmount(new BigDecimal("1000.00"));
        statement.setReceiptAmount(BigDecimal.ZERO);
        statement.setClosingAmount(new BigDecimal("1000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");
        statement.setItems(List.of());
        return statement;
    }

    private CustomerStatementRequest buildRequest(BigDecimal receiptAmount) {
        return buildRequest(null, receiptAmount);
    }

    private CustomerStatementRequest buildRequest(String customerCode, BigDecimal receiptAmount) {
        return new CustomerStatementRequest(
                "KHDZ-001",
                customerCode,
                "客户甲",
                null,
                "项目A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 6),
                new BigDecimal("1000.00"),
                receiptAmount,
                null,
                "待确认",
                "备注",
                List.of(new CustomerStatementItemRequest(
                        "SO-001",
                        201L,
                        "M-001",
                        "品牌A",
                        "螺纹",
                        "盘螺",
                        "HRB400",
                        "12",
                        "吨",
                        "LOT-001",
                        1,
                        "件",
                        new BigDecimal("1.000"),
                        1,
                        new BigDecimal("1.000"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                ))
        );
    }

    private SalesOrderItem buildSalesOrderItem() {
        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-001");
        order.setCustomerCode("C-001");
        order.setCustomerName("客户甲");
        order.setProjectName("项目A");
        order.setStatus("完成销售");

        SalesOrderItem item = new SalesOrderItem();
        item.setId(201L);
        item.setSalesOrder(order);
        item.setMaterialCode("M-001");
        item.setBrand("品牌A");
        item.setCategory("螺纹");
        item.setMaterial("盘螺");
        item.setSpec("HRB400");
        item.setLength("12");
        item.setUnit("吨");
        item.setBatchNo("LOT-001");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }

    private SalesOrderItem sourceSalesOrderItem(Long itemId, Long orderId) {
        SalesOrderItem item = buildSalesOrderItem();
        item.setId(itemId);
        item.getSalesOrder().setId(orderId);
        return item;
    }

    private static class TestableCustomerStatementService extends CustomerStatementService {

        TestableCustomerStatementService(CustomerStatementRepository repository,
                                         SnowflakeIdGenerator idGenerator,
                                         CustomerStatementResponseAssembler responseAssembler,
                                         WorkflowTransitionGuard workflowTransitionGuard,
                                         CustomerStatementSourceService customerStatementSourceService,
                                         CustomerStatementApplyService applyService,
                                         SalesOrderItemQueryService salesOrderItemQueryService,
                                         SourceAllocationLockService sourceAllocationLockService,
                                         com.leo.erp.statement.service.StatementSettlementMutationGuard settlementMutationGuard) {
            super(
                    repository,
                    idGenerator,
                    responseAssembler,
                    workflowTransitionGuard,
                    customerStatementSourceService,
                    applyService,
                    salesOrderItemQueryService,
                    sourceAllocationLockService,
                    settlementMutationGuard
            );
        }

        @Override
        protected void validateUpdate(CustomerStatement entity, CustomerStatementRequest request) {
            super.validateUpdate(entity, request);
        }

        @Override
        protected Optional<CustomerStatement> findVisibleEntity(Long id) {
            return super.findVisibleEntity(id);
        }

        @Override
        protected String notFoundMessage() {
            return super.notFoundMessage();
        }
    }
}
