package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.lengthpremium.service.LengthPremiumService;
import com.leo.erp.market.quotation.QuoteDefaults;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.ProjectQuery.ProjectQuoteSettings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 比价项目级配置读写(独立事务)。
 *
 * <p><b>V220 起本类只保存「每品牌运费 + 备注」</b>：品牌/品种/商品键/12米加价/HRB400E 代取/
 * 指定品牌都已归并到项目资料（{@code md_project.quote_*}），读路径按项目资料投影，
 * 写路径只写运费。这样「参与比价的品牌」只有一个配置点（项目资料），
 * 不会再出现「项目允许 A/B、比价却在用 C」的两份事实。</p>
 *
 * <p>响应形状保持不变：{@code brands} 的顺序与品牌来自项目的允许品牌清单，
 * {@code categories} 来自项目资料的品牌品种配置，运费来自本表；其余标量来自项目资料。
 * 写入仍按项目唯一，并发冲突（唯一键/乐观锁）由 {@link QuoteProjectConfigService} 串行化并重试。</p>
 */
@Service
public class QuoteProjectConfigStore {

    private final QuoteProjectConfigRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final EntityManager entityManager;
    private final ProjectQuery projectQuery;

    /** 定尺主数据：项目资料的「12米定尺加价」未配置时，读接口回退到 12米 当前维护的加价。 */
    private final LengthPremiumService lengthPremiumService;

