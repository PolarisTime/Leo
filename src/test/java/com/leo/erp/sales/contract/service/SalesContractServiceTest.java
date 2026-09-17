package com.leo.erp.sales.contract.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.contract.domain.entity.SalesContract;
import com.leo.erp.sales.contract.repository.SalesContractRepository;
import com.leo.erp.sales.contract.repository.SalesOrderContractMetricsRepository;
import com.leo.erp.sales.contract.web.dto.SalesContractRequest;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesContractServiceTest {

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private SalesContractRepository repository;

    @Mock
    private SalesOrderContractMetricsRepository orderMetricsRepository;

    @Mock
    private CustomerQuery customerQuery;

    @Mock
    private ProjectQuery projectQuery;

    private SalesContractService service;

    @BeforeEach
    void setUp() {
        service = new SalesContractService(idGenerator, repository, orderMetricsRepository,
                customerQuery, projectQuery, new SalesContractApplyService());
    }

    @Test
    void create_assignsSnowflakeIdAndDraftStatus_andResolvesMasterSnapshots() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByContractNo("100")).thenReturn(false);
        when(customerQuery.findActiveById(1L)).thenReturn(Optional.of(customer(1L, "客户甲")));
        when(projectQuery.findActiveById(2L)).thenReturn(Optional.of(project(2L, "项目乙")));
        when(repository.save(any(SalesContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesContractResponse response = service.create(request(null, StatusConstants.DRAFT));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.contractNo()).isEqualTo("100");
        assertThat(response.status()).isEqualTo(StatusConstants.DRAFT);
        assertThat(response.customerName()).isEqualTo("客户甲");
        assertThat(response.projectName()).isEqualTo("项目乙");
    }

    @Test
    void create_keepsProvidedContractNo() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByContractNo("HT-2026-001")).thenReturn(false);
        when(customerQuery.findActiveById(1L)).thenReturn(Optional.of(customer(1L, "客户甲")));
        when(projectQuery.findActiveById(2L)).thenReturn(Optional.of(project(2L, "项目乙")));
        when(repository.save(any(SalesContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesContractResponse response = service.create(request("HT-2026-001", null));

        assertThat(response.contractNo()).isEqualTo("HT-2026-001");
        assertThat(response.status()).isEqualTo(StatusConstants.DRAFT);
    }

    @Test
    void create_rejectsDuplicateContractNo() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByContractNo("HT-2026-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("HT-2026-001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编号已存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsNonDraftStatus() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByContractNo("100")).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(null, StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownCustomer() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByContractNo("100")).thenReturn(false);
        when(customerQuery.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不存在");
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsUnknownProject() {
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByContractNo("100")).thenReturn(false);
        when(customerQuery.findActiveById(1L)).thenReturn(Optional.of(customer(1L, "客户甲")));
        when(projectQuery.findActiveById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsStaleExpectedVersion() {
        SalesContract entity = existing(9L, StatusConstants.DRAFT, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(9L, request(null, null), 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("版本已变更")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_FAILED);
        verify(repository, never()).save(any());
    }

    @Test
    void update_acceptsMatchingExpectedVersion() {
        SalesContract entity = existing(9L, StatusConstants.DRAFT, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));
        when(customerQuery.findActiveById(1L)).thenReturn(Optional.of(customer(1L, "客户甲")));
        when(projectQuery.findActiveById(2L)).thenReturn(Optional.of(project(2L, "项目乙")));
        when(repository.save(any(SalesContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesContractResponse response = service.update(9L, request(null, null), 3L);

        assertThat(response.status()).isEqualTo(StatusConstants.DRAFT);
        verify(repository).save(entity);
        verify(repository, never()).existsByContractNo(any());
    }

    @Test
    void update_rejectsStatusChangeViaPut() {
        SalesContract entity = existing(9L, StatusConstants.DRAFT, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(9L, request(null, StatusConstants.AUDITED), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态只能通过状态变更操作修改");
        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsDuplicateChangedContractNo() {
        SalesContract entity = existing(9L, StatusConstants.DRAFT, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));
        when(repository.existsByContractNo("HT-2")).thenReturn(true);

        assertThatThrownBy(() -> service.update(9L, request("HT-2", null), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编号已存在");
        verify(repository, never()).save(any());
    }

    @Test
    void update_rejectsAuditedContract() {
        SalesContract entity = existing(9L, StatusConstants.AUDITED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.update(9L, request(null, null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能编辑");
        verify(repository, never()).save(any());
    }

    @Test
    void delete_softDeletesDraftContract() {
        SalesContract entity = existing(9L, StatusConstants.DRAFT, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));
        when(repository.save(any(SalesContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(9L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(repository).save(entity);
    }

    @Test
    void delete_rejectsAuditedContract() {
        SalesContract entity = existing(9L, StatusConstants.AUDITED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");
        verify(repository, never()).save(any());
    }

    @Test
    void detail_rejectsMissingContract() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售合同不存在")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void updateStatus_draftToAudited() {
        assertTransition(StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    @Test
    void updateStatus_auditedToIssued() {
        assertTransition(StatusConstants.AUDITED, StatusConstants.ISSUED);
    }

    @Test
    void updateStatus_issuedToArchived() {
        assertTransition(StatusConstants.ISSUED, StatusConstants.ARCHIVED);
    }

    @Test
    void updateStatus_draftToVoided() {
        assertTransition(StatusConstants.DRAFT, StatusConstants.VOIDED);
    }

    @Test
    void updateStatus_auditedToVoided() {
        assertTransition(StatusConstants.AUDITED, StatusConstants.VOIDED);
    }

    @Test
    void updateStatus_archivedToVoided_allowedWhenNotReferenced() {
        SalesContract entity = existing(9L, StatusConstants.ARCHIVED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));
        when(orderMetricsRepository.existsByProjectIdAndDeletedFlagFalse(2L)).thenReturn(false);
        when(repository.save(any(SalesContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesContractResponse response = service.updateStatus(9L, StatusConstants.VOIDED);

        assertThat(response.status()).isEqualTo(StatusConstants.VOIDED);
    }

    @Test
    void updateStatus_archivedToVoided_rejectedWhenReferencedBySalesOrder() {
        SalesContract entity = existing(9L, StatusConstants.ARCHIVED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));
        when(orderMetricsRepository.existsByProjectIdAndDeletedFlagFalse(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateStatus(9L, StatusConstants.VOIDED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被销售订单引用")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_issuedCannotBeVoided() {
        SalesContract entity = existing(9L, StatusConstants.ISSUED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(9L, StatusConstants.VOIDED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
        verify(orderMetricsRepository, never()).existsByProjectIdAndDeletedFlagFalse(any());
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_reverseTransitionRejected() {
        SalesContract entity = existing(9L, StatusConstants.AUDITED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(9L, StatusConstants.DRAFT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_unknownStatusRejected() {
        SalesContract entity = existing(9L, StatusConstants.AUDITED, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(9L, "不存在的状态"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
        verify(repository, never()).save(any());
    }

    @Test
    void updateStatus_sameStatus_isIdempotentWithoutSave() {
        SalesContract entity = existing(9L, StatusConstants.DRAFT, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));

        SalesContractResponse response = service.updateStatus(9L, StatusConstants.DRAFT);

        assertThat(response.status()).isEqualTo(StatusConstants.DRAFT);
        verify(repository, never()).save(any());
    }

    private void assertTransition(String from, String to) {
        SalesContract entity = existing(9L, from, "HT-1", 3L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(entity));
        when(repository.save(any(SalesContract.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SalesContractResponse response = service.updateStatus(9L, to);

        assertThat(response.status()).isEqualTo(to);
    }

    private SalesContract existing(Long id, String status, String contractNo, Long version) {
        SalesContract entity = new SalesContract();
        entity.setId(id);
        entity.setContractNo(contractNo);
        entity.setStatus(status);
        entity.setVersion(version);
        entity.setCustomerId(1L);
        entity.setCustomerName("旧客户");
        entity.setProjectId(2L);
        entity.setProjectName("旧项目");
        return entity;
    }

    private SalesContractRequest request(String contractNo, String status) {
        return new SalesContractRequest(
                contractNo,
                "年度钢材采购合同",
                1L,
                2L,
                LocalDate.of(2026, 9, 17),
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1000000.00"),
                new BigDecimal("3000.50000000"),
                status,
                "备注"
        );
    }

    private CustomerQuery.CustomerSnapshot customer(Long id, String name) {
        return new CustomerQuery.CustomerSnapshot(id, "C001", name, "项目乙", null, null);
    }

    private ProjectQuery.ProjectSnapshot project(Long id, String name) {
        return new ProjectQuery.ProjectSnapshot(id, name, "简称", 1L, "C001", null, null);
    }
}
