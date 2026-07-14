package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LedgerAdjustmentSettlementCompanyServiceTest {

    @Test
    void shouldRejectWriteBeforeResolvingSettlementCompanySnapshot() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
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

        assertThatThrownBy(() -> service.create(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("台账调整单已停用，余额调整必须通过有来源的业务或资金单据完成");
        verifyNoInteractions(repository, mapper, customerRepository, companySettingService);
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
}
