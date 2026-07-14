package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatementItem;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapper;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

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

class SupplierStatementServiceTest {

    @Test
    void shouldIgnoreRequestedPaymentAmountWhenNoSettledAllocationExists() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatementMapper mapper = mock(SupplierStatementMapper.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(false);
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseInboundItemQueryService.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(buildInboundItem()));
        when(mapper.toResponse(any(SupplierStatement.class))).thenAnswer(invocation -> {
            SupplierStatement statement = invocation.getArgument(0);
            return new SupplierStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getSupplierName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getPurchaseAmount(),
                    statement.getPaymentAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        SupplierStatementService service = service(
                repository,
                mapper,
                mock(PurchaseInboundRepository.class),
                purchaseInboundItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        SupplierStatementResponse response = service.create(buildRequest(new BigDecimal("1000.00")));

        assertThat(response.purchaseAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.paymentAmount()).isEqualByComparingTo("0.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldRejectSettledPaymentAmountThatExceedsSourceAmount() {
        SupplierStatementSourceService sourceService = mock(SupplierStatementSourceService.class);
        com.leo.erp.statement.service.StatementSettlementSyncService settlementSyncService =
                mock(com.leo.erp.statement.service.StatementSettlementSyncService.class);
        when(sourceService.applyItems(
                any(SupplierStatement.class),
                any(SupplierStatementRequest.class),
                any(java.util.function.LongSupplier.class)
        )).thenReturn(new SupplierStatementSourceService.SourceApplyResult(
                new BigDecimal("1000.00"),
                null,
                null
        ));
        when(settlementSyncService.resolveSupplierPaymentAmount(1L)).thenReturn(new BigDecimal("1200.00"));
        SupplierStatementApplyService applyService = new SupplierStatementApplyService(
                mock(WorkflowTransitionGuard.class),
                sourceService,
                settlementSyncService
        );
        SupplierStatement entity = new SupplierStatement();
        entity.setId(1L);

        assertThatThrownBy(() -> applyService.apply(entity, buildRequest(BigDecimal.ZERO), () -> 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购金额不能低于已付款金额");
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatementMapper mapper = mock(SupplierStatementMapper.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-001");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(statement))
        );
        when(mapper.toResponse(any(SupplierStatement.class))).thenAnswer(invocation -> {
            SupplierStatement s = invocation.getArgument(0);
            return new SupplierStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getSupplierName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getPurchaseAmount(),
                    s.getPaymentAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        SupplierStatementService service = service(
                repository,
                mapper,
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatementMapper mapper = mock(SupplierStatementMapper.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-001");
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(statement)));
        when(mapper.toResponse(any(SupplierStatement.class))).thenAnswer(invocation -> {
            SupplierStatement s = invocation.getArgument(0);
            return new SupplierStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getSupplierName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getPurchaseAmount(),
                    s.getPaymentAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        SupplierStatementService service = service(
                repository,
                mapper,
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.search("GYDZ", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnCandidatePage_whenCallingCandidatePage() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundRepository purchaseInboundRepository = mock(PurchaseInboundRepository.class);
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(10L);
        inbound.setInboundNo("IN-001");
        inbound.setSupplierName("供应商甲");
        inbound.setWarehouseName("一号仓");
        inbound.setInboundDate(LocalDate.of(2026, 5, 1));
        inbound.setSettlementMode("月结");
        inbound.setTotalWeight(new BigDecimal("1.000"));
        inbound.setTotalAmount(new BigDecimal("1000.00"));
        inbound.setStatus(StatusConstants.INBOUND_COMPLETED);
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(purchaseInboundRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(inbound)));
        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                purchaseInboundRepository,
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.candidatePage(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));

        assertThat(result.getContent())
                .extracting(SupplierStatementCandidateResponse::inboundNo)
                .containsExactly("IN-001");
        verify(purchaseInboundRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(true);

        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单号已存在");
    }

    @Test
    void validateUpdateShouldRejectDuplicateChangedStatementNo() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement entity = createSupplierStatement(1L, "GYDZ-OLD");
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(true);
        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.validateUpdate(entity, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单号已存在");
    }

    @Test
    void validateUpdateShouldAllowChangedStatementNoWhenUnique() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement entity = createSupplierStatement(1L, "GYDZ-OLD");
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(false);
        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.validateUpdate(entity, buildRequest(new BigDecimal("1000.00")));

        verify(repository).existsByStatementNoAndDeletedFlagFalse("GYDZ-001");
    }

    @Test
    void detailShouldUseVisibleLookupAndSupplierNotFoundMessageForAdminDeletedView() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        when(repository.findById(404L)).thenReturn(Optional.empty());
        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );
        CrudRuntimeSettings runtimeSettings = mock(CrudRuntimeSettings.class);
        when(runtimeSettings.shouldAdminSeeDeletedRecords()).thenReturn(true);
        ReflectionTestUtils.invokeMethod(service, "setCrudRuntimeSettings", runtimeSettings);
        var principal = com.leo.erp.security.support.SecurityPrincipal.authenticated(
                1L, "admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单不存在");
        verify(repository).findById(404L);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnException_whenUpdateWithDuplicateStatementNo() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-OLD");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(true);

        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.update(1L, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单来源采购入库单不能为空");
    }

    @Test
    void shouldRejectNegativeSettledPaymentAmount() {
        SupplierStatementSourceService sourceService = mock(SupplierStatementSourceService.class);
        com.leo.erp.statement.service.StatementSettlementSyncService settlementSyncService =
                mock(com.leo.erp.statement.service.StatementSettlementSyncService.class);
        when(sourceService.applyItems(
                any(SupplierStatement.class),
                any(SupplierStatementRequest.class),
                any(java.util.function.LongSupplier.class)
        )).thenReturn(new SupplierStatementSourceService.SourceApplyResult(
                new BigDecimal("1000.00"),
                null,
                null
        ));
        when(settlementSyncService.resolveSupplierPaymentAmount(1L)).thenReturn(new BigDecimal("-100.00"));
        SupplierStatementApplyService applyService = new SupplierStatementApplyService(
                mock(WorkflowTransitionGuard.class),
                sourceService,
                settlementSyncService
        );
        SupplierStatement entity = new SupplierStatement();
        entity.setId(1L);

        assertThatThrownBy(() -> applyService.apply(entity, buildRequest(BigDecimal.ZERO), () -> 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单付款金额不能为负数");
    }

    @Test
    void shouldMarkDeletedStatementStatusWhenDeleting() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        statement.setStatementNo("GYDZ-DELETE-001");
        statement.setStatus("待确认");
        statement.setDeletedFlag(false);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierStatementService service = service(
                repository,
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        assertThat(statement.isDeletedFlag()).isTrue();
        assertThat(statement.getStatus()).isEqualTo("待确认");
    }

    @Test
    void shouldUpdateStatusToConfirmedWithAuditGuard() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatementMapper mapper = mock(SupplierStatementMapper.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(SupplierStatement.class))).thenAnswer(invocation -> {
            SupplierStatement s = invocation.getArgument(0);
            return new SupplierStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getSupplierCode(),
                    s.getSupplierName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getPurchaseAmount(),
                    s.getPaymentAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        SupplierStatementService service = service(
                repository,
                mapper,
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                workflowTransitionGuard
        );

        SupplierStatementResponse response = service.updateStatus(1L, StatusConstants.CONFIRMED);

        assertThat(response.status()).isEqualTo(StatusConstants.CONFIRMED);
        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "supplier-statement",
                StatusConstants.PENDING_CONFIRM,
                StatusConstants.CONFIRMED,
                StatusConstants.CONFIRMED
        );
        verify(repository).save(argThat(saved -> StatusConstants.CONFIRMED.equals(saved.getStatus())));
    }

    @Test
    void shouldLockActualPurchaseInboundsBeforeCreateApply() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        SupplierStatementApplyService applyService = mock(SupplierStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(itemQueryService.findAllActiveByIdIn(List.of(101L)))
                .thenReturn(List.of(sourceInboundItem(101L, 30L)));
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SupplierStatementService service = lockingService(
                repository,
                itemQueryService,
                mock(WorkflowTransitionGuard.class),
                applyService,
                lockService
        );

        service.create(buildRequest(BigDecimal.ZERO));

        InOrder inOrder = inOrder(lockService, applyService);
        inOrder.verify(lockService).lockDocumentSources(List.of(30L), List.of(), List.of(), List.of());
        inOrder.verify(applyService).apply(any(SupplierStatement.class), any(SupplierStatementRequest.class), any());
    }

    @Test
    void shouldLockOldAndNewActualPurchaseInboundsBeforeUpdateApply() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        SupplierStatementApplyService applyService = mock(SupplierStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-001");
        SupplierStatementItem existingItem = new SupplierStatementItem();
        existingItem.setSourceInboundItemId(301L);
        statement.setItems(new java.util.ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemQueryService.findAllActiveByIdIn(List.of(101L, 301L))).thenReturn(List.of(
                sourceInboundItem(301L, 40L),
                sourceInboundItem(101L, 20L),
                sourceInboundItem(101L, 20L)
        ));
        SupplierStatementService service = lockingService(
                repository,
                itemQueryService,
                mock(WorkflowTransitionGuard.class),
                applyService,
                lockService
        );

        service.update(1L, buildRequest(BigDecimal.ZERO));

        InOrder inOrder = inOrder(lockService, applyService);
        inOrder.verify(lockService).lockDocumentSources(List.of(20L, 40L), List.of(), List.of(), List.of());
        inOrder.verify(applyService).apply(any(SupplierStatement.class), any(SupplierStatementRequest.class), any());
    }

    @Test
    void shouldLockExistingActualPurchaseInboundsBeforeStatusMutation() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-STATUS");
        SupplierStatementItem existingItem = new SupplierStatementItem();
        existingItem.setSourceInboundItemId(301L);
        statement.setItems(new java.util.ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemQueryService.findAllActiveByIdIn(List.of(301L)))
                .thenReturn(List.of(sourceInboundItem(301L, 40L)));
        SupplierStatementService service = lockingService(
                repository,
                itemQueryService,
                workflowTransitionGuard,
                mock(SupplierStatementApplyService.class),
                lockService,
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );

        service.updateStatus(1L, StatusConstants.CONFIRMED);

        InOrder inOrder = inOrder(lockService, workflowTransitionGuard);
        inOrder.verify(lockService).lockDocumentSources(List.of(40L), List.of(), List.of(), List.of());
        inOrder.verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "supplier-statement",
                StatusConstants.PENDING_CONFIRM,
                StatusConstants.CONFIRMED,
                StatusConstants.CONFIRMED
        );
    }

    @Test
    void shouldLockExistingActualPurchaseInboundsBeforeDeleteMutation() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-DELETE");
        SupplierStatementItem existingItem = new SupplierStatementItem();
        existingItem.setSourceInboundItemId(301L);
        statement.setItems(new java.util.ArrayList<>(List.of(existingItem)));
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(SupplierStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemQueryService.findAllActiveByIdIn(List.of(301L)))
                .thenReturn(List.of(sourceInboundItem(301L, 40L)));
        SupplierStatementService service = lockingService(
                repository,
                itemQueryService,
                mock(WorkflowTransitionGuard.class),
                mock(SupplierStatementApplyService.class),
                lockService
        );

        service.delete(1L);

        InOrder inOrder = inOrder(lockService, repository);
        inOrder.verify(lockService).lockDocumentSources(List.of(40L), List.of(), List.of(), List.of());
        inOrder.verify(repository).save(statement);
    }

    @Test
    void shouldBlockReverseConfirmationWhenSettledAllocationExists() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-SETTLED-STATUS");
        statement.setStatus(StatusConstants.CONFIRMED);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已付款核销"
        )).when(guard).assertNoSettledAllocations(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.SUPPLIER,
                1L,
                "反确认"
        );

        assertThatThrownBy(() -> mutationGuardService(repository, guard).updateStatus(
                1L,
                StatusConstants.PENDING_CONFIRM
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已付款核销");
    }

    @Test
    void shouldBlockDeleteWhenSettledAllocationExists() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-SETTLED-DELETE");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已付款核销"
        )).when(guard).assertNoSettledAllocations(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.SUPPLIER,
                1L,
                "删除"
        );

        assertThatThrownBy(() -> mutationGuardService(repository, guard).delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已付款核销");
    }

    @Test
    void shouldBlockFinancialLinkageUpdateWhenSettledAllocationExists() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已付款核销"
        )).when(guard).assertFinancialLinkageMutationAllowed(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.SUPPLIER,
                1L,
                true
        );

        assertThatThrownBy(() -> mutationGuardService(repository, guard).update(
                1L,
                buildRequest(BigDecimal.ZERO)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已付款核销");
    }

    private SupplierStatementService mutationGuardService(
            SupplierStatementRepository repository,
            com.leo.erp.statement.service.StatementSettlementMutationGuard guard) {
        return new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementResponseAssembler.class),
                mock(WorkflowTransitionGuard.class),
                mock(SupplierStatementSourceService.class),
                mock(SupplierStatementApplyService.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(SourceAllocationLockService.class),
                guard
        );
    }

    private SupplierStatementService lockingService(SupplierStatementRepository repository,
                                                     PurchaseInboundItemQueryService itemQueryService,
                                                     WorkflowTransitionGuard workflowTransitionGuard,
                                                     SupplierStatementApplyService applyService,
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

    private SupplierStatementService lockingService(SupplierStatementRepository repository,
                                                     PurchaseInboundItemQueryService itemQueryService,
                                                     WorkflowTransitionGuard workflowTransitionGuard,
                                                     SupplierStatementApplyService applyService,
                                                     SourceAllocationLockService lockService,
                                                     com.leo.erp.statement.service.StatementSettlementMutationGuard guard) {
        return new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementResponseAssembler.class),
                workflowTransitionGuard,
                mock(SupplierStatementSourceService.class),
                applyService,
                itemQueryService,
                lockService,
                guard
        );
    }

