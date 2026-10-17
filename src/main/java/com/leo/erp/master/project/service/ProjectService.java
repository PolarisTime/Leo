package com.leo.erp.master.project.service;

import com.leo.erp.common.support.ValidationMessages;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.api.ProjectPricingBasisQuery.Basis;
import com.leo.erp.master.api.ProjectPricingBasisQuery.PeriodPolicy;
import com.leo.erp.master.api.ProjectPricingBasisQuery.QuotePeriod;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.service.CrudVisibilityPolicy;
import com.leo.erp.common.support.CsvValues;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.StatusTransition;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.api.ProjectProcurementScopeQuery.Enforcement;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.mapper.ProjectMapper;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectRequest;
import com.leo.erp.master.project.web.dto.ProjectOptionResponse;
import com.leo.erp.master.project.web.dto.ProjectResponse;
import com.leo.erp.system.company.api.SettlementCompanySnapshot;
import com.leo.erp.system.company.service.CompanySettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class ProjectService {

    /** 取价偏移交易日上限: 与 md_project 的 CHECK 约束一致。 */
    private static final int MAX_PRICE_DATE_OFFSET_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final String CODE_MODULE_KEY = ModuleKeys.PROJECT;
    private static final String PROJECT_OPTIONS_CACHE_KEY = "leo:project:all";
    /** 网价浮动方向常量。 */
    private static final String PRICE_FLOAT_ADD = "ADD";
    private static final String PRICE_FLOAT_SUBTRACT = "SUBTRACT";
    /** 取价数据源常量。 */
    private static final String PRICE_SOURCE_MYSTEEL = "MYSTEEL";
    private static final String PRICE_SOURCE_STEELX = "STEELX";
    /**
     * 取价数据源缺省值。
     *
     * <p><b>必须显式落库, 不能写成 null</b>: 运行时虽会按 MYSTEEL/杭州 兜底解析取价, 但库里
     * 留空会让"这个项目到底支不支持网价"没有任何数据依据 —— 项目选项下发时省略 quoteSource,
     * 前端也就无从判断, 只能表现成"暂无可选/不支持网价"。写入显式默认值后, 取价配置才是可查的。</p>
     */
    private static final String DEFAULT_PRICE_SOURCE = PRICE_SOURCE_MYSTEEL;
    private static final String DEFAULT_QUOTE_REGION = "杭州";
    private static final CrudStatusGuard<Project> STATUS_GUARD = CrudStatusGuard.withoutStatus();
    private static final CrudVisibilityPolicy VISIBILITY_POLICY = new CrudVisibilityPolicy();
    private static final Set<StatusTransition> NO_STATUS_TRANSITIONS = Set.of();

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(ProjectService.class);
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final MasterDataReferenceGuard referenceGuard;
    private final CustomerRepository customerRepository;
    private final CompanySettingService companySettingService;
    private final MasterDataCodeIssuanceService codeIssuanceService;
    private final com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService;

    @Autowired
    public ProjectService(SnowflakeIdGenerator snowflakeIdGenerator,
                          ProjectRepository projectRepository,
                          ProjectMapper projectMapper,
                          MasterDataReferenceGuard referenceGuard,
                          CustomerRepository customerRepository,
                          CompanySettingService companySettingService,
                          MasterDataCodeIssuanceService codeIssuanceService,
                          com.leo.erp.master.service.ReferenceSnapshotSyncService referenceSnapshotSyncService) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.referenceGuard = referenceGuard;
        this.customerRepository = customerRepository;
        this.companySettingService = companySettingService;
        this.codeIssuanceService = codeIssuanceService;
        this.referenceSnapshotSyncService = referenceSnapshotSyncService;
    }

    @Transactional(readOnly = true)
    public ProjectResponse detail(Long id) {
        return toResponse(requireActiveProject(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PROJECT_OPTIONS, allEntries = true)
    public ProjectResponse create(ProjectRequest request) {
        ProjectRequest normalized = normalizeCreateRequest(request);
        codeIssuanceService.validate(CODE_MODULE_KEY, normalized.projectCode());
        Project entity = new Project();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        apply(entity, normalized);
        Project saved = saveCreatedProject(entity);
        operationLogger.created(entity, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PROJECT_OPTIONS, allEntries = true)
    public ProjectResponse update(Long id, ProjectRequest request) {
        Project entity = requireActiveProject(id);
        String currentName = entity.getProjectName();
        ProjectRequest normalized = normalizeUpdateRequest(entity, request);
        apply(entity, normalized);
        String nextName = entity.getProjectName();
        Project saved = projectRepository.save(entity);
        ProjectResponse response = toResponse(saved);
        operationLogger.updated(entity, id);
        if (!currentName.equals(nextName)) {
            referenceSnapshotSyncService.syncProjectName(id, nextName);
        }
        return response;
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PROJECT_OPTIONS, allEntries = true)
    public ProjectResponse updateStatus(Long id, String status) {
        Project entity = requireActiveProject(id);
        String currentStatus = STATUS_GUARD.resolveStatus(entity).orElse("");
        String nextStatus = STATUS_GUARD.normalizeRequiredStatus(status);
        if (currentStatus.equals(nextStatus)) {
            return toResponse(entity);
        }
        STATUS_GUARD.validateStatusTransition(NO_STATUS_TRANSITIONS, currentStatus, nextStatus);
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.STATUS_CHANGE_UNSUPPORTED);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_PROJECT_OPTIONS, allEntries = true)
    public void delete(Long id) {
        Project entity = requireActiveProject(id);
        if (referenceGuard != null) {
            referenceGuard.assertNoReferences("该项目", projectReferences(entity));
        }
        entity.setDeletedFlag(true);
        projectRepository.save(entity);
        operationLogger.deleted(entity, id);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> page(PageQuery query, String keyword, String status, Long customerId) {
        Specification<Project> spec = Specs.<Project>notDeleted()
                .and(Specs.keywordLike(keyword,
                        "projectCode", "projectName", "projectNameAbbr",
                        "customerCode", "projectManager"))
                .and(Specs.equalIfPresent("status", status))
                .and(customerIdentity(customerId));
        return projectRepository
                .findAll(VISIBILITY_POLICY.applyDeletedVisibility(spec, false), query.toPageable("id"))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_PROJECT_OPTIONS, key = "'" + PROJECT_OPTIONS_CACHE_KEY + ":' + #customerId",
            unless = "#result == null || #result.isEmpty()")
    public List<ProjectOptionResponse> listActiveOptions(Long customerId) {
        Customer customer = customerRepository.findByIdAndDeletedFlagFalse(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.CUSTOMER_NOT_FOUND));
        String customerCode = trimToNull(customer.getCustomerCode());
        return projectRepository.findActiveOptionsByCustomerIdentity(
                        customer.getId(),
                        customerCode,
                        StatusConstants.NORMAL
                ).stream()
                .map(project -> new ProjectOptionResponse(
                        project.getId(),
                        project.getProjectCode() + " / " + project.getProjectName(),
                        project.getId(),
                        project.getCustomerId() == null ? customer.getId() : project.getCustomerId(),
                        project.getCustomerCode(),
                        project.getProjectCode(),
                        project.getProjectName(),
                        project.getProjectNameAbbr(),
                        project.getSettlementCompanyId(),
                        project.getSettlementCompanyName(),
                        project.getPriceFloatMode(),
                        project.getPriceFloatValue(),
                        project.getLastPriceRuleId(),
                        project.getQuoteSource(),
                        project.getQuoteRegion(),
                        CsvValues.split(project.getAllowedBrands()),
                        CsvValues.split(project.getAllowedMaterials()),
                        project.getAllowedBrandSeverity(),
                        project.getAllowedMaterialSeverity()
                ))
                .toList();
    }

    private Project requireActiveProject(Long id) {
        return projectRepository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, ValidationMessages.PROJECT_NOT_FOUND));
    }

    private ProjectRequest normalizeCreateRequest(ProjectRequest request) {
        return normalizeCustomerIdentity(request, null);
    }

    private ProjectRequest normalizeUpdateRequest(Project entity, ProjectRequest request) {
        Long settlementCompanyId = request.settlementCompanyId() == null
                ? entity.getSettlementCompanyId()
                : request.settlementCompanyId();
        String settlementCompanyName = request.settlementCompanyId() == null
                ? entity.getSettlementCompanyName()
                : request.settlementCompanyName();
        return normalizeCustomerIdentity(
                new ProjectRequest(
                        request.projectCode(), request.projectName(), request.projectNameAbbr(),
                        request.projectAddress(), request.projectManager(), request.customerId(),
                        request.customerCode(), settlementCompanyId, settlementCompanyName,
                        request.status(), request.priceFloatMode(), request.priceFloatValue(),
                        request.quoteSource(), request.quoteRegion(),
                        request.allowedBrands(), request.allowedMaterials(),
                        request.allowedBrandSeverity(), request.allowedMaterialSeverity(),
                        request.priceDateBasis(), request.priceDateOffsetDays(),
                        request.priceClosedDayPrevSettle(), request.pricePeriodPolicy(),
                        request.pricePeriodFixed(), request.remark()
                ),
                entity
        );
    }

    private void apply(Project entity, ProjectRequest request) {
        entity.setProjectCode(codeIssuanceService.resolve(
                CODE_MODULE_KEY,
                entity.getProjectCode(),
                request.projectCode()
        ));
        entity.setProjectName(requireText(request.projectName(), "项目名称不能为空"));
        entity.setProjectNameAbbr(trimToNull(request.projectNameAbbr()));
        entity.setProjectAddress(trimToNull(request.projectAddress()));
        entity.setProjectManager(trimToNull(request.projectManager()));
        entity.setCustomerId(request.customerId());
        entity.setCustomerCode(trimToNull(request.customerCode()));
        entity.setSettlementCompanyId(request.settlementCompanyId());
        entity.setSettlementCompanyName(trimToNull(request.settlementCompanyName()));
        entity.setStatus(request.status());
        applyPriceFloat(entity, request);
        applyQuoteSource(entity, request);
        applyAllowedSpec(entity, request);
        entity.setRemark(trimToNull(request.remark()));
    }

    /**
     * 归一化项目允许品牌/材质: 去空白、去重后按逗号分隔落库; 空列表表示不限制(置空)。
     * <p>
     * 单项长度与条数上限由 {@link ProjectRequest} 的 Bean Validation 兜底, 这里只做清洗,
     * 让「空数组」「全为空白」「重复值」三种前端输入统一收敛为同一个库内表示。
     */
    private void applyAllowedSpec(Project entity, ProjectRequest request) {
        String allowedBrands = CsvValues.join(request.allowedBrands());
        String allowedMaterials = CsvValues.join(request.allowedMaterials());
        entity.setAllowedBrands(allowedBrands);
        entity.setAllowedMaterials(allowedMaterials);
        entity.setAllowedBrandSeverity(resolveAllowedSeverity(allowedBrands, request.allowedBrandSeverity(), "允许品牌"));
        entity.setAllowedMaterialSeverity(resolveAllowedSeverity(allowedMaterials, request.allowedMaterialSeverity(), "允许材质"));
        applyPricingBasis(entity, request);
    }

    /**
     * 归一化取价基准配置。
     *
     * <p>非适用字段一律复位（偏移与休市开关只在报单日基准有意义，固定时段只在 FIXED 有意义）：
     * 否则会出现「切回自动策略却仍带着旧固定时段」这类半配置状态，脏值又会被取价解析当成有效输入。</p>
     */
    private void applyPricingBasis(Project entity, ProjectRequest request) {
        Basis basis = resolvePricingBasis(request.priceDateBasis());
        // 偏移量无条件校验 0..30(与 md_project 的 CHECK 约束、取价解析侧一致):
        // 传了非法值说明调用方有 bug, 不该被「非 ORDER 就复位」顺带吞掉。
        int requestedOffsetDays = resolvePriceOffsetDays(request.priceDateOffsetDays());
        int offsetDays = basis == Basis.ORDER ? requestedOffsetDays : 0;
        boolean settlePrev = basis == Basis.ORDER && Boolean.TRUE.equals(request.priceClosedDayPrevSettle());
        PeriodPolicy policy = resolvePricePeriodPolicy(request.pricePeriodPolicy());
        String fixedPeriod = policy == PeriodPolicy.FIXED ? requireFixedPeriod(request.pricePeriodFixed()) : null;
        entity.setPriceDateBasis(basis.name());
        entity.setPriceDateOffsetDays(offsetDays);
        entity.setPriceClosedDayPrevSettle(settlePrev);
        entity.setPricePeriodPolicy(policy.name());
        entity.setPricePeriodFixed(fixedPeriod);
    }

    /** 取价基准: 空表示送货日期当天(默认)。 */
    private Basis resolvePricingBasis(String requested) {
        String normalized = trimToNull(requested);
        if (normalized == null) {
            return Basis.DELIVERY;
        }
        Basis parsed = Basis.parse(normalized);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "取价基准仅支持 DELIVERY(送货日期当天)/ORDER(报单日期之后第N个交易日)/MANUAL(每次手工选择)");
        }
        return parsed;
    }

    private int resolvePriceOffsetDays(Integer requested) {
        if (requested == null) {
            return 0;
        }
        if (requested < 0 || requested > MAX_PRICE_DATE_OFFSET_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "取价偏移交易日数必须在 0 到 " + MAX_PRICE_DATE_OFFSET_DAYS + " 之间");
        }
        return requested;
    }

    private PeriodPolicy resolvePricePeriodPolicy(String requested) {
        String normalized = trimToNull(requested);
        if (normalized == null) {
            return PeriodPolicy.AUTO;
        }
        PeriodPolicy parsed = PeriodPolicy.parse(normalized);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "时段策略仅支持 AUTO(取当日最新时段)/FIXED(固定时段)/MANUAL(每次手工选择)");
        }
        return parsed;
    }

    private String requireFixedPeriod(String requested) {
        String normalized = trimToNull(requested);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "固定时段策略必须选择取价时段");
        }
        if (!QuotePeriod.isValid(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "固定取价时段仅支持 上午/中午/下午");
        }
        return normalized;
    }

    /**
     * 归一化允许范围的处理方式。
     *
     * <p>清单为空 = 不限制, 力度随之清空(避免留下「配了力度却没有清单」的半配置状态);
     * 清单非空但未指定力度时落到 WARN, 这样 V176 已配置的项目升级后行为不变,
     * 不需要数据回填, 也不需要业务重新保存一次。
     */
    private String resolveAllowedSeverity(String allowedValues, String requested, String label) {
        if (allowedValues == null) {
            return null;
        }
        String normalized = trimToNull(requested);
        if (normalized == null) {
            return Enforcement.WARN.name();
        }
        if (!Enforcement.isValid(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    label + "的处理方式仅支持 OFF/WARN/BLOCK");
        }
        return Enforcement.normalize(normalized).name();
    }

    /** 归一化并校验网价浮动: 方向与幅度必须同时为空或同时有值; 方向仅允许 ADD/SUBTRACT。 */
    private void applyPriceFloat(Project entity, ProjectRequest request) {
        String mode = trimToNull(request.priceFloatMode());
        if (mode == null) {
            entity.setPriceFloatMode(null);
            entity.setPriceFloatValue(null);
            return;
        }
        if (!PRICE_FLOAT_ADD.equals(mode) && !PRICE_FLOAT_SUBTRACT.equals(mode)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "网价浮动方向仅支持加价或减价");
        }
        if (request.priceFloatValue() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写网价浮动幅度");
        }
        entity.setPriceFloatMode(mode);
        entity.setPriceFloatValue(TradeItemCalculator.scaleAmount(request.priceFloatValue()));
    }

    /**
     * 归一化取价数据源与地区: 仅允许 MYSTEEL/STEELX。
     *
     * <p><b>缺省值显式落库</b>(而不是留 null): 取价解析虽然对空值有运行时兜底, 但"项目支持
     * 哪个数据源/哪个地区"必须能在库里查到 —— 留空会让项目选项不下发这两个字段, 前端无法
     * 判断该项目能不能取网价, 只能表现成"暂无可选/不支持网价", 用户也看不出是没配置。</p>
     */
    private void applyQuoteSource(Project entity, ProjectRequest request) {
        String source = trimToNull(request.quoteSource());
        if (source == null) {
            entity.setQuoteSource(DEFAULT_PRICE_SOURCE);
        } else if (PRICE_SOURCE_MYSTEEL.equals(source) || PRICE_SOURCE_STEELX.equals(source)) {
            entity.setQuoteSource(source);
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取价数据源仅支持 MYSTEEL 或 STEELX");
        }
        String region = trimToNull(request.quoteRegion());
        /*
         * MYSTEEL 只按全局市场(默认杭州)报价, 地区对它没有意义; 但把默认地区也落下来, 前端
         * "取价地区"才有可回显的值, 不会显示成空(空值在界面上读起来像"没配置")。
         * STEELX 的地区由用户自选, 未选时同样回落默认地区 —— 解析侧本就按默认市场取价。
         */
        entity.setQuoteRegion(region == null ? DEFAULT_QUOTE_REGION : region);
    }

    private Project saveCreatedProject(Project entity) {
        Project saved = projectRepository.save(entity);
        codeIssuanceService.consume(CODE_MODULE_KEY, saved.getProjectCode());
        return saved;
    }

    private ProjectResponse toResponse(Project entity) {
        return projectMapper.toResponse(entity);
    }

    private ProjectRequest normalizeCustomerIdentity(ProjectRequest request, Project existingEntity) {
        if (customerRepository == null) {
            return resolveSettlementCompany(request, null, null);
        }
        String requestedCustomerCode = trimToNull(request.customerCode());
        if (request.customerId() == null && requestedCustomerCode == null) {
            return request;
        }
        Customer customer;
        if (request.customerId() == null) {
            customer = customerRepository.findByCustomerCodeAndDeletedFlagFalse(requestedCustomerCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.CUSTOMER_NOT_FOUND));
            log.warn(
                    "identity fallback used: module=project, field=customerId, "
                            + "reason=legacy-customer-code, resolvedId={}",
                    customer.getId()
            );
        } else {
            customer = customerRepository.findByIdAndDeletedFlagFalse(request.customerId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.CUSTOMER_NOT_FOUND));
        }
        String customerCode = trimToNull(customer.getCustomerCode());
        if (requestedCustomerCode != null && !requestedCustomerCode.equals(customerCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "客户ID与客户编码不一致");
        }
        Long fallbackSettlementCompanyId = request.settlementCompanyId();
        String fallbackSettlementCompanyName = request.settlementCompanyName();
        if (fallbackSettlementCompanyId == null && existingEntity == null) {
            fallbackSettlementCompanyId = customer.getDefaultSettlementCompanyId();
            fallbackSettlementCompanyName = customer.getDefaultSettlementCompanyName();
        }
        return resolveSettlementCompany(new ProjectRequest(
                request.projectCode(),
                request.projectName(),
                request.projectNameAbbr(),
                request.projectAddress(),
                request.projectManager(),
                customer.getId(),
                customerCode,
                fallbackSettlementCompanyId,
                fallbackSettlementCompanyName,
                request.status(),
                request.priceFloatMode(),
                request.priceFloatValue(),
                request.quoteSource(),
                request.quoteRegion(),
                request.allowedBrands(),
                request.allowedMaterials(),
                request.allowedBrandSeverity(),
                request.allowedMaterialSeverity(),
                request.priceDateBasis(),
                request.priceDateOffsetDays(),
                request.priceClosedDayPrevSettle(),
                request.pricePeriodPolicy(),
                request.pricePeriodFixed(),
                request.remark()
        ), customer.getDefaultSettlementCompanyId(), customer.getDefaultSettlementCompanyName());
    }

    private ProjectRequest resolveSettlementCompany(ProjectRequest request,
                                                    Long fallbackId,
                                                    String fallbackName) {
        Long settlementCompanyId = request.settlementCompanyId() == null
                ? fallbackId
                : request.settlementCompanyId();
        String settlementCompanyName = request.settlementCompanyName() == null
                ? fallbackName
                : request.settlementCompanyName();
        if (settlementCompanyId == null || companySettingService == null) {
            return new ProjectRequest(
                    request.projectCode(), request.projectName(), request.projectNameAbbr(),
                    request.projectAddress(), request.projectManager(), request.customerId(),
                    request.customerCode(), settlementCompanyId, settlementCompanyName,
                    request.status(), request.priceFloatMode(), request.priceFloatValue(),
                    request.quoteSource(), request.quoteRegion(),
                    request.allowedBrands(), request.allowedMaterials(),
                    request.allowedBrandSeverity(), request.allowedMaterialSeverity(),
                    request.priceDateBasis(), request.priceDateOffsetDays(),
                    request.priceClosedDayPrevSettle(), request.pricePeriodPolicy(),
                    request.pricePeriodFixed(), request.remark()
            );
        }
        SettlementCompanySnapshot company = companySettingService
                .requireActiveSettlementCompanySnapshot(settlementCompanyId);
        return new ProjectRequest(
                request.projectCode(), request.projectName(), request.projectNameAbbr(),
                request.projectAddress(), request.projectManager(), request.customerId(),
                request.customerCode(), company.id(), company.name(),
                request.status(), request.priceFloatMode(), request.priceFloatValue(),
                request.quoteSource(), request.quoteRegion(),
                request.allowedBrands(), request.allowedMaterials(),
                request.allowedBrandSeverity(), request.allowedMaterialSeverity(),
                request.priceDateBasis(), request.priceDateOffsetDays(),
                request.priceClosedDayPrevSettle(), request.pricePeriodPolicy(),
                request.pricePeriodFixed(), request.remark()
        );
    }

    private Specification<Project> customerIdentity(Long customerId) {
        if (customerId == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        Customer customer = customerRepository.findByIdAndDeletedFlagFalse(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, ValidationMessages.CUSTOMER_NOT_FOUND));
        String customerCode = trimToNull(customer.getCustomerCode());
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.equal(root.get("customerId"), customer.getId()),
                criteriaBuilder.and(
                        criteriaBuilder.isNull(root.get("customerId")),
                        criteriaBuilder.equal(root.get("customerCode"), customerCode)
                )
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private List<ReferenceCheck> projectReferences(Project entity) {
        Long projectId = entity.getId();
        return List.of(
                ReferenceCheck.active("so_sales_order", "project_id", projectId),
                ReferenceCheck.active("so_sales_contract", "project_id", projectId),
                ReferenceCheck.active("ct_sales_contract", "project_id", projectId),
                ReferenceCheck.active("so_sales_outbound", "project_id", projectId),
                ReferenceCheck.active("st_customer_statement", "project_id", projectId),
                ReferenceCheck.ofActiveParent(
                        "st_customer_statement_item",
                        "project_id",
                        projectId,
                        "st_customer_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_receipt", "project_id", projectId),
                ReferenceCheck.ofActiveParent(
                        "lg_freight_bill_item",
                        "project_id",
                        projectId,
                        "lg_freight_bill",
                        "bill_id"
                ),
                ReferenceCheck.ofActiveParent(
                        "st_freight_statement_item",
                        "project_id",
                        projectId,
                        "st_freight_statement",
                        "statement_id"
                ),
                ReferenceCheck.active("fm_ledger_adjustment", "project_id", projectId)
        );
    }
}
