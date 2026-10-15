package com.leo.erp.market.region.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.market.region.domain.entity.MdQuoteRegion;
import com.leo.erp.market.region.repository.MdQuoteRegionRepository;
import com.leo.erp.market.region.web.dto.QuoteRegionRequest;
import com.leo.erp.market.region.web.dto.QuoteRegionResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 取价地区维护服务。
 *
 * <p>把「哪些城市能取价」从配置搬到表里：原先新增城市必须改 application.yml 并重启，
 * 现在业务侧可自行维护，停用城市也不需要删除记录。</p>
 *
 * <p>{@link #activeRegionMap()} 供行情抓取与运行时配置下发使用，带缓存；表为空时返回空 Map，
 * 由调用方回退到 yml 配置与 Java 默认值，保证「表未初始化」时行为与改造前一致。</p>
 */
@Service
public class QuoteRegionService {

    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_SLUG_LENGTH = 64;

    /**
     * 状态写入的唯一出口：状态门禁只允许 ApplyService / CompletionSyncService /
     * CrudStatusGuard 写实体状态，服务层直接 {@code setStatus} 会被
     * {@code ModuleBoundaryVerifier} 判为旁路写。
     */
    private static final CrudStatusGuard<MdQuoteRegion> STATUS_GUARD =
            CrudStatusGuard.forStatusAwareEntities();

    private final MdQuoteRegionRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(QuoteRegionService.class);

    public QuoteRegionService(MdQuoteRegionRepository repository, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public Page<QuoteRegionResponse> page(PageQuery query, String keyword, String status) {
        Specification<MdQuoteRegion> spec = Specs.<MdQuoteRegion>notDeleted()
                .and(Specs.keywordLike(keyword, "regionName", "slug", "remark"))
                .and(Specs.equalIfPresent("status", normalizeOptionalStatus(status)));
        return repository.findAll(spec, query.toPageable("sortOrder")).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuoteRegionResponse detail(Long id) {
        return toResponse(requireActive(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public QuoteRegionResponse create(QuoteRegionRequest request) {
        String regionName = normalize(request.regionName(), "城市名称", MAX_NAME_LENGTH);
        String slug = normalize(request.slug(), "子域名", MAX_SLUG_LENGTH);
        String status = requireStatus(request.status());
        if (repository.existsByRegionNameAndDeletedFlagFalse(regionName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "城市「" + regionName + "」已存在");
        }
        if (repository.existsBySlugAndDeletedFlagFalse(slug)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "子域名「" + slug + "」已被其他城市占用");
        }
        MdQuoteRegion entity = new MdQuoteRegion();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        entity.setRegionName(regionName);
        entity.setSlug(slug);
        entity.setSortOrder(request.sortOrder());
        STATUS_GUARD.writeStatus(entity, status);
        entity.setRemark(trimToNull(request.remark()));
        MdQuoteRegion saved = repository.saveAndFlush(entity);
        operationLogger.created(saved, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public QuoteRegionResponse update(Long id, QuoteRegionRequest request) {
        MdQuoteRegion entity = requireActive(id);
        String regionName = normalize(request.regionName(), "城市名称", MAX_NAME_LENGTH);
        String slug = normalize(request.slug(), "子域名", MAX_SLUG_LENGTH);
        String status = requireStatus(request.status());
        if (repository.existsByRegionNameAndDeletedFlagFalseAndIdNot(regionName, id)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "城市「" + regionName + "」已被其他记录占用");
        }
        if (repository.existsBySlugAndDeletedFlagFalseAndIdNot(slug, id)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "子域名「" + slug + "」已被其他记录占用");
        }
        entity.setRegionName(regionName);
        entity.setSlug(slug);
        entity.setSortOrder(request.sortOrder());
        STATUS_GUARD.writeStatus(entity, status);
        entity.setRemark(trimToNull(request.remark()));
        MdQuoteRegion saved = repository.saveAndFlush(entity);
        operationLogger.updated(saved, id);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public void delete(Long id) {
        MdQuoteRegion entity = requireActive(id);
        entity.setDeletedFlag(true);
        repository.saveAndFlush(entity);
        operationLogger.deleted(entity, id);
    }

    /**
     * 正常状态地区映射：城市中文名 → 子域名 slug，按 sortOrder 排序。
     *
     * <p>返回可变 Map 以便写入 Redis 缓存；表为空时返回空 Map，调用方据此回退配置兜底。</p>
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'quote-region:active'")
    public Map<String, String> activeRegionMap() {
        List<MdQuoteRegion> rows = repository
                .findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL);
        Map<String, String> mapping = new LinkedHashMap<>();
        for (MdQuoteRegion row : rows) {
            mapping.putIfAbsent(row.getRegionName(), row.getSlug());
        }
        return mapping;
    }

    private MdQuoteRegion requireActive(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "取价地区不存在"));
    }

    private String requireStatus(String raw) {
        String status = raw == null ? "" : raw.trim();
        if (!StatusConstants.NORMAL.equals(status) && !StatusConstants.DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "状态只能是「" + StatusConstants.NORMAL + "」或「" + StatusConstants.DISABLED + "」");
        }
        return status;
    }

    private String normalizeOptionalStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return requireStatus(raw);
    }

    private String normalize(String raw, String fieldLabel, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldLabel + "不能为空");
        }
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    fieldLabel + "不能超过" + maxLength + "个字符");
        }
        return value;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private QuoteRegionResponse toResponse(MdQuoteRegion entity) {
        return new QuoteRegionResponse(
                entity.getId(),
                entity.getRegionName(),
                entity.getSlug(),
                entity.getSortOrder(),
                entity.getStatus(),
                entity.getRemark()
        );
    }
}
