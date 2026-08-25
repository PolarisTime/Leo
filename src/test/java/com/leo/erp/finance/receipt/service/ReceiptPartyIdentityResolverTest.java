package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptPartyIdentityResolverTest {

    @Mock
    private CustomerQuery customerQuery;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private SupplierQuery supplierQuery;

    @Mock
    private CompanySettingRepository companySettingRepository;

    private ReceiptPartyIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ReceiptPartyIdentityResolver(
                customerQuery,
                projectQuery,
                supplierQuery,
                companySettingRepository
        );
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(
                new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", null, 30L, "客户主体")
        ));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001", 40L, "项目主体")
        ));
    }

    @Test
    void resolve_shouldFollowProjectSettlementCompany() {
        when(companySettingRepository.findByIdAndDeletedFlagFalse(40L))
                .thenReturn(Optional.of(company(40L, "项目主体")));

        ReceiptPartyIdentityResolver.PartySnapshot snapshot = resolver.resolve(
                request(null, null)
        );

        assertThat(snapshot.settlementCompanyId()).isEqualTo(40L);
        assertThat(snapshot.settlementCompanyName()).isEqualTo("项目主体");
        assertThat(snapshot.projectId()).isEqualTo(20L);
    }

    @Test
    void resolve_shouldRejectSettlementCompanyDifferentFromProject() {
        assertThatThrownBy(() -> resolver.resolve(request(30L, "客户主体")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体与项目不一致");
    }

    private ReceiptRequest request(Long settlementCompanyId, String settlementCompanyName) {
        return new ReceiptRequest(
                "RC001",
                10L,
                "CUST001",
                "客户A",
                20L,
                "项目A",
                settlementCompanyId,
                settlementCompanyName,
                null,
                LocalDate.of(2026, 8, 25),
                "银行转账",
                new BigDecimal("100.00"),
                "草稿",
                "操作员",
                null,
                List.of()
        );
    }

    private CompanySetting company(Long id, String name) {
        CompanySetting company = new CompanySetting();
        company.setId(id);
        company.setCompanyName(name);
        return company;
    }
}