    public QuoteProjectConfigStore(QuoteProjectConfigRepository repository,
                                   SnowflakeIdGenerator snowflakeIdGenerator,
                                   EntityManager entityManager,
                                   ProjectQuery projectQuery,
                                   LengthPremiumService lengthPremiumService) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.entityManager = entityManager;
        this.projectQuery = projectQuery;
        this.lengthPremiumService = lengthPremiumService;
    }

    /** 项目级定尺加价默认值（定尺主数据 12米 优先，取不到回退常量）。 */
    private BigDecimal defaultLengthPremium() {
        return QuoteDefaults.lengthPremiumOrDefault(
                lengthPremiumService == null ? null : lengthPremiumService.activePremiumMap());
    }

    /** 项目取价数据源(MYSTEEL/STEELX)。 */
    private String quoteSourceOf(Long projectId) {
        if (projectId == null || projectQuery == null) {
            return null;
        }
        return projectQuery.findActiveById(projectId)
                .map(ProjectQuery.ProjectSnapshot::quoteSource)
                .orElse(null);
    }

    /** 读项目资料里的报单比价配置；项目不存在/未传 id 时返回 null（调用方按「未配置」处理）。 */
    private ProjectQuoteSettings quoteSettingsOf(Long projectId) {
        if (projectId == null || projectQuery == null) {
            return null;
        }
        return projectQuery.findQuoteSettingsById(projectId).orElse(null);
    }

    /**
     * 查询项目配置；未配置运费时按项目资料返回（{@code version=0}，且**不落库**）。
     *
     * <p>响应形状与归并前一致，值全部来自项目资料 + 本表的运费，因此「先看项目资料、再看运费」
     * 是唯一读口径。</p>
     */
    @Transactional(readOnly = true)
    public QuoteProjectConfigResponse find(Long projectId) {
        ProjectQuoteSettings settings = quoteSettingsOf(projectId);
        return repository.findByProjectIdAndDeletedFlagFalse(projectId)
                .map(config -> toResponse(config, projectId, settings))
                .orElseGet(() -> toResponse(null, projectId, settings));
    }

    /**
     * 保存运费配置（不存在则创建，存在则更新运费/备注）。每次调用开启独立事务。
     *
     * <p>请求里的旧字段（{@code products}/{@code lengthPremium}/{@code hrb400eFallback}/
     * {@code designatedBrands}/{@code brands[].categories}/{@code brands[].sortOrder}）**一律忽略**：
     * 这些配置已归并到项目资料，本接口只负责运费与备注。</p>
     *
     * @throws BusinessException 品牌不在项目允许品牌内（422）、版本冲突（412）
     */
    @Transactional
    public QuoteProjectConfigResponse save(Long projectId, QuoteProjectConfigRequest request, Long expectedVersion) {
        Optional<QuoteProjectConfig> existing = repository.findByProjectIdAndDeletedFlagFalse(projectId);
        Long currentVersion = existing.map(QuoteProjectConfig::getVersion).orElse(0L);
        checkVersion(currentVersion, expectedVersion);

        ProjectQuoteSettings settings = quoteSettingsOf(projectId);
        assertBrandsWithinProjectScope(settings, request.brands());

        QuoteProjectConfig entity = existing.orElseGet(() -> {
            QuoteProjectConfig created = new QuoteProjectConfig();
            created.setId(snowflakeIdGenerator.nextId());
            created.setProjectId(projectId);
            // V220 起 length_premium 等旧标量列只保留回溯、不再被读，但库上仍是 NOT NULL；
            // 新建运费配置时必须补一个合法值，否则只写运费的首次保存会在 INSERT 阶段失败。
            created.setLengthPremium(settings == null || settings.lengthPremium() == null
                    ? defaultLengthPremium()
                    : settings.lengthPremium());
            return created;
        });
        boolean remarkChanged = apply(entity, request);
        // 整体替换改的是 mappedBy 反向集合, 仅变更品牌子集合时 Hibernate 不会把父行标脏,
        // 父 @Version 不会递增; 此时对已有配置显式 FORCE_INCREMENT。若备注已变更, 父行会被自然标脏、
        // 由 @Version 自然递增一次, 再叠加 FORCE_INCREMENT 会变成 +2, 故仅在备注未变时强制自增。
        if (existing.isPresent() && !remarkChanged) {
            entityManager.lock(entity, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        }
        return toResponse(repository.saveAndFlush(entity), projectId, settings);
    }

    /**
     * 以独立只读事务回读配置当前权威版本, 供服务层在写事务提交后覆盖响应版本。
     * <p>FORCE_INCREMENT 在事务提交(方法返回之后)才应用, 存储层在 flush 后构造的 DTO 版本会落后 1;
     * 提交后再回读可保证对外 {@code X-Resource-Version} 与数据库一致。</p>
     */
    @Transactional(readOnly = true)
    public Long currentVersion(Long projectId) {
        return repository.findVersionByProjectId(projectId);
    }

    /** 乐观并发校验: 版本不匹配抛 412(PRECONDITION_FAILED); expectedVersion 为空表示不做校验。 */
    private void checkVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.PRECONDITION_FAILED, "项目配置版本已变更，请刷新后重试");
        }
    }

    /**
     * 写入本表仍负责的字段（备注 + 每品牌运费），返回备注是否实际变化。
     *
     * <p>标量配置已迁到项目资料，这里**不再写** lengthPremium/hrb400eFallback/products/
     * designatedBrands：留着旧值不读，是为了保留回溯能力，也让回滚时数据仍在。</p>
     */
    private boolean apply(QuoteProjectConfig entity, QuoteProjectConfigRequest request) {
        boolean remarkChanged = !Objects.equals(request.remark(), entity.getRemark());
        entity.setRemark(request.remark());
        replaceBrands(entity, request.brands());
        return remarkChanged;
    }

    /**
     * 校验请求里的品牌是否都在项目的「允许的品牌」范围内（{@code md_project.allowed_brands}）。
     *
     * <p>只要项目配了品牌清单就必须校验，**不因力度是 {@code OFF} 而放行**：
     * 配置面写入越界品牌会直接制造「项目允许 A/B、比价却在用 C」的脏数据；
     * 力度只影响业务单据的放行，不影响配置本身能不能写。清单为空 = 不限制，此时不校验。</p>
     */
    private void assertBrandsWithinProjectScope(ProjectQuoteSettings settings,
                                                List<QuoteProjectConfigRequest.BrandRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<String> allowed = settings == null ? List.of() : settings.allowedBrands();
        if (allowed.isEmpty()) {
            return;
        }
        Set<String> scope = new LinkedHashSet<>(allowed);
        List<String> violations = new ArrayList<>();
        for (QuoteProjectConfigRequest.BrandRequest request : requests) {
            if (request == null || request.brandName() == null) {
                continue;
            }
            String brandName = request.brandName().trim();
            if (!brandName.isEmpty() && !scope.contains(brandName) && !violations.contains(brandName)) {
                violations.add(brandName);
            }
        }
        if (!violations.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "以下品牌不在项目「允许的品牌」范围内: " + String.join(", ", violations)
                            + "；请先在项目资料中维护允许品牌，再回来配置运费");
        }
    }

    /**
     * 按品牌名称协调参与品牌: 同名复用原实体(只更新运费与排序, id 不变), 新增才创建,
     * 库中存在但请求未携带的从集合移除。
     * <p>
     * 不能使用 clear + 重新 add: 新子实体带新雪花 ID, Hibernate 会在同一 flush 内先 INSERT
     * 新子行再处理旧子行删除, 从而违反 {@code uk_quote_project_brand(config_id, brand_name)}。
     * 复用同实体可避免任何 INSERT/DELETE, 只产生受控 UPDATE。
     * <p>
     * {@code categories} 列不再写入（品种已归并到项目资料），既有行保留原值以便回溯。
     */
    private void replaceBrands(QuoteProjectConfig entity, List<QuoteProjectConfigRequest.BrandRequest> requests) {
        Map<String, QuoteProjectBrand> existingByBrandName = new HashMap<>();
        for (QuoteProjectBrand brand : entity.getBrands()) {
            existingByBrandName.put(brand.getBrandName(), brand);
        }
        List<QuoteProjectBrand> reconciled = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int index = 0;
        if (requests != null) {
            for (QuoteProjectConfigRequest.BrandRequest request : requests) {
                String brandName = request.brandName() == null ? "" : request.brandName().trim();
                if (brandName.isEmpty() || !seen.add(brandName)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌名称不能为空且不可重复: " + brandName);
                }
                QuoteProjectBrand brand = existingByBrandName.remove(brandName);
                if (brand == null) {
                    brand = new QuoteProjectBrand();
                    brand.setId(snowflakeIdGenerator.nextId());
                    brand.setConfig(entity);
                    brand.setBrandName(brandName);
                }
                brand.setFreight(request.freight() == null ? BigDecimal.ZERO : request.freight());
                // 顺序由请求数组决定：前端按项目允许品牌的顺序提交，与读接口的投影一致。
                brand.setSortOrder(index);
                reconciled.add(brand);
                index += 1;
            }
        }
        // 未被请求命中的 existingByBrandName 余项即需要删除的孤儿, 通过重设集合内容解除关联。
        entity.getBrands().clear();
        entity.getBrands().addAll(reconciled);
    }

    /**
     * 组装响应：形状与归并前完全一致，值全部按「项目资料 + 运费」派生。
     *
     * <p>{@code brands} 只列项目允许的品牌（顺序 = 允许品牌清单顺序），运费取本表，
     * 未配置运费的品牌给 0；{@code categories} 取项目资料的品牌品种，缺键 = 全部品种（空列表）。</p>
     *
     * @param entity    运费载体；未配置运费时为 null（此时 version=0、remark=null）
     * @param projectId 项目 id（entity 为 null 时用它回填响应）
     * @param settings  项目资料的比价配置投影；项目不存在时为 null
     */
    private QuoteProjectConfigResponse toResponse(QuoteProjectConfig entity,
                                                 Long projectId,
                                                 ProjectQuoteSettings settings) {
        Long resolvedProjectId = entity == null ? projectId : entity.getProjectId();
        Map<String, BigDecimal> freightByBrand = new HashMap<>();
        if (entity != null) {
            for (QuoteProjectBrand brand : entity.getBrands()) {
                freightByBrand.put(brand.getBrandName(), brand.getFreight());
            }
        }

        List<QuoteProjectConfigResponse.BrandResponse> brands = new ArrayList<>();
        if (settings != null) {
            int index = 0;
            for (String brandName : settings.allowedBrands()) {
                brands.add(new QuoteProjectConfigResponse.BrandResponse(
                        brandName,
                        freightByBrand.getOrDefault(brandName, BigDecimal.ZERO),
                        settings.brandCategories().getOrDefault(brandName, List.of()),
                        index));
                index += 1;
            }
        }

        BigDecimal lengthPremium = settings == null || settings.lengthPremium() == null
                ? defaultLengthPremium()
                : settings.lengthPremium();
        return new QuoteProjectConfigResponse(
                resolvedProjectId,
                lengthPremium,
                settings != null && settings.hrb400eFallback(),
                settings == null ? List.of() : settings.products(),
                settings == null ? List.of() : settings.designatedBrands(),
                entity == null ? null : entity.getRemark(),
                brands,
                // 新建但尚未 flush 的实体 version 为 null：对外统一按 0 表示「还没有版本」。
                entity == null || entity.getVersion() == null ? 0L : entity.getVersion(),
                quoteSourceOf(resolvedProjectId));
    }
}
