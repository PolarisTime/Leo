package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.mapper.SupplierStatementMapper;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementItemRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementRequest;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementResponse;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SupplierStatementServiceTest {

    @Test
    void shouldPersistRequestedPaymentAmountWithoutImmediateSync() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatementMapper mapper = mock(SupplierStatementMapper.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        StatementSettlementSyncService syncService = mock(StatementSettlementSyncService.class);
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

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                mock(PurchaseInboundRepository.class),
                purchaseInboundItemQueryService,
                syncService,
                mock(WorkflowTransitionGuard.class)
        );

        SupplierStatementResponse response = service.create(buildRequest(new BigDecimal("1000.00")));

        assertThat(response.purchaseAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.paymentAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("0.00");
        verifyNoInteractions(syncService);
    }

    @Test
    void shouldRejectOverPaymentAmount() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(false);
        when(purchaseInboundItemQueryService.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(buildInboundItem()));

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                purchaseInboundItemQueryService,
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1200.00"))))
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

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(StatementSettlementSyncService.class),
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

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.search("GYDZ", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(true);

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单号已存在");
    }

    @Test
    void shouldReturnException_whenUpdateWithDuplicateStatementNo() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = createSupplierStatement(1L, "GYDZ-OLD");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(true);

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.update(1L, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商对账单来源采购入库单不能为空");
    }

    @Test
    void shouldRejectNegativePaymentAmount() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundItemQueryService purchaseInboundItemQueryService = mock(PurchaseInboundItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("GYDZ-001")).thenReturn(false);
        when(purchaseInboundItemQueryService.findAllActiveByIdIn(List.of(101L))).thenReturn(List.of(buildInboundItem()));

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                purchaseInboundItemQueryService,
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("-100.00"))))
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

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mock(SupplierStatementMapper.class),
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(StatementSettlementSyncService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        assertThat(statement.isDeletedFlag()).isTrue();
        assertThat(statement.getStatus()).isEqualTo("已删除");
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

        SupplierStatementService service = new SupplierStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper,
                mock(PurchaseInboundRepository.class),
                mock(PurchaseInboundItemQueryService.class),
                mock(StatementSettlementSyncService.class),
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
        inbound.setSupplierName("供应商甲");
        inbound.setStatus("完成采购");

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
}
