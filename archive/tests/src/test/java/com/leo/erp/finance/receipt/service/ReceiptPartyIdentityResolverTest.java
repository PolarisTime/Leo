package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptPartyIdentityResolverTest {

    @Test
    void shouldReturnCanonicalCustomerAndProjectSnapshots() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(customerRepository.findByIdAndDeletedFlagFalse(101L))
                .thenReturn(Optional.of(customer(101L, "CUST-001", "客户A")));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L))
                .thenReturn(Optional.of(project(1001L, 101L, "项目A")));
        ReceiptPartyIdentityResolver resolver = new ReceiptPartyIdentityResolver(
                customerRepository,
                projectRepository
        );

        ReceiptPartyIdentityResolver.PartySnapshot result = resolver.resolve(
                request(null, " 客户A ", " 项目A ")
        );

        assertThat(result.customerId()).isEqualTo(101L);
        assertThat(result.customerCode()).isEqualTo("CUST-001");
        assertThat(result.customerName()).isEqualTo("客户A");
        assertThat(result.projectId()).isEqualTo(1001L);
        assertThat(result.projectName()).isEqualTo("项目A");
    }

    @Test
    void shouldRejectMissingCustomer() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L))
                .thenReturn(Optional.of(project(1001L, 101L, "项目A")));
        ReceiptPartyIdentityResolver resolver = new ReceiptPartyIdentityResolver(
                customerRepository,
                projectRepository
        );

        assertThatThrownBy(() -> resolver.resolve(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    void shouldRejectCustomerNameThatConflictsWithCustomerId() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(customerRepository.findByIdAndDeletedFlagFalse(101L))
                .thenReturn(Optional.of(customer(101L, "CUST-001", "客户A")));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L))
                .thenReturn(Optional.of(project(1001L, 101L, "项目A")));
        ReceiptPartyIdentityResolver resolver = new ReceiptPartyIdentityResolver(
                customerRepository,
                projectRepository
        );

        assertThatThrownBy(() -> resolver.resolve(request("CUST-001", "客户B", "项目A")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与ID不一致");
    }

    @Test
    void shouldRejectCustomerCodeThatConflictsWithCustomerId() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(customerRepository.findByIdAndDeletedFlagFalse(101L))
                .thenReturn(Optional.of(customer(101L, "CUST-001", "客户A")));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L))
                .thenReturn(Optional.of(project(1001L, 101L, "项目A")));
        ReceiptPartyIdentityResolver resolver = new ReceiptPartyIdentityResolver(
                customerRepository,
                projectRepository
        );

        assertThatThrownBy(() -> resolver.resolve(request("CUST-999", "客户A", "项目A")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码与ID不一致");
    }

    @Test
    void shouldRejectProjectNameThatConflictsWithProjectId() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(customerRepository.findByIdAndDeletedFlagFalse(101L))
                .thenReturn(Optional.of(customer(101L, "CUST-001", "客户A")));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L))
                .thenReturn(Optional.of(project(1001L, 101L, "项目A")));
        ReceiptPartyIdentityResolver resolver = new ReceiptPartyIdentityResolver(
                customerRepository,
                projectRepository
        );

        assertThatThrownBy(() -> resolver.resolve(request("CUST-001", "客户A", "项目B")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目名称与ID不一致");
    }

    @Test
    void shouldRejectProjectBelongingToAnotherCustomer() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Customer customer = customer(101L, "CUST-001", "客户A");
        Project project = project(1001L, 202L, "项目A");
        when(customerRepository.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(project));
        ReceiptPartyIdentityResolver resolver = new ReceiptPartyIdentityResolver(
                customerRepository,
                projectRepository
        );

        assertThatThrownBy(() -> resolver.resolve(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    private ReceiptRequest request() {
        return request("CUST-001", "客户A", "项目A");
    }

    private ReceiptRequest request(String customerCode, String customerName, String projectName) {
        return new ReceiptRequest(
                "SK-001",
                101L,
                customerCode,
                customerName,
                1001L,
                projectName,
                null,
                null,
                null,
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of()
        );
    }

    private Customer customer(Long id, String code, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerCode(code);
        customer.setCustomerName(name);
        return customer;
    }

    private Project project(Long id, Long customerId, String name) {
        Project project = new Project();
        project.setId(id);
        project.setCustomerId(customerId);
        project.setProjectName(name);
        return project;
    }
}
