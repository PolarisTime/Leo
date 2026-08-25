package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerAdjustmentServiceTest {

    @Mock
    private LedgerAdjustmentRepository repository;

    @Mock
    private LedgerAdjustmentMapper mapper;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private CustomerQuery customerQuery;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private CarrierQuery carrierQuery;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private CompanySettingService companySettingService;

    @Test
    void apply_shouldFollowProjectSettlementCompany() {
        LedgerAdjustmentService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));
        when(companySettingService.requireActiveSettlementCompany(40L))
                .thenReturn(company(40L, "项目主体"));

        LedgerAdjustment entity = new LedgerAdjustment();
        service.apply(entity, request(null, null));

        assertThat(entity.getProjectId()).isEqualTo(20L);
        assertThat(entity.getSettlementCompanyId()).isEqualTo(40L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("项目主体");
    }

    @Test
    void apply_shouldRejectSettlementCompanyDifferentFromProject() {
        LedgerAdjustmentService service = service();
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));

        assertThatThrownBy(() -> service.apply(new LedgerAdjustment(), request(30L, "客户主体")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体与项目不一致");
    }

    private LedgerAdjustmentService service() {
        return new LedgerAdjustmentService(
                repository,
                mapper,
                idGenerator,
                customerQuery,
                supplierQuery,
                carrierQuery,
                projectQuery,
                companySettingService
        );
    }

    private LedgerAdjustmentRequest request(Long settlementCompanyId, String settlementCompanyName) {
        return new LedgerAdjustmentRequest(
                "LA001",
                "应收",
                "客户",
                10L,
                "CUST001",
                "客户A",
                settlementCompanyId,
                settlementCompanyName,
                20L,
                "项目A",
                LocalDate.of(2026, 8, 25),
                new BigDecimal("10.00"),
                "其他调整",
                "增加余额",
                "草稿",
                "操作员",
                null
        );
    }

    private CompanySetting company(Long id, String name) {
        CompanySetting company = new CompanySetting();
        company.setId(id);
        company.setCompanyName(name);
        return company;
    }
}
