package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.concurrency.SourceAllocationLockService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.service.CrudRuntimeSettings;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightStatementServiceTest {

    private FreightStatementRepository repository;
    private FreightBillRepository freightBillRepository;
    private AttachmentBindingService attachmentBindingService;
    private StatementSettlementSyncService statementSettlementSyncService;
    private WorkflowTransitionGuard workflowTransitionGuard;
    private FreightStatementWebMapper freightStatementWebMapper;
    private CarrierRepository carrierRepository;
    private SourceAllocationLockService sourceAllocationLockService;
    private FreightStatementService service;

    @BeforeEach
    void setUp() {
        repository = repository(List.of(createEntity(1L, "FS-001")), false, List.of());
        freightBillRepository = freightBillRepository(List.of(createFreightBill("FB-001")));
        attachmentBindingService = mock(AttachmentBindingService.class);
        statementSettlementSyncService = mock(StatementSettlementSyncService.class);
        org.mockito.Mockito.when(statementSettlementSyncService.syncFreightStatement(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        freightStatementWebMapper = mock(FreightStatementWebMapper.class);
        Carrier carrier = new Carrier();
        carrier.setCarrierCode("C-001");
        carrierRepository = carrierRepository(Optional.of(carrier));
        sourceAllocationLockService = mock(SourceAllocationLockService.class);
        service = service(repository, freightBillRepository, carrierRepository);
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        var svc = service(repository(List.of(), true, List.of()), freightBillRepository, carrierRepository);
        var command = new FreightStatementCommand("FS-001", null, null, null, null, null, null, null, null, null, null, null, List.of());

        assertThatThrownBy(() -> svc.create(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单号已存在");
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        var result = service.search("FS", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnResponsePage_whenCallingResponsePage() {
        var result = service.responsePage(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnResponseSearchResults_whenCallingResponseSearch() {
        var result = service.responseSearch("FS", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldMapResponseDetailThroughWebMapper() {
        FreightStatementResponse response = response(1L, "FS-001");
        org.mockito.Mockito.when(freightStatementWebMapper.toResponse(org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        FreightStatementResponse result = service.responseDetail(1L);

        assertThat(result).isSameAs(response);
        verify(freightStatementWebMapper).toResponse(org.mockito.ArgumentMatchers.any(FreightStatementView.class));
    }

    @Test
    void shouldMapResponseCreateThroughRequestMapper() {
        FreightStatementCommand command = command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"));
        FreightStatementResponse response = response(100L, "FS-NEW");
        when(freightStatementWebMapper.toCommand(org.mockito.ArgumentMatchers.any())).thenReturn(command);
        when(freightStatementWebMapper.toResponse(org.mockito.ArgumentMatchers.any())).thenReturn(response);
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository, carrierRepository);

        FreightStatementResponse result = svc.responseCreate(request());

        assertThat(result).isSameAs(response);
        verify(freightStatementWebMapper).toCommand(org.mockito.ArgumentMatchers.any());
        verify(freightStatementWebMapper).toResponse(org.mockito.ArgumentMatchers.any(FreightStatementView.class));
    }

    @Test
    void shouldMapResponseUpdateAndKeepOriginalStatementNo() {
        FreightStatementCommand command = command("IGNORED", "物流甲", BigDecimal.ZERO, item(null, "FB-001"));
        FreightStatementResponse response = response(1L, "FS-OLD");
        when(freightStatementWebMapper.toCommand(org.mockito.ArgumentMatchers.any())).thenReturn(command);
        when(freightStatementWebMapper.toResponse(org.mockito.ArgumentMatchers.any())).thenReturn(response);
        var svc = service(repository(List.of(createEntity(1L, "FS-OLD")), false, List.of()), freightBillRepository, carrierRepository);

        FreightStatementResponse result = svc.responseUpdate(1L, request());

        assertThat(result).isSameAs(response);
        verify(freightStatementWebMapper).toCommand(org.mockito.ArgumentMatchers.any());
        verify(freightStatementWebMapper).toResponse(org.mockito.ArgumentMatchers.any(FreightStatementView.class));
    }

    @Test
    void shouldMapResponseUpdateStatusThroughWebMapper() {
        FreightStatementResponse response = response(1L, "FS-001");
        when(freightStatementWebMapper.toResponse(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        FreightStatementResponse result = service.responseUpdateStatus(1L, StatusConstants.AUDITED);

        assertThat(result).isSameAs(response);
        verify(freightStatementWebMapper).toResponse(org.mockito.ArgumentMatchers.any(FreightStatementView.class));
    }

    @Test
    void shouldReturnCandidatePage_whenCallingCandidatePage() {
        var result = service.candidatePage(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnException_whenUpdateWithoutSourceBillItems() {
        var svc = service(repository(List.of(createEntity(1L, "FS-OLD")), true, List.of()), freightBillRepository, carrierRepository);
        var command = new FreightStatementCommand("FS-001", null, null, null, null, null, null, null, null, null, null, null, List.of());

        assertThatThrownBy(() -> svc.update(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单来源物流单不能为空");
    }

    @Test
    void shouldUpdateStatus_whenCallingUpdateStatus() {
        var result = service.updateStatus(1L, StatusConstants.AUDITED);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateStatementFromAuditedFreightBillAndResolveCarrierCode() {
        Carrier carrier = new Carrier();
        carrier.setCarrierCode("C-001");
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository, carrierRepository(Optional.of(carrier)));
        var result = svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001")));

        assertThat(result.statementNo()).isEqualTo("FS-NEW");
        assertThat(result.carrierCode()).isEqualTo("C-001");
        assertThat(result.totalWeight()).isEqualByComparingTo("2.000");
        assertThat(result.totalFreight()).isEqualByComparingTo("100.00");
        assertThat(result.unpaidAmount()).isEqualByComparingTo("100.00");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).sourceNo()).isEqualTo("FB-001");
        assertThat(result.items().get(0).quantityUnit()).isEqualTo("件");
        assertThat(result.items().get(0).settlementCompanyId()).isEqualTo(7L);
        assertThat(result.items().get(0).settlementCompanyName()).isEqualTo("主体A");
    }

    @Test
    void shouldRejectCreate_whenPaidAmountExceedsSourceBillFreight() {
        FreightStatement entity = createEntity(1L, "FS-001");
        entity.setPaidAmount(new BigDecimal("101"));
        var svc = service(repository(List.of(entity), false, List.of()), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.update(1L, command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单总运费不能低于已付款金额");
    }

    @Test
    void shouldRejectCreate_whenSourceBillMissing() {
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository(List.of()), carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-MISSING"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单ID1不存在");
    }

    @Test
    void shouldReportNotFoundMessageWhenDetailMissing() {
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单不存在");
    }

    @Test
    void shouldRejectCreate_whenSourceBillCarrierDiffers() {
        var bill = createFreightBill("FB-001");
        bill.setCarrierCode("C-002");
        bill.setCarrierName("物流乙");
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository(List.of(bill)), carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商编码不存在");
    }

    @Test
    void shouldRejectCreate_whenSourceBillUnaudited() {
        var bill = createFreightBill("FB-001");
        bill.setStatus(StatusConstants.PENDING_AUDIT);
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository(List.of(bill)), carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单FB-001未审核");
    }

    @Test
    void shouldRejectCreate_whenSourceBillAlreadyOccupied() {
        FreightStatement occupied = createEntity(2L, "FS-USED");
        FreightStatementItem occupiedItem = entityItem("FB-001");
        occupiedItem.setSourceFreightBillId(1L);
        occupied.setItems(List.of(occupiedItem));
        var svc = service(repository(List.of(), false, List.of(occupied)), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单FB-001已生成物流对账单");
    }

    @Test
    void shouldRejectUpdate_whenItemIdDoesNotBelongToStatement() {
        var svc = service(repository(List.of(createEntity(1L, "FS-001")), false, List.of()), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.update(1L, command("FS-UPDATED", "物流甲", BigDecimal.ZERO, item(404L, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行子项ID不存在");
    }

    @Test
    void shouldRejectUpdateWhenChangedStatementNoAlreadyExists() {
        FreightStatement entity = createEntity(1L, "FS-OLD");
        var svc = service(repository(List.of(entity), true, List.of()), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                svc,
                "validateUpdate",
                entity,
                command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单号已存在");
    }

    @Test
    void shouldAllowUnchangedStatementNoWhenDuplicateExists() {
        FreightStatement entity = createEntity(1L, "FS-001");
        var svc = service(repository(List.of(entity), true, List.of()), freightBillRepository, carrierRepository);

        ReflectionTestUtils.invokeMethod(
                svc,
                "validateUpdate",
                entity,
                command("FS-001", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))
        );
    }

    @Test
    void shouldAllowChangedStatementNoWhenNoDuplicateExists() {
        FreightStatement entity = createEntity(1L, "FS-OLD");
        var svc = service(repository(List.of(entity), false, List.of()), freightBillRepository, carrierRepository);

        ReflectionTestUtils.invokeMethod(
                svc,
                "validateUpdate",
                entity,
                command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))
        );
    }

    @Test
    void shouldLoadVisibleDeletedDetailForAdminWhenRuntimeSettingAllows() {
        FreightStatement deleted = createEntity(2L, "FS-DELETED");
        deleted.setDeletedFlag(true);
        var svc = service(repository(List.of(deleted), false, List.of()), freightBillRepository, carrierRepository);
        CrudRuntimeSettings runtimeSettings = mock(CrudRuntimeSettings.class);
        when(runtimeSettings.shouldAdminSeeDeletedRecords()).thenReturn(true);
        ReflectionTestUtils.invokeMethod(svc, "setCrudRuntimeSettings", runtimeSettings);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        FreightStatementView result = svc.detail(2L);

        assertThat(result.statementNo()).isEqualTo("FS-DELETED");
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldLockActualFreightBillsBeforeCreateApply() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightStatementApplyService applyService = mock(FreightStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(statementRepository.save(any(FreightStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightStatementService service = lockingService(statementRepository, applyService, lockService);

        service.create(command(
                "FS-LOCK-CREATE",
                "物流甲",
                BigDecimal.ZERO,
                item(null, "FB-002", 30L, 301L),
                item(null, " FB-001 ", 10L, 101L),
                item(null, "FB-002", 30L, 302L)
        ));

        InOrder inOrder = inOrder(lockService, applyService);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(), List.of(), List.of(10L, 30L));
        inOrder.verify(applyService).apply(any(FreightStatement.class), any(FreightStatementCommand.class), any());
    }

    @Test
    void shouldLockOldAndNewActualFreightBillsBeforeUpdateApply() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightStatementApplyService applyService = mock(FreightStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        FreightStatement statement = createEntity(1L, "FS-LOCK-UPDATE");
        statement.setItems(new java.util.ArrayList<>(List.of(entityItem("FB-OLD", 40L))));
        when(statementRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(statementRepository.save(any(FreightStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightStatementService service = lockingService(statementRepository, applyService, lockService);

        service.update(1L, command(
                "IGNORED",
                "物流甲",
                BigDecimal.ZERO,
                item(null, "FB-NEW", 20L, 201L)
        ));

        InOrder inOrder = inOrder(lockService, applyService);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(), List.of(), List.of(20L, 40L));
        inOrder.verify(applyService).apply(any(FreightStatement.class), any(FreightStatementCommand.class), any());
    }

    @Test
    void shouldLockExistingActualFreightBillsBeforeStatusMutation() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        FreightStatement statement = createEntity(1L, "FS-LOCK-STATUS");
        statement.setItems(new java.util.ArrayList<>(List.of(entityItem("FB-STATUS", 50L))));
        when(statementRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(statementRepository.save(any(FreightStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightStatementService service = lockingService(
                statementRepository,
                mock(FreightStatementApplyService.class),
                lockService
        );

        service.updateStatus(1L, StatusConstants.AUDITED);

        InOrder inOrder = inOrder(lockService, statementRepository);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(), List.of(), List.of(50L));
        inOrder.verify(statementRepository).save(statement);
    }

    @Test
    void shouldLockExistingActualFreightBillsBeforeDeleteMutation() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        FreightStatement statement = createEntity(1L, "FS-LOCK-DELETE");
        statement.setItems(new java.util.ArrayList<>(List.of(entityItem("FB-DELETE", 60L))));
        when(statementRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(statementRepository.save(any(FreightStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightStatementService service = lockingService(
                statementRepository,
                mock(FreightStatementApplyService.class),
                lockService
        );

        service.delete(1L);

        InOrder inOrder = inOrder(lockService, statementRepository);
        inOrder.verify(lockService).lockDocumentSources(List.of(), List.of(), List.of(), List.of(60L));
        inOrder.verify(statementRepository).save(statement);
    }

    @Test
    void shouldNotResolveSourceLockFromBusinessNumberSnapshot() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightStatementApplyService applyService = mock(FreightStatementApplyService.class);
        SourceAllocationLockService lockService = mock(SourceAllocationLockService.class);
        when(statementRepository.save(any(FreightStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FreightStatementService service = lockingService(statementRepository, applyService, lockService);

        service.create(command(
                "FS-LEGACY-SNAPSHOT",
                "物流甲",
                BigDecimal.ZERO,
                legacyItem(null, "FB-LEGACY")
        ));

        verify(lockService).lockDocumentSources(List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void shouldIgnoreSourceNumberSnapshotWhenCheckingFinancialLinkage() {
        FreightStatement entity = createEntity(1L, "FS-SNAPSHOT");
        entity.setItems(new java.util.ArrayList<>(List.of(entityItem("FB-OLD"))));
        FreightStatementCommand command = command(
                "IGNORED",
                "物流甲",
                BigDecimal.ZERO,
                legacyItem(null, "FB-NEW")
        );

        Boolean changed = ReflectionTestUtils.invokeMethod(
                service,
                "freightFinancialLinkageChanged",
                entity,
                command
        );

        assertThat(changed).isFalse();
    }

    @Test
    void shouldBlockReverseAuditWhenSettledAllocationExists() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightStatement statement = createEntity(1L, "FS-SETTLED-STATUS");
        statement.setStatus(StatusConstants.AUDITED);
        when(statementRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已付款核销"
        )).when(guard).assertNoSettledAllocations(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.FREIGHT,
                1L,
                "反审核"
        );

        assertThatThrownBy(() -> mutationGuardService(statementRepository, guard).updateStatus(
                1L,
                StatusConstants.PENDING_AUDIT
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已付款核销");
    }

    @Test
    void shouldBlockDeleteWhenSettledAllocationExists() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightStatement statement = createEntity(1L, "FS-SETTLED-DELETE");
        when(statementRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已付款核销"
        )).when(guard).assertNoSettledAllocations(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.FREIGHT,
                1L,
                "删除"
        );

        assertThatThrownBy(() -> mutationGuardService(statementRepository, guard).delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已付款核销");
    }

    @Test
    void shouldBlockFinancialLinkageUpdateWhenSettledAllocationExists() {
        FreightStatementRepository statementRepository = mock(FreightStatementRepository.class);
        FreightStatement statement = createEntity(1L, "FS-001");
        when(statementRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        com.leo.erp.statement.service.StatementSettlementMutationGuard guard =
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class);
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR,
                "存在已付款核销"
        )).when(guard).assertFinancialLinkageMutationAllowed(
                com.leo.erp.statement.service.StatementSettlementMutationGuard.StatementType.FREIGHT,
                1L,
                true
        );

        assertThatThrownBy(() -> mutationGuardService(statementRepository, guard).update(
                1L,
                command("FS-001", "物流乙", BigDecimal.ZERO, item(null, "FB-001"))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在已付款核销");
    }

    private FreightStatementService mutationGuardService(
            FreightStatementRepository statementRepository,
            com.leo.erp.statement.service.StatementSettlementMutationGuard guard) {
        return new FreightStatementService(
                statementRepository,
                new SnowflakeIdGenerator(1L),
                statementSettlementSyncService,
                freightStatementWebMapper,
                mock(FreightStatementSourceService.class),
                mock(FreightStatementViewAssembler.class),
                mock(FreightStatementPageAssembler.class),
                mock(FreightStatementApplyService.class),
                mock(SourceAllocationLockService.class),
                guard
        );
    }

    private FreightStatementService lockingService(FreightStatementRepository statementRepository,
                                                    FreightStatementApplyService applyService,
                                                    SourceAllocationLockService lockService) {
        return new FreightStatementService(
                statementRepository,
                new SnowflakeIdGenerator(1L),
                statementSettlementSyncService,
                freightStatementWebMapper,
                mock(FreightStatementSourceService.class),
                mock(FreightStatementViewAssembler.class),
                mock(FreightStatementPageAssembler.class),
                applyService,
                lockService,
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );
    }

    private FreightStatementService service(FreightStatementRepository repo,
                                            FreightBillRepository billRepo,
                                            CarrierRepository carrierRepo) {
        FreightStatementSourceService sourceService = new FreightStatementSourceService(repo, billRepo);
        FreightStatementViewAssembler viewAssembler = new FreightStatementViewAssembler(attachmentBindingService);
        return new FreightStatementService(
                repo,
                new SnowflakeIdGenerator(1),
                statementSettlementSyncService,
                freightStatementWebMapper,
                sourceService,
                viewAssembler,
                new FreightStatementPageAssembler(viewAssembler),
                new FreightStatementApplyService(
                        workflowTransitionGuard,
                        new FreightStatementCarrierResolver(carrierRepo),
                        sourceService
                ),
                sourceAllocationLockService,
                mock(com.leo.erp.statement.service.StatementSettlementMutationGuard.class)
        );
    }

    @SuppressWarnings("unchecked")
    private static FreightStatementRepository repository(List<FreightStatement> statements,
                                                         boolean duplicateStatementNo,
                                                         List<FreightStatement> occupiedStatements) {
        return (FreightStatementRepository) Proxy.newProxyInstance(
                FreightStatementRepository.class.getClassLoader(),
                new Class[]{FreightStatementRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        if (method.getParameterCount() == 2) {
                            yield new PageImpl<>(statements, new PageQuery(0, 10, "id", "desc").toPageable("id"), statements.size());
                        }
                        yield statements;
                    }
                    case "findByIdAndDeletedFlagFalse" -> statements.stream()
                            .filter(entity -> entity.getId().equals(args[0]))
                            .findFirst();
                    case "findById" -> statements.stream()
                            .filter(entity -> entity.getId().equals(args[0]))
                            .findFirst();
                    case "existsByStatementNoAndDeletedFlagFalse" -> duplicateStatementNo;
                    case "save" -> args[0];
                    case "findAllBySourceNosExcludingCurrentStatement" -> occupiedStatements;
                    case "findOccupiedSourceFreightBillIdsExcludingCurrentStatement" -> occupiedStatements.stream()
                            .flatMap(statement -> statement.getItems().stream())
                            .map(FreightStatementItem::getSourceFreightBillId)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();
                    case "findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement" -> {
                        Set<Long> requestedIds = (Set<Long>) args[0];
                        yield occupiedStatements.stream()
                                .flatMap(statement -> statement.getItems().stream())
                                .map(FreightStatementItem::getSourceFreightBillId)
                                .filter(java.util.Objects::nonNull)
                                .filter(requestedIds::contains)
                                .distinct()
                                .toList();
                    }
                    case "toString" -> "FreightStatementRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static FreightBillRepository freightBillRepository(List<FreightBill> bills) {
        return (FreightBillRepository) Proxy.newProxyInstance(
                FreightBillRepository.class.getClassLoader(),
                new Class[]{FreightBillRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> new PageImpl<>(bills);
                    case "findByIdInAndDeletedFlagFalse" -> bills.stream()
                            .filter(bill -> ((Set<Long>) args[0]).contains(bill.getId()))
                            .toList();
                    case "findByBillNoInAndDeletedFlagFalse" -> bills.stream()
                            .filter(bill -> ((Set<String>) args[0]).contains(bill.getBillNo()))
                            .toList();
                    case "toString" -> "FreightBillRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static CarrierRepository carrierRepository(Optional<Carrier> carrier) {
        return (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByCarrierCodeAndDeletedFlagFalse" -> carrier
                            .filter(value -> value.getCarrierCode().equals(args[0]));
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static FreightStatementCommand command(String statementNo,
                                                   String carrierName,
                                                   BigDecimal paidAmount,
                                                   FreightStatementItemCommand... items) {
        return new FreightStatementCommand(
                statementNo,
                null,
                carrierName,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null,
                paidAmount,
                null,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                "备注",
                List.of(items)
        );
    }

    private static FreightStatementItemCommand item(Long id, String sourceNo) {
        return item(id, sourceNo, 1L, 11L);
    }

    private static FreightStatementItemCommand item(Long id,
                                                    String sourceNo,
                                                    Long sourceFreightBillId,
                                                    Long sourceFreightBillItemId) {
        return new FreightStatementItemCommand(
                id,
                sourceNo,
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

    private static FreightStatementItemCommand legacyItem(Long id, String sourceNo) {
        return new FreightStatementItemCommand(
                id,
                sourceNo,
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

    private static FreightStatementItem entityItem(String sourceNo) {
        return entityItem(sourceNo, null);
    }

    private static FreightStatementItem entityItem(String sourceNo, Long sourceFreightBillId) {
        FreightStatementItem item = new FreightStatementItem();
        item.setId(10L);
        item.setSourceNo(sourceNo);
        item.setSourceFreightBillId(sourceFreightBillId);
        return item;
    }

    private static FreightStatement createEntity(Long id, String statementNo) {
        FreightStatement entity = new FreightStatement();
        entity.setId(id);
        entity.setStatementNo(statementNo);
        entity.setCarrierCode("C-001");
        entity.setCarrierName("物流甲");
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 1, 31));
        entity.setStatus(StatusConstants.PENDING_AUDIT);
        entity.setSignStatus(StatusConstants.UNSIGNED);
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalFreight(new BigDecimal("100"));
        entity.setPaidAmount(BigDecimal.ZERO);
        entity.setUnpaidAmount(new BigDecimal("100"));
        entity.setItems(new java.util.ArrayList<>());
        return entity;
    }

    private static FreightBill createFreightBill(String billNo) {
        FreightBill bill = new FreightBill();
        bill.setId(1L);
        bill.setBillNo(billNo);
        bill.setCarrierCode("C-001");
        bill.setCarrierName("物流甲");
        bill.setSettlementCompanyId(70L);
        bill.setSettlementCompanyName("物流主体");
        bill.setStatus(StatusConstants.AUDITED);
        bill.setTotalFreight(new BigDecimal("100"));
        bill.setItems(new java.util.ArrayList<>(List.of(freightBillItem(bill))));
        return bill;
    }

    private static FreightBillItem freightBillItem(FreightBill bill) {
        FreightBillItem item = new FreightBillItem();
        item.setId(11L);
        item.setFreightBill(bill);
        item.setLineNo(1);
        item.setSourceNo(bill.getBillNo());
        item.setSourceSalesOutboundItemId(21L);
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
        return item;
    }

    private static FreightStatementRequest request() {
        return new FreightStatementRequest(
                "FS-REQ",
                "物流甲",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                "备注",
                List.of()
        );
    }

    private static FreightStatementResponse response(Long id, String statementNo) {
        return new FreightStatementResponse(
                id,
                statementNo,
                "物流甲",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                List.of(),
                "备注",
                List.of()
        );
    }
}
