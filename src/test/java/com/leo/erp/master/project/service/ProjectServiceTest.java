package com.leo.erp.master.project.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.system.company.api.SettlementCompanySnapshot;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private MasterDataReferenceGuard referenceGuard;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CompanySettingService companySettingService;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;
    @Mock
    private ReferenceSnapshotSyncService referenceSnapshotSyncService;

    private ProjectService service() {
        return new ProjectService(snowflakeIdGenerator, projectRepository, projectMapper, referenceGuard,
                customerRepository, companySettingService, codeIssuanceService, referenceSnapshotSyncService);
    }

    private ProjectRequest request(Long customerId, String customerCode) {
        return new ProjectRequest("111", "项目A", "项A", "地址", "张三",
                customerId, customerCode, null, null, "正常", "备注");
    }

    private Customer customer(Long id, String code, Long settlementCompanyId, String settlementCompanyName) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerCode(code);
        customer.setDefaultSettlementCompanyId(settlementCompanyId);
        customer.setDefaultSettlementCompanyName(settlementCompanyName);
        return customer;
    }

    @Test
    void create_resolvesCustomerIdentityAndDefaultSettlementCompany() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", 55L, "结算公司A")));
        when(companySettingService.requireActiveSettlementCompanySnapshot(55L))
                .thenReturn(new SettlementCompanySnapshot(55L, "结算公司A"));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve("project", null, "111")).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(request(7L, "C007"));

        Project entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(100L);
        assertThat(entity.getProjectCode()).isEqualTo("111");
        assertThat(entity.getCustomerId()).isEqualTo(7L);
        assertThat(entity.getCustomerCode()).isEqualTo("C007");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(55L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算公司A");
        assertThat(entity.getStatus()).isEqualTo("正常");
        verify(codeIssuanceService).validate("project", "111");
        verify(codeIssuanceService).consume("project", "111");
    }

    @Test
    void create_customerNotFound_rejected() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(request(7L, "C007")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户不存在");
        verify(projectRepository, never()).save(any());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void create_customerIdMismatchWithCode_rejected() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));

        assertThatThrownBy(() -> service().create(request(7L, "C999")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户ID与客户编码不一致");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void create_legacyCustomerCode_fallsBackToCustomerLookup() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C007"))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve("project", null, "111")).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(request(null, "C007"));

        Project entity = savedHolder[0];
        assertThat(entity.getCustomerId()).isEqualTo(7L);
        assertThat(entity.getCustomerCode()).isEqualTo("C007");
        assertThat(entity.getSettlementCompanyId()).isNull();
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(projectRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("项目不存在");
    }

    @Test
    void update_missingSettlementCompany_fallsBackToExistingEntityValue() {
        Project entity = new Project();
        entity.setId(5L);
        entity.setProjectName("旧项目名");
        entity.setProjectCode("P001");
        entity.setSettlementCompanyId(66L);
        entity.setSettlementCompanyName("结算公司B");
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", 55L, "结算公司A")));
        when(companySettingService.requireActiveSettlementCompanySnapshot(66L))
                .thenReturn(new SettlementCompanySnapshot(66L, "结算公司B"));
        when(codeIssuanceService.resolve("project", "P001", "111")).thenReturn("P001");
        when(projectRepository.save(entity)).thenReturn(entity);

        service().update(5L, request(7L, "C007"));

        assertThat(entity.getSettlementCompanyId()).isEqualTo(66L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("结算公司B");
    }

    @Test
    void update_nameUnchanged_skipsReferenceSnapshotSync() {
        Project entity = new Project();
        entity.setId(5L);
        entity.setProjectName("项目A");
        entity.setProjectCode("P001");
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        when(codeIssuanceService.resolve("project", "P001", "111")).thenReturn("P001");
        when(projectRepository.save(entity)).thenReturn(entity);

        service().update(5L, request(7L, "C007"));

        verify(referenceSnapshotSyncService, never()).syncProjectName(any(), anyString());
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Project()));

        assertThatThrownBy(() -> service().updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Project()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_referenced_blockedBeforeSoftDelete() {
        Project entity = new Project();
        entity.setId(5L);
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "项目被引用"))
                .when(referenceGuard).assertNoReferences(eq("该项目"), any());

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("项目被引用");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(projectRepository, never()).save(any());
    }

    @Test
    void delete_softDeletesEntity() {
        Project entity = new Project();
        entity.setId(5L);
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(projectRepository.save(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(projectRepository).save(entity);
    }
}
