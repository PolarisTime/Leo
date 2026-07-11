package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerAdjustmentSettlementCompanyServiceTest {

    @Test
    void shouldResolveAndPersistActiveSettlementCompanySnapshot() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        Customer customer = new Customer();
        customer.setCustomerCode("C-001");
        customer.setCustomerName("客户A");
        CompanySetting settlementCompany = new CompanySetting();
        settlementCompany.setId(31L);
        settlementCompany.setCompanyName("真实结算主体");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C-001"))
                .thenReturn(Optional.of(customer));
        when(companySettingService.requireActiveSettlementCompany(31L)).thenReturn(settlementCompany);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(LedgerAdjustment.class))).thenAnswer(invocation -> {
            LedgerAdjustment adjustment = invocation.getArgument(0);
            return response(adjustment);
        });
        LedgerAdjustmentService service = new LedgerAdjustmentService(
                repository,
                mapper,
                new SnowflakeIdGenerator(0L),
                customerRepository,
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class),
                mock(WorkflowTransitionGuard.class),
                companySettingService
        );

        LedgerAdjustmentResponse response = service.create(request());

        assertThat(response.settlementCompanyId()).isEqualTo(31L);
        assertThat(response.settlementCompanyName()).isEqualTo("真实结算主体");
        verify(companySettingService).requireActiveSettlementCompany(31L);
        ArgumentCaptor<LedgerAdjustment> captor = ArgumentCaptor.forClass(LedgerAdjustment.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSettlementCompanyId()).isEqualTo(31L);
        assertThat(captor.getValue().getSettlementCompanyName()).isEqualTo("真实结算主体");
    }

    private LedgerAdjustmentRequest request() {
        return new LedgerAdjustmentRequest(
                "LA-SC-001",
                "应收",
                "客户",
                "C-001",
                "请求客户名",
                31L,
                "不能信任的结算主体名称",
                null,
                null,
                LocalDate.of(2026, 7, 11),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        );
    }

    private LedgerAdjustmentResponse response(LedgerAdjustment adjustment) {
        return new LedgerAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNo(),
                adjustment.getDirection(),
                adjustment.getCounterpartyType(),
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