    private SupplierStatementService service(SupplierStatementRepository repository,
                                             SupplierStatementMapper mapper,
                                             PurchaseInboundRepository purchaseInboundRepository,
                                             PurchaseInboundItemQueryService purchaseInboundItemQueryService,
                                             WorkflowTransitionGuard workflowTransitionGuard) {
        SupplierStatementSourceService sourceService =
                new SupplierStatementSourceService(repository, purchaseInboundRepository, purchaseInboundItemQueryService, null);
        return new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                new SupplierStatementResponseAssembler(mapper),
                workflowTransitionGuard,
                sourceService,
                new SupplierStatementApplyService(
                        workflowTransitionGuard,
                        sourceService,
                        mock(com.leo.erp.statement.service.StatementSettlementSyncService.class)
                ),
                purchaseInboundItemQueryService,
                mock(SourceAllocationLockService.class),
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );
    }

    private SupplierStatement createSupplierStatement(Long id, String statementNo) {
        SupplierStatement statement = new SupplierStatement();
        statement.setId(id);
        statement.setStatementNo(statementNo);
        statement.setSupplierName("供应商甲");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 6));
        statement.setPurchaseAmount(new BigDecimal("1000.00"));
        statement.setPaymentAmount(BigDecimal.ZERO);
        statement.setClosingAmount(new BigDecimal("1000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");
        statement.setItems(List.of());
        return statement;
    }

    private SupplierStatementRequest buildRequest(BigDecimal paymentAmount) {
        return new SupplierStatementRequest(
                "GYDZ-001",
                "供应商甲",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 6),
                new BigDecimal("1000.00"),
                paymentAmount,
                null,
                "待确认",
                "备注",
                List.of(new SupplierStatementItemRequest(
                        "IN-001",
                        101L,
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

    private PurchaseInboundItem buildInboundItem() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("IN-001");
        inbound.setSupplierCode("SUP-001");
        inbound.setSupplierName("供应商甲");
        inbound.setStatus(StatusConstants.INBOUND_COMPLETED);

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setPurchaseInbound(inbound);
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
        item.setWeightAdjustmentTon(BigDecimal.ZERO);
        item.setWeightAdjustmentAmount(BigDecimal.ZERO);
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }

    private PurchaseInboundItem sourceInboundItem(Long itemId, Long inboundId) {
        PurchaseInboundItem item = buildInboundItem();
        item.setId(itemId);
        item.getPurchaseInbound().setId(inboundId);
        return item;
    }
}
