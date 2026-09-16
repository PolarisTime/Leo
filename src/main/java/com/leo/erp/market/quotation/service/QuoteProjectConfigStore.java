package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 比价项目级配置读写(独立事务)。
 * 写入按项目唯一; 并发冲突(唯一键/乐观锁)由 {@link QuoteProjectConfigService} 串行化并重试。
 */
@Service
public class QuoteProjectConfigStore {

    private static final BigDecimal DEFAULT_LENGTH_PREMIUM = new BigDecimal("30");

    private final QuoteProjectConfigRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public QuoteProjectConfigStore(QuoteProjectConfigRepository repository,
                                   SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 查询项目配置; 未配置时返回默认空配置(不落库)。 */
    @Transactional(readOnly = true)
    public QuoteProjectConfigResponse find(Long projectId) {
        return repository.findByProjectIdAndDeletedFlagFalse(projectId)
                .map(this::toResponse)
                .orElseGet(() -> new QuoteProjectConfigResponse(
                        projectId, DEFAULT_LENGTH_PREMIUM, false, List.of(), List.of(), null, List.of(), null));
    }

    /** 保存项目配置(不存在则创建, 存在则整体替换)。每次调用开启独立事务。 */
    @Transactional
    public QuoteProjectConfigResponse save(Long projectId, QuoteProjectConfigRequest request, Long expectedVersion) {
        Optional<QuoteProjectConfig> existing = repository.findByProjectIdAndDeletedFlagFalse(projectId);
        checkVersion(existing.map(QuoteProjectConfig::getVersion).orElse(null), expectedVersion);
        QuoteProjectConfig entity = existing.orElseGet(() -> {
            QuoteProjectConfig created = new QuoteProjectConfig();
            created.setId(snowflakeIdGenerator.nextId());
            created.setProjectId(projectId);
            return created;
        });
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    /** 乐观并发校验: expectedVersion 为空表示不做校验(兼容旧调用)。 */
    private void checkVersion(Long currentVersion, Long expectedVersion) {
        if (expectedVersion != null && !expectedVersion.equals(currentVersion)) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "数据已被他人修改，请刷新后重试");
        }
    }

    private void apply(QuoteProjectConfig entity, QuoteProjectConfigRequest request) {
        entity.setLengthPremium(request.lengthPremium() == null ? DEFAULT_LENGTH_PREMIUM : request.lengthPremium());
        entity.setHrb400eFallback(Boolean.TRUE.equals(request.hrb400eFallback()));
        entity.setProducts(join(request.products()));
        entity.setDesignatedBrands(join(request.designatedBrands()));
        entity.setRemark(request.remark());
        replaceBrands(entity, request.brands());
    }

    private void replaceBrands(QuoteProjectConfig entity, List<QuoteProjectConfigRequest.BrandRequest> requests) {
        entity.getBrands().clear();
        if (requests == null) {
            return;
        }
        Set<String> seen = new LinkedHashSet<>();
        int index = 0;
        for (QuoteProjectConfigRequest.BrandRequest request : requests) {
            String brandName = request.brandName() == null ? "" : request.brandName().trim();
            if (brandName.isEmpty() || !seen.add(brandName)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌名称不能为空且不可重复: " + brandName);
            }
            QuoteProjectBrand brand = new QuoteProjectBrand();
            brand.setId(snowflakeIdGenerator.nextId());
            brand.setConfig(entity);
            brand.setBrandName(brandName);
            brand.setFreight(request.freight() == null ? BigDecimal.ZERO : request.freight());
            brand.setCategories(join(request.categories()));
            brand.setSortOrder(request.sortOrder() == null ? index : request.sortOrder());
            entity.getBrands().add(brand);
            index += 1;
        }
    }

    private QuoteProjectConfigResponse toResponse(QuoteProjectConfig entity) {
        List<QuoteProjectConfigResponse.BrandResponse> brands = entity.getBrands().stream()
                .map(brand -> new QuoteProjectConfigResponse.BrandResponse(
                        brand.getBrandName(), brand.getFreight(), split(brand.getCategories()), brand.getSortOrder()))
                .toList();
        return new QuoteProjectConfigResponse(
                entity.getProjectId(),
                entity.getLengthPremium(),
                entity.isHrb400eFallback(),
                split(entity.getProducts()),
                split(entity.getDesignatedBrands()),
                entity.getRemark(),
                brands,
                entity.getVersion());
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                cleaned.add(value.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return result;
    }
}
