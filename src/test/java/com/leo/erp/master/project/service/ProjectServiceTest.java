package com.leo.erp.master.project.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
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
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.service.ReferenceSnapshotSyncService;
import com.leo.erp.system.company.api.SettlementCompanySnapshot;
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
    void create_trimsUserVisibleStrings() {        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(new ProjectRequest("  111  ", "  项目A  ", " 项A ", " 地址 ", " 张三 ",
                7L, " C007 ", null, null, "正常", " 备注 "));

        Project entity = savedHolder[0];
        assertThat(entity.getProjectName()).isEqualTo("项目A");
        assertThat(entity.getProjectNameAbbr()).isEqualTo("项A");
        assertThat(entity.getProjectAddress()).isEqualTo("地址");
        assertThat(entity.getProjectManager()).isEqualTo("张三");
        assertThat(entity.getCustomerCode()).isEqualTo("C007");
        assertThat(entity.getRemark()).isEqualTo("备注");
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

    @Test
    void cacheAnnotations_coverOptionsReadAndAllWritePaths() throws Exception {
        Method create = ProjectService.class.getMethod("create", ProjectRequest.class);
        Method update = ProjectService.class.getMethod("update", Long.class, ProjectRequest.class);
        Method updateStatus = ProjectService.class.getMethod("updateStatus", Long.class, String.class);
        Method delete = ProjectService.class.getMethod("delete", Long.class);
        Method listActiveOptions = ProjectService.class.getMethod("listActiveOptions", Long.class);

        // 项目选项 key 含 customerId，只能整 region 失效；因此必须使用专用 region，
        // 否则一次项目改动会把供应商/客户/物流商/仓库等全部 options 缓存一并清空。
        for (Method method : List.of(create, update, updateStatus, delete)) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_PROJECT_OPTIONS);
            assertThat(cacheEvict.allEntries()).isTrue();
        }
        Cacheable cacheable = listActiveOptions.getAnnotation(Cacheable.class);
        assertThat(cacheable).isNotNull();
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_PROJECT_OPTIONS);
        assertThat(cacheable.key()).isEqualTo("'leo:project:all:' + #customerId");
        assertThat(cacheable.unless()).isEqualTo("#result == null || #result.isEmpty()");
    }

    @Test
    void create_persistsPriceFloatAdd() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", null, null, "正常", "ADD",
                new java.math.BigDecimal("30.00"), null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceFloatMode()).isEqualTo("ADD");
        assertThat(entity.getPriceFloatValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void create_clearsFloatWhenModeAbsent() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        // 未配置方向时应清空幅度, 避免残留半配置
        service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", null, null, "正常", null,
                new java.math.BigDecimal("30.00"), null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceFloatMode()).isNull();
        assertThat(entity.getPriceFloatValue()).isNull();
    }

    @Test
    void create_rejectsInvalidFloatMode() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));

        assertThatThrownBy(() -> service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", null, null, "正常", "UP",
                new java.math.BigDecimal("30.00"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void create_rejectsFloatModeWithoutValue() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));

        assertThatThrownBy(() -> service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", null, null, "正常", "SUBTRACT", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(projectRepository, never()).save(any(Project.class));
    }

    /** 允许品牌/材质: 空白、重复项收敛为去重后的逗号分隔文本。 */
    @Test
    void create_normalizesAllowedSpec() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(allowedSpecRequest(
                List.of(" 沙钢 ", "沙钢", "", "   ", "永钢"),
                List.of("HRB400E", " HRB400 ")));

        Project entity = savedHolder[0];
        assertThat(entity.getAllowedBrands()).isEqualTo("沙钢,永钢");
        assertThat(entity.getAllowedMaterials()).isEqualTo("HRB400E,HRB400");
    }

    /** 未配置(或全为空白)时保持「不限制」语义: 库内为 null, 不能落成空串。 */
    @Test
    void create_blankAllowedSpec_persistsNull() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(allowedSpecRequest(List.of("   "), List.of()));

        Project entity = savedHolder[0];
        assertThat(entity.getAllowedBrands()).isNull();
        assertThat(entity.getAllowedMaterials()).isNull();
    }

    /** 项目下拉要带上允许品牌/材质, 销售订单导入采购订单时才能在前端做匹配预警。 */
    @Test
    void listActiveOptions_splitsAllowedSpecIntoLists() {
        Customer customer = customer(7L, "C007", null, null);
        when(customerRepository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(customer));
        Project project = new Project();
        project.setId(5L);
        project.setProjectCode("P001");
        project.setProjectName("项目A");
        project.setCustomerCode("C007");
        project.setAllowedBrands("沙钢,永钢");
        project.setAllowedMaterials("HRB400E");
        when(projectRepository.findActiveOptionsByCustomerIdentity(7L, "C007", "正常"))
                .thenReturn(List.of(project));

        List<ProjectOptionResponse> options = service().listActiveOptions(7L);

        assertThat(options).hasSize(1);
        assertThat(options.get(0).allowedBrands()).containsExactly("沙钢", "永钢");
        assertThat(options.get(0).allowedMaterials()).containsExactly("HRB400E");
    }

    /** 未配置白名单的项目选项必须是空列表, 前端据此判定「不限制」。 */
    @Test
    void listActiveOptions_absentAllowedSpec_returnsEmptyLists() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        Project project = new Project();
        project.setId(5L);
        project.setProjectCode("P001");
        project.setProjectName("项目A");
        project.setCustomerCode("C007");
        when(projectRepository.findActiveOptionsByCustomerIdentity(7L, "C007", "正常"))
                .thenReturn(List.of(project));

        List<ProjectOptionResponse> options = service().listActiveOptions(7L);

        assertThat(options.get(0).allowedBrands()).isEmpty();
        assertThat(options.get(0).allowedMaterials()).isEmpty();
    }

    private void stubCreateSave(Project[] savedHolder) {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
    }

    private ProjectRequest allowedSpecRequest(List<String> allowedBrands, List<String> allowedMaterials) {
        return allowedSpecRequest(allowedBrands, allowedMaterials, null, null);
    }

    private ProjectRequest allowedSpecRequest(List<String> allowedBrands,
                                              List<String> allowedMaterials,
                                              String allowedBrandSeverity,
                                              String allowedMaterialSeverity) {
        return new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", null, null, "正常", null, null, null, null,
                allowedBrands, allowedMaterials, allowedBrandSeverity, allowedMaterialSeverity, null);
    }

    /** 清单非空但未指定力度时落到 WARN: V176 已配置的项目升级后行为必须不变。 */
    @Test
    void create_allowedSpecWithoutSeverity_defaultsToWarn() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(allowedSpecRequest(List.of("沙钢"), List.of("HRB400")));

        Project entity = savedHolder[0];
        assertThat(entity.getAllowedBrandSeverity()).isEqualTo("WARN");
        assertThat(entity.getAllowedMaterialSeverity()).isEqualTo("WARN");
    }

    @Test
    void create_persistsBlockSeverityPerDimension() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(allowedSpecRequest(List.of("沙钢"), List.of("HRB400"), "block", "OFF"));

        Project entity = savedHolder[0];
        assertThat(entity.getAllowedBrandSeverity()).isEqualTo("BLOCK");
        assertThat(entity.getAllowedMaterialSeverity()).isEqualTo("OFF");
    }

    /** 清单为空 = 不限制, 力度必须一并清空, 避免留下半配置状态。 */
    @Test
    void create_emptyAllowedSpec_clearsSeverity() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(allowedSpecRequest(List.of(), List.of(), "BLOCK", "BLOCK"));

        Project entity = savedHolder[0];
        assertThat(entity.getAllowedBrandSeverity()).isNull();
        assertThat(entity.getAllowedMaterialSeverity()).isNull();
    }

    @Test
    void create_rejectsUnknownSeverity() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007", null, null)));

        assertThatThrownBy(() -> service().create(
                allowedSpecRequest(List.of("沙钢"), List.of(), "DISABLED", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("允许品牌的处理方式仅支持 OFF/WARN/BLOCK");
        verify(projectRepository, never()).save(any(Project.class));
    }
}
