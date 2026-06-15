package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class FreightStatementApplyServiceTest {

    @Test
    void shouldApplyStatementHeaderAndAmounts() {
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        FreightStatementCarrierResolver carrierResolver = spy(new FreightStatementCarrierResolver(null));
        FreightStatementSourceService sourceService = spy(new FreightStatementSourceService(
                mock(FreightStatementRepository.class),
                mock(FreightBillRepository.class)
        ));
        FreightStatementApplyService service = new FreightStatementApplyService(
                workflowTransitionGuard,
                carrierResolver,
                sourceService
        );
        FreightStatement entity = statement();
        entity.setPaidAmount(new BigDecimal("30.00"));
        FreightStatementCommand command = command(StatusConstants.AUDITED, StatusConstants.SIGNED);

        doReturn("C-001").when(carrierResolver).resolveCarrierCode(" C-001 ", "物流甲");
        doReturn(new FreightStatementSourceService.SourceApplyResult(
                new BigDecimal("2.000"),
                new BigDecimal("100.00")
        )).when(sourceService).applyItems(eq(entity), eq(command), any());

        service.apply(entity, command, new AtomicLong(10L)::getAndIncrement);

        assertThat(entity.getStatementNo()).isEqualTo("FS-001");
        assertThat(entity.getCarrierName()).isEqualTo("物流甲");
        assertThat(entity.getCarrierCode()).isEqualTo("C-001");
        assertThat(entity.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(entity.getEndDate()).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.AUDITED);
        assertThat(entity.getSignStatus()).isEqualTo(StatusConstants.SIGNED);
        assertThat(entity.getAttachment()).isEqualTo("新附件");
        assertThat(entity.getRemark()).isEqualTo("备注");
        assertThat(entity.getTotalWeight()).isEqualByComparingTo("2.000");
        assertThat(entity.getTotalFreight()).isEqualByComparingTo("100.00");
        assertThat(entity.getPaidAmount()).isEqualByComparingTo("30.00");
        assertThat(entity.getUnpaidAmount()).isEqualByComparingTo("70.00");
        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "freight-statement",
                StatusConstants.PENDING_AUDIT,
                StatusConstants.AUDITED,
                StatusConstants.AUDITED
        );
        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "freight-statement",
                StatusConstants.UNSIGNED,
                StatusConstants.SIGNED,
                StatusConstants.SIGNED
        );
    }

    @Test
    void shouldKeepExistingAttachmentWhenRequestAttachmentIsNull() {
        FreightStatementSourceService sourceService = spy(new FreightStatementSourceService(
                mock(FreightStatementRepository.class),
                mock(FreightBillRepository.class)
        ));
        FreightStatementApplyService service = new FreightStatementApplyService(
                mock(WorkflowTransitionGuard.class),
                new FreightStatementCarrierResolver(null),
                sourceService
        );
        FreightStatement entity = statement();
        entity.setAttachment("旧附件");
        FreightStatementCommand command = new FreightStatementCommand(
                "FS-001",
                null,
                "物流甲",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null,
                null,
                null,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                "备注",
                List.of()
        );
        doReturn(new FreightStatementSourceService.SourceApplyResult(
                new BigDecimal("2.000"),
                new BigDecimal("100.00")
        )).when(sourceService).applyItems(eq(entity), eq(command), any());

        service.apply(entity, command, () -> 10L);

        assertThat(entity.getAttachment()).isEqualTo("旧附件");
        assertThat(entity.getPaidAmount()).isEqualByComparingTo("0.00");
        assertThat(entity.getUnpaidAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldRejectWhenPaidAmountExceedsTotalFreight() {
        FreightStatementSourceService sourceService = spy(new FreightStatementSourceService(
                mock(FreightStatementRepository.class),
                mock(FreightBillRepository.class)
        ));
        FreightStatementApplyService service = new FreightStatementApplyService(
                mock(WorkflowTransitionGuard.class),
                new FreightStatementCarrierResolver(null),
                sourceService
        );
        FreightStatement entity = statement();
        entity.setPaidAmount(new BigDecimal("101.00"));
        FreightStatementCommand command = command(StatusConstants.PENDING_AUDIT, StatusConstants.UNSIGNED);
        doReturn(new FreightStatementSourceService.SourceApplyResult(
                new BigDecimal("2.000"),
                new BigDecimal("100.00")
        )).when(sourceService).applyItems(eq(entity), eq(command), any());

        assertThatThrownBy(() -> service.apply(entity, command, () -> 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单总运费不能低于已付款金额");
    }

    private FreightStatement statement() {
        FreightStatement entity = new FreightStatement();
        entity.setId(1L);
        entity.setStatus(StatusConstants.PENDING_AUDIT);
        entity.setSignStatus(StatusConstants.UNSIGNED);
        return entity;
    }

    private FreightStatementCommand command(String status, String signStatus) {
        return new FreightStatementCommand(
                "FS-001",
                " C-001 ",
                "物流甲",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null,
                null,
                null,
                status,
                signStatus,
                "新附件",
                "备注",
                List.of()
        );
    }
}
