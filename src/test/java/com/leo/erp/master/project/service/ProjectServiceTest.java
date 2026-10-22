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

    @org.junit.jupiter.api.BeforeEach
    void stubSettlementCompany() {
        org.mockito.Mockito.lenient()
                .when(companySettingService.requireActiveSettlementCompanySnapshot(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> new SettlementCompanySnapshot(
                        invocation.getArgument(0), "结算公司A"));
    }

    private ProjectService service() {
        return new ProjectService(snowflakeIdGenerator, projectRepository, projectMapper, referenceGuard,
                customerRepository, companySettingService, codeIssuanceService, referenceSnapshotSyncService);
    }

    private ProjectRequest request(Long customerId, String customerCode) {
        return new ProjectRequest("111", "项目A", "项A", "地址", "张三",
                customerId, customerCode, 55L, "结算公司A", "正常", "备注");
    }

    private Customer customer(Long id, String code) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerCode(code);
        return customer;
    }

    @Test
    void create_resolvesCustomerIdentityAndSettlementCompanyFromRequest() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
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
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(new ProjectRequest("  111  ", "  项目A  ", " 项A ", " 地址 ", " 张三 ",
                7L, " C007 ", 55L, "结算公司A", "正常", " 备注 "));

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
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(request(7L, "C999")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("客户ID与客户编码不一致");
        verify(projectRepository, never()).save(any());
    }

    @Test
    void create_legacyCustomerCode_fallsBackToCustomerLookup() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C007"))
                .thenReturn(Optional.of(customer(7L, "C007")));
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
        // 结算主体来自请求（客户资料已不维护默认主体）
        assertThat(entity.getSettlementCompanyId()).isEqualTo(55L);
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
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(companySettingService.requireActiveSettlementCompanySnapshot(66L))
                .thenReturn(new SettlementCompanySnapshot(66L, "结算公司B"));
        when(codeIssuanceService.resolve("project", "P001", "111")).thenReturn("P001");
        when(projectRepository.save(entity)).thenReturn(entity);

        // 请求省略结算主体 → 沿用实体上已配置的主体（不因缺省被清空）
        service().update(5L, new ProjectRequest("111", "项目A", "项A", "地址", "张三",
                7L, "C007", null, null, "正常", "备注"));

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
                .thenReturn(Optional.of(customer(7L, "C007")));
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

    /**
     * 新建项目时取价数据源/地区必须<b>显式落库</b>(MYSTEEL / 杭州), 不能留 null。
     *
     * <p>回归的真实缺陷: 库里 7 个项目的 quote_source/quote_region 全是 NULL, 项目选项
     * 因此不下发这两个字段, 前端无从判断"该项目支持哪个数据源", 取网价整条链路只能表现成
     * "暂无可选/不支持网价" —— 用户看不出是没配置。缺省值落库后取价配置才是可查、可回显的。</p>
     *
     * <p>反例钉: 若把空值重新写成 null(旧行为), 本用例立即变红。</p>
     */
    @Test
    void create_defaultsQuoteSourceAndRegionWhenAbsent() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        // quoteSource/quoteRegion 都不传(等同于用户在页面上没动这两个字段)
        service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", null, null,
                null, null,
                null, null, null, null,
                null, null, null,
                null, null, null));

        Project entity = savedHolder[0];
        assertThat(entity.getQuoteSource()).isEqualTo("MYSTEEL");
        assertThat(entity.getQuoteRegion()).isEqualTo("杭州");
    }

    /** 用户显式选择的取价数据源/地区必须原样保留, 不被缺省值覆盖。 */
    @Test
    void create_keepsExplicitQuoteSourceAndRegion() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", null, null,
                "STEELX", "嘉兴",
                null, null, null, null,
                null, null, null,
                null, null, null));

        Project entity = savedHolder[0];
        assertThat(entity.getQuoteSource()).isEqualTo("STEELX");
        assertThat(entity.getQuoteRegion()).isEqualTo("嘉兴");
    }

    @Test
    void create_persistsPriceFloatAdd() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", "ADD",
                new java.math.BigDecimal("30.00"), null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceFloatMode()).isEqualTo("ADD");
        assertThat(entity.getPriceFloatValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void create_clearsFloatWhenModeAbsent() {
        Project[] savedHolder = new Project[1];
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(snowflakeIdGenerator.nextId()).thenReturn(100L);
        when(codeIssuanceService.resolve(eq("project"), any(), anyString())).thenReturn("111");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });

        // 未配置方向时应清空幅度, 避免残留半配置
        service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", null,
                new java.math.BigDecimal("30.00"), null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceFloatMode()).isNull();
        assertThat(entity.getPriceFloatValue()).isNull();
    }

    @Test
    void create_rejectsInvalidFloatMode() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", "UP",
                new java.math.BigDecimal("30.00"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void create_rejectsFloatModeWithoutValue() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", "SUBTRACT", null, null)))
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
        Customer customer = customer(7L, "C007");
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
                .thenReturn(Optional.of(customer(7L, "C007")));
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
                .thenReturn(Optional.of(customer(7L, "C007")));
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
                7L, "C007", 55L, "结算公司A", "正常", null, null, null, null,
                allowedBrands, allowedMaterials, allowedBrandSeverity, allowedMaterialSeverity,
                null, null, null, null, null, null);
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
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(
                allowedSpecRequest(List.of("沙钢"), List.of(), "DISABLED", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("允许品牌的处理方式仅支持 OFF/WARN/BLOCK");
        verify(projectRepository, never()).save(any(Project.class));
    }

    // ==================== 取价基准(按日期自动取价)配置 ====================

    /** 未配置时落到默认: 送货日期当天 + 偏移 0 + 不按休市前结算 + 自动取当日最新时段。 */
    @Test
    void create_pricingBasisAbsent_defaultsToDeliveryAuto() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(pricingRequest(null, null, null, null, null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceDateBasis()).isEqualTo("DELIVERY");
        assertThat(entity.getPriceDateOffsetDays()).isZero();
        assertThat(entity.getPriceClosedDayPrevSettle()).isFalse();
        assertThat(entity.getPricePeriodPolicy()).isEqualTo("AUTO");
        assertThat(entity.getPricePeriodFixed()).isNull();
    }

    /** 报单日基准下偏移与「休市按前一交易日结算」开关都要落库; 取值大小写不敏感。 */
    @Test
    void create_orderBasis_persistsOffsetAndClosedDaySettleSwitch() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(pricingRequest("order", 2, true, null, null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceDateBasis()).isEqualTo("ORDER");
        assertThat(entity.getPriceDateOffsetDays()).isEqualTo(2);
        assertThat(entity.getPriceClosedDayPrevSettle()).isTrue();
        assertThat(entity.getPricePeriodPolicy()).isEqualTo("AUTO");
    }

    /** 非报单日基准时偏移与休市开关必须复位, 避免切换基准后残留旧值继续影响取价。 */
    @Test
    void create_nonOrderBasis_resetsOffsetAndSettleSwitch() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(pricingRequest("DELIVERY", 5, true, null, null));

        Project entity = savedHolder[0];
        assertThat(entity.getPriceDateBasis()).isEqualTo("DELIVERY");
        assertThat(entity.getPriceDateOffsetDays()).isZero();
        assertThat(entity.getPriceClosedDayPrevSettle()).isFalse();
    }

    @Test
    void create_fixedPeriodPolicy_persistsFixedPeriod() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(pricingRequest(null, null, null, "fixed", "下午"));

        Project entity = savedHolder[0];
        assertThat(entity.getPricePeriodPolicy()).isEqualTo("FIXED");
        assertThat(entity.getPricePeriodFixed()).isEqualTo("下午");
    }

    /** 切回自动/手工策略时固定时段必须清空, 否则残留旧时段会被取价解析当成有效输入。 */
    @Test
    void create_nonFixedPeriodPolicy_clearsFixedPeriod() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(pricingRequest(null, null, null, "MANUAL", "下午"));

        Project entity = savedHolder[0];
        assertThat(entity.getPricePeriodPolicy()).isEqualTo("MANUAL");
        assertThat(entity.getPricePeriodFixed()).isNull();
    }

    @Test
    void create_fixedPeriodPolicyWithoutPeriod_rejected() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(pricingRequest(null, null, null, "FIXED", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("固定时段策略必须选择取价时段");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void create_fixedPeriodPolicyWithUnknownPeriod_rejected() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(pricingRequest(null, null, null, "FIXED", "傍晚")))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("固定取价时段仅支持 上午/中午/下午");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void create_rejectsUnknownPriceDateBasis() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(pricingRequest("WEEKLY", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("取价基准仅支持 DELIVERY");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void create_rejectsUnknownPricePeriodPolicy() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(pricingRequest(null, null, null, "SOMETIMES", null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("时段策略仅支持 AUTO");
        verify(projectRepository, never()).save(any(Project.class));
    }

    @Test
    void create_rejectsOffsetDaysAboveLimit() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(pricingRequest("ORDER", 31, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("取价偏移交易日数必须在 0 到 30 之间");
        verify(projectRepository, never()).save(any(Project.class));
    }

    /**
     * 非报单日基准下偏移是「不适用字段」, 但**非法值仍然报错**:
     * 偏移范围是无条件校验(与 md_project 的 CHECK 约束、取价解析侧一致), 传 31 说明调用方有 bug,
     * 不该被「非 ORDER 就复位」顺带吞掉; 只有合法值才会在非 ORDER 基准下被复位为 0。
     */
    @Test
    void create_offsetOutOfRangeWithNonOrderBasis_isRejected() {
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));

        assertThatThrownBy(() -> service().create(pricingRequest("DELIVERY", 31, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("取价偏移交易日数必须在 0 到 30 之间");
        verify(projectRepository, never()).save(any(Project.class));
    }

    /** 合法偏移在非报单日基准下属于「不适用字段」: 复位为 0, 不报错。 */
    @Test
    void create_validOffsetWithNonOrderBasis_isResetNotRejected() {
        Project[] savedHolder = new Project[1];
        stubCreateSave(savedHolder);

        service().create(pricingRequest("DELIVERY", 5, null, null, null));

        assertThat(savedHolder[0].getPriceDateOffsetDays()).isZero();
    }

    /** 项目从报单日基准切走时, 偏移与休市开关必须随之一并复位。 */
    @Test
    void update_switchingAwayFromOrderBasis_resetsOffsetAndSettleSwitch() {
        Project entity = new Project();
        entity.setId(5L);
        entity.setProjectName("项目A");
        entity.setProjectCode("P001");
        entity.setPriceDateBasis("ORDER");
        entity.setPriceDateOffsetDays(2);
        entity.setPriceClosedDayPrevSettle(true);
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(codeIssuanceService.resolve("project", "P001", "111")).thenReturn("P001");
        when(projectRepository.save(entity)).thenReturn(entity);

        service().update(5L, pricingRequest("DELIVERY", null, null, null, null));

        assertThat(entity.getPriceDateBasis()).isEqualTo("DELIVERY");
        assertThat(entity.getPriceDateOffsetDays()).isZero();
        assertThat(entity.getPriceClosedDayPrevSettle()).isFalse();
    }

    /** 项目从固定时段策略切走时, 固定时段必须清空。 */
    @Test
    void update_switchingAwayFromFixedPolicy_clearsFixedPeriod() {
        Project entity = new Project();
        entity.setId(5L);
        entity.setProjectName("项目A");
        entity.setProjectCode("P001");
        entity.setPricePeriodPolicy("FIXED");
        entity.setPricePeriodFixed("中午");
        when(projectRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(customerRepository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(customer(7L, "C007")));
        when(codeIssuanceService.resolve("project", "P001", "111")).thenReturn("P001");
        when(projectRepository.save(entity)).thenReturn(entity);

        service().update(5L, pricingRequest(null, null, null, "AUTO", null));

        assertThat(entity.getPricePeriodPolicy()).isEqualTo("AUTO");
        assertThat(entity.getPricePeriodFixed()).isNull();
    }

    private ProjectRequest pricingRequest(String priceDateBasis, Integer priceDateOffsetDays,
                                          Boolean priceClosedDayPrevSettle, String pricePeriodPolicy,
                                          String pricePeriodFixed) {
        return new ProjectRequest("111", "项目A", null, null, null,
                7L, "C007", 55L, "结算公司A", "正常", null, null, null, null,
                null, null, null, null,
                priceDateBasis, priceDateOffsetDays, priceClosedDayPrevSettle,
                pricePeriodPolicy, pricePeriodFixed, null);
    }
}
