package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.service.CompanySettingService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LedgerAdjustmentServiceTest {

    private static final String WRITE_DISABLED_MESSAGE =
            "台账调整单已停用，余额调整必须通过有来源的业务或资金单据完成";

    @Test
    void shouldPageHistoricalAdjustmentsWithFilters() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        LedgerAdjustment adjustment = adjustment("LA-PAGE", StatusConstants.DRAFT);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adjustment)));
        when(mapper.toResponse(adjustment)).thenReturn(toResponse(adjustment));
        LedgerAdjustmentService service = newService(repository, mapper);

        var page = service.page(
                new com.leo.erp.common.api.PageQuery(0, 10, "adjustmentDate", "desc"),
                com.leo.erp.common.api.PageFilter.of(
                        "客户",
                        null,
                        31L,
                        StatusConstants.DRAFT,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)
                ),
                "应收",
                "客户"
        );

        assertThat(page.getContent()).singleElement()
                .satisfies(response -> assertThat(response.adjustmentNo()).isEqualTo("LA-PAGE"));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldSearchHistoricalAdjustmentsByKeyword() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        LedgerAdjustment adjustment = adjustment("LA-SEARCH", StatusConstants.AUDITED);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adjustment)));
        when(mapper.toResponse(adjustment)).thenReturn(toResponse(adjustment));
        LedgerAdjustmentService service = newService(repository, mapper);

        List<LedgerAdjustmentResponse> results = service.search("客户", 5);

        assertThat(results).singleElement()
                .satisfies(response -> assertThat(response.adjustmentNo()).isEqualTo("LA-SEARCH"));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldRejectCreatingLedgerAdjustment() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentService service = newService(repository, mock(LedgerAdjustmentMapper.class));

        assertWriteDisabled(() -> service.create(validRequest()));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectUpdatingLedgerAdjustment() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentService service = newService(repository, mock(LedgerAdjustmentMapper.class));

        assertWriteDisabled(() -> service.update(1L, validRequest()));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectUpdatingLedgerAdjustmentStatus() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentService service = newService(repository, mock(LedgerAdjustmentMapper.class));

        assertWriteDisabled(() -> service.updateStatus(1L, StatusConstants.AUDITED));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectDeletingLedgerAdjustment() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentService service = newService(repository, mock(LedgerAdjustmentMapper.class));

        assertWriteDisabled(() -> service.delete(1L));

        verifyNoInteractions(repository);
    }

    private void assertWriteDisabled(ThrowingCallable command) {
        assertThatThrownBy(command)
                .isInstanceOf(BusinessException.class)
                .hasMessage(WRITE_DISABLED_MESSAGE);
    }

    private LedgerAdjustmentService newService(LedgerAdjustmentRepository repository,
                                               LedgerAdjustmentMapper mapper) {
        return new LedgerAdjustmentService(
                repository,
                mapper,
                new SnowflakeIdGenerator(0L),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class),
                mock(WorkflowTransitionGuard.class),
                mock(CompanySettingService.class)
        );
    }

    private LedgerAdjustmentRequest validRequest() {
        return new LedgerAdjustmentRequest(
                "LA-001",
                "应收",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        );
    }

    private LedgerAdjustment adjustment(String adjustmentNo, String status) {
        LedgerAdjustment adjustment = new LedgerAdjustment();
        adjustment.setId(1L);
        adjustment.setAdjustmentNo(adjustmentNo);
        adjustment.setDirection("应收");
        adjustment.setCounterpartyType("客户");
        adjustment.setCounterpartyId(71L);
        adjustment.setCounterpartyCode("C-001");
        adjustment.setCounterpartyName("客户A");
        adjustment.setSettlementCompanyId(31L);
        adjustment.setSettlementCompanyName("结算主体A");
        adjustment.setAdjustmentDate(LocalDate.of(2026, 6, 1));
        adjustment.setAmount(new BigDecimal("100.00"));
        adjustment.setAdjustmentType("其他调整");
        adjustment.setEffect("增加余额");
        adjustment.setStatus(status);
        adjustment.setOperatorName("财务A");
        return adjustment;
    }

    private LedgerAdjustmentResponse toResponse(LedgerAdjustment adjustment) {
        return new LedgerAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNo(),
                adjustment.getDirection(),
                adjustment.getCounterpartyType(),
                adjustment.getCounterpartyId(),
                adjustment.getCounterpartyCode(),
                adjustment.getCounterpartyName(),
                adjustment.getSettlementCompanyId(),
                adjustment.getSettlementCompanyName(),
                adjustment.getProjectId(),
                adjustment.getProjectName(),
                adjustment.getAdjustmentDate(),
                adjustment.getAmount(),
                adjustment.getAdjustmentType(),
                adjustment.getEffect(),
                adjustment.getStatus(),
                adjustment.getOperatorName(),
                adjustment.getRemark()
        );
    }
}
