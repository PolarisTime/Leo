package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.web.dto.ReceiptAllocationRequest;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptApplyStableIdentityTest {

    @Test
    void shouldStoreCanonicalPartySnapshotsForUnallocatedDraft() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Customer customer = new Customer();
        customer.setId(101L);
        customer.setCustomerCode("CUST-001");
        customer.setCustomerName("客户A");
        Project project = new Project();
        project.setId(1001L);
        project.setCustomerId(101L);
        project.setProjectName("项目A");
        when(customerRepository.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(project));
        ReceiptApplyService applyService = new ReceiptApplyService(
                mock(WorkflowTransitionGuard.class),
                new ReceiptAllocationService(mock(ReceiptStatementAllocationValidator.class)),
                mock(ReceiptSettlementSyncService.class),
                new ReceiptPartyIdentityResolver(customerRepository, projectRepository)
        );
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        ReceiptRequest request = new ReceiptRequest(
                "SK-001",
                101L,
                null,
                " 客户A ",
                1001L,
                " 项目A ",
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

        applyService.apply(receipt, request, () -> 301L);

        assertThat(receipt.getCustomerId()).isEqualTo(101L);
        assertThat(receipt.getCustomerCode()).isEqualTo("CUST-001");
        assertThat(receipt.getCustomerName()).isEqualTo("客户A");
        assertThat(receipt.getProjectId()).isEqualTo(1001L);
        assertThat(receipt.getProjectName()).isEqualTo("项目A");
    }

    @Test
    void shouldRejectUnallocatedDraftWithoutCustomerId() {
        ReceiptApplyService applyService = new ReceiptApplyService(
                mock(WorkflowTransitionGuard.class),
                new ReceiptAllocationService(mock(ReceiptStatementAllocationValidator.class)),
                mock(ReceiptSettlementSyncService.class),
                partyIdentityResolver()
        );
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        ReceiptRequest request = new ReceiptRequest(
                "SK-001",
                null,
                "CUST-001",
                "客户A",
                1001L,
                "项目A",
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

        assertThatThrownBy(() -> applyService.apply(receipt, request, () -> 301L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID不能为空");
    }

    @Test
    void shouldRejectUnallocatedDraftWithoutProjectId() {
        ReceiptApplyService applyService = new ReceiptApplyService(
                mock(WorkflowTransitionGuard.class),
                new ReceiptAllocationService(mock(ReceiptStatementAllocationValidator.class)),
                mock(ReceiptSettlementSyncService.class),
                partyIdentityResolver()
        );
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        ReceiptRequest request = new ReceiptRequest(
                "SK-001",
                101L,
                "CUST-001",
                "客户A",
                null,
                "项目A",
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

        assertThatThrownBy(() -> applyService.apply(receipt, request, () -> 301L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目ID不能为空");
    }

    @Test
    void shouldDeriveCustomerAndProjectIdentityFromCustomerStatement() {
        ReceiptStatementAllocationValidator validator = mock(ReceiptStatementAllocationValidator.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(21L);
        statement.setCustomerId(101L);
        statement.setCustomerCode("CUST-101");
        statement.setProjectId(1001L);
        statement.setSettlementCompanyId(31L);
        statement.setSettlementCompanyName("结算主体A");
        when(validator.validate(
                any(ReceiptRequest.class),
                any(),
                anyLong(),
                anyLong(),
                any(BigDecimal.class),
                any(),
                anyInt()
        )).thenReturn(statement);
        ReceiptAllocationService allocationService = new ReceiptAllocationService(validator);
        ReceiptSettlementSyncService settlementSyncService = mock(ReceiptSettlementSyncService.class);
        when(settlementSyncService.resolveLegacySourceStatementId(any(Receipt.class))).thenReturn(21L);
        ReceiptApplyService applyService = new ReceiptApplyService(
                mock(WorkflowTransitionGuard.class),
                allocationService,
                settlementSyncService,
                partyIdentityResolver()
        );
        Receipt receipt = new Receipt();
        receipt.setId(1L);
        receipt.setItems(new ArrayList<>());
        ReceiptRequest request = new ReceiptRequest(
                "SK-001",
                null,
                null,
                "客户A",
                null,
                "项目A",
                null,
                null,
                null,
                LocalDate.of(2026, 7, 11),
                "银行转账",
                new BigDecimal("100.00"),
                StatusConstants.DRAFT,
                "财务A",
                null,
                List.of(new ReceiptAllocationRequest(null, 21L, new BigDecimal("100.00")))
        );

        applyService.apply(receipt, request, () -> 301L);

        assertThat(receipt.getCustomerId()).isEqualTo(101L);
        assertThat(receipt.getProjectId()).isEqualTo(1001L);
        assertThat(receipt.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceStatementId()).isEqualTo(21L);
            assertThat(item.getSourceCustomerStatementId()).isEqualTo(21L);
        });
    }

    private ReceiptPartyIdentityResolver partyIdentityResolver() {
        return new ReceiptPartyIdentityResolver(
                mock(CustomerRepository.class),
                mock(ProjectRepository.class)
        );
    }
}
