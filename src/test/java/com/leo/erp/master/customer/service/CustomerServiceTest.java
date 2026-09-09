package com.leo.erp.master.customer.service;

import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.mapper.CustomerMapper;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.customer.web.dto.CustomerRequest;
import com.leo.erp.master.customer.web.dto.CustomerResponse;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.util.List;
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
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private CustomerMapper customerMapper;
    @Mock
    private MasterDataReferenceGuard referenceGuard;
    @Mock
    private CompanySettingService companySettingService;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ReferenceSnapshotSyncService referenceSnapshotSyncService;

    private CustomerService service() {
        return new CustomerService(customerRepository, snowflakeIdGenerator, customerMapper,
                referenceGuard, companySettingService, codeIssuanceService, projectRepository,
                referenceSnapshotSyncService);
    }

    private CustomerRequest request(String customerName) {
        return new CustomerRequest("KH001", customerName, "联系人", "13800000000",
                "上海", "月结", null, null, null, 9L, "正常", "备注");
    }

    private CustomerResponse baseResponse(String customerName) {
        return new CustomerResponse(5L, "KH001", customerName, "联系人", "13800000000",
                "上海", "月结", null, null, null, 9L, "结算公司C", "正常", "备注", "");
    }

    @Test
    void create_assignsSnowflakeIdResolvesSettlementCompanyAndDefaultsProjectName() {
        Customer[] savedHolder = new Customer[1];
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("结算公司C");
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(codeIssuanceService.resolve("customer", null, "KH001")).thenReturn("KH001");
        when(companySettingService.requireActiveSettlementCompany(9L)).thenReturn(company);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(customerMapper.toResponse(any(Customer.class))).thenReturn(baseResponse("客户A"));
        when(projectRepository.findAllByCustomerIdentity(77L, "KH001")).thenReturn(List.of());

        service().create(request("客户A"));

        Customer entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(77L);
        assertThat(entity.getCustomerCode()).isEqualTo("KH001");
        assertThat(entity.getCustomerName()).isEqualTo("客户A");
        assertThat(entity.getDefaultSettlementCompanyId()).isEqualTo(9L);
        assertThat(entity.getDefaultSettlementCompanyName()).isEqualTo("结算公司C");
        assertThat(entity.getProjectName()).isEqualTo("客户A");
        assertThat(entity.getStatus()).isEqualTo("正常");
        verify(codeIssuanceService).validate("customer", "KH001");
        verify(codeIssuanceService).consume("customer", "KH001");
    }

    @Test
    void create_nullSettlementCompanyService_toleratedWithIdOnlySnapshot() {
        CustomerService nullCollaboratorService = new CustomerService(customerRepository, snowflakeIdGenerator,
                customerMapper, referenceGuard, null, codeIssuanceService, projectRepository,
                referenceSnapshotSyncService);
        Customer[] savedHolder = new Customer[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(codeIssuanceService.resolve("customer", null, "KH001")).thenReturn("KH001");
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(customerMapper.toResponse(any(Customer.class))).thenReturn(baseResponse("客户A"));
        when(projectRepository.findAllByCustomerIdentity(77L, "KH001")).thenReturn(List.of());

        nullCollaboratorService.create(request("客户A"));

        assertThat(savedHolder[0].getDefaultSettlementCompanyId()).isEqualTo(9L);
        assertThat(savedHolder[0].getDefaultSettlementCompanyName()).isNull();
        verify(companySettingService, never()).requireActiveSettlementCompany(any());
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(customerRepository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("客户不存在");
    }

    @Test
    void detail_enrichesProjectNames() {
        Customer entity = new Customer();
        entity.setId(5L);
        entity.setCustomerCode("KH001");
        entity.setCustomerName("客户A");
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        Project project = new Project();
        project.setProjectName("项目A");
        project.setCustomerId(5L);
        when(projectRepository.findAllByCustomerIdentity(5L, "KH001")).thenReturn(List.of(project));
        CustomerResponse base = new CustomerResponse(5L, "KH001", "客户A", null, null,
                null, null, null, null, null, null, null, "正常", null, null);
        when(customerMapper.toResponse(entity)).thenReturn(base);

        CustomerResponse response = service().detail(5L);

        assertThat(response.projectNames()).isEqualTo("项目A");
    }

    @Test
    void update_nameChanged_syncsReferenceSnapshot() {
        Customer entity = new Customer();
        entity.setId(5L);
        entity.setCustomerName("旧名称");
        entity.setCustomerCode("KH001");
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("结算公司C");
        when(companySettingService.requireActiveSettlementCompany(9L)).thenReturn(company);
        when(codeIssuanceService.resolve("customer", "KH001", "KH001")).thenReturn("KH001");
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.save(entity)).thenReturn(entity);
        when(customerMapper.toResponse(entity)).thenReturn(baseResponse("新名称"));
        when(projectRepository.findAllByCustomerIdentity(5L, "KH001")).thenReturn(List.of());

        service().update(5L, request("新名称"));

        verify(referenceSnapshotSyncService).syncCustomerName(5L, "新名称");
    }

    @Test
    void update_nameUnchanged_doesNotSyncReferenceSnapshot() {
        Customer entity = new Customer();
        entity.setId(5L);
        entity.setCustomerName("客户A");
        entity.setCustomerCode("KH001");
        CompanySetting company = new CompanySetting();
        company.setId(9L);
        company.setCompanyName("结算公司C");
        when(companySettingService.requireActiveSettlementCompany(9L)).thenReturn(company);
        when(codeIssuanceService.resolve("customer", "KH001", "KH001")).thenReturn("KH001");
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.save(entity)).thenReturn(entity);
        when(customerMapper.toResponse(entity)).thenReturn(baseResponse("客户A"));
        when(projectRepository.findAllByCustomerIdentity(5L, "KH001")).thenReturn(List.of());

        service().update(5L, request("客户A"));

        verify(referenceSnapshotSyncService, never()).syncCustomerName(any(), anyString());
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Customer()));

        assertThatThrownBy(() -> service().updateStatus(5L, "  "))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new Customer()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_referenced_blockedBeforeSoftDelete() {
        Customer entity = new Customer();
        entity.setId(5L);
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "该客户被引用，无法删除"))
                .when(referenceGuard).assertNoReferences(eq("该客户"), any());

        assertThatThrownBy(() -> service().delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该客户被引用，无法删除");
        assertThat(entity.isDeletedFlag()).isFalse();
        verify(customerRepository, never()).save(any());
    }

    @Test
    void delete_softDeletesEntity() {
        Customer entity = new Customer();
        entity.setId(5L);
        when(customerRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.save(entity)).thenReturn(entity);

        service().delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(customerRepository).save(entity);
    }

    @Test
    void cacheAnnotations_preservedOnPublicWriteAndReadMethods() throws Exception {
        Method create = CustomerService.class.getMethod("create", CustomerRequest.class);
        Method update = CustomerService.class.getMethod("update", Long.class, CustomerRequest.class);
        Method updateStatus = CustomerService.class.getMethod("updateStatus", Long.class, String.class);
        Method delete = CustomerService.class.getMethod("delete", Long.class);
        Method listActiveOptions = CustomerService.class.getMethod("listActiveOptions");

        for (Method method : List.of(create, update, updateStatus, delete)) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
            assertThat(cacheEvict.key()).isEqualTo("'leo:customer:all'");
        }
        Cacheable cacheable = listActiveOptions.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:customer:all'");
        assertThat(cacheable.unless()).isEqualTo("#result == null || #result.isEmpty()");
    }
}
