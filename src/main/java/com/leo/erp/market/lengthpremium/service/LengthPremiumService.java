package com.leo.erp.market.lengthpremium.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.service.CrudStatusGuard;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.market.lengthpremium.domain.entity.MdLengthPremium;
import com.leo.erp.market.lengthpremium.repository.MdLengthPremiumRepository;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumRequest;
import com.leo.erp.market.lengthpremium.web.dto.LengthPremiumResponse;
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
 * 定尺加价维护服务。
 *
 * <p>把「12米 +30 元/吨」这类规则从 application.yml 与 Java 默认值的重复定义搬到表里，
 * 调整加价不再需要改代码重启。{@link #activePremiumMap()} 供行情匹配热路径使用，带缓存；
 * 表为空时返回空 Map，由调用方回退到配置兜底，保证「表未初始化」时行为与改造前一致。</p>
 */
@Service
public class LengthPremiumService {

    private static final int MAX_LENGTH_NAME = 32;

    /**
     * 状态写入的唯一出口：状态门禁只允许 ApplyService / CompletionSyncService /
     * CrudStatusGuard 写实体状态，服务层直接 {@code setStatus} 会被
     * {@code ModuleBoundaryVerifier} 判为旁路写。
     */
    private static final CrudStatusGuard<MdLengthPremium> STATUS_GUARD =
            CrudStatusGuard.forStatusAwareEntities();

    private final MdLengthPremiumRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(LengthPremiumService.class);

    public LengthPremiumService(MdLengthPremiumRepository repository, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public Page<LengthPremiumResponse> page(PageQuery query, String keyword, String status) {
        Specification<MdLengthPremium> spec = Specs.<MdLengthPremium>notDeleted()
                .and(Specs.keywordLike(keyword, "lengthName", "remark"))
                .and(Specs.equalIfPresent("status", normalizeOptionalStatus(status)));
        return repository.findAll(spec, query.toPageable("sortOrder")).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LengthPremiumResponse detail(Long id) {
        return toResponse(requireActive(id));
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public LengthPremiumResponse create(LengthPremiumRequest request) {
        String lengthName = normalizeLengthName(request.lengthName());
        String status = requireStatus(request.status());
        if (repository.existsByLengthNameAndDeletedFlagFalse(lengthName)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "定尺「" + lengthName + "」已存在加价规则");
        }
        MdLengthPremium entity = new MdLengthPremium();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        entity.setLengthName(lengthName);
        entity.setPremium(request.premium());
        entity.setSortOrder(request.sortOrder());
        STATUS_GUARD.writeStatus(entity, status);
        entity.setRemark(trimToNull(request.remark()));
        MdLengthPremium saved = repository.saveAndFlush(entity);
        operationLogger.created(saved, entityId);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public LengthPremiumResponse update(Long id, LengthPremiumRequest request) {
        MdLengthPremium entity = requireActive(id);
        String lengthName = normalizeLengthName(request.lengthName());
        String status = requireStatus(request.status());
        if (repository.existsByLengthNameAndDeletedFlagFalseAndIdNot(lengthName, id)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "定尺「" + lengthName + "」已被其他记录占用");
        }
        entity.setLengthName(lengthName);
        entity.setPremium(request.premium());
        entity.setSortOrder(request.sortOrder());
        STATUS_GUARD.writeStatus(entity, status);
        entity.setRemark(trimToNull(request.remark()));
        MdLengthPremium saved = repository.saveAndFlush(entity);
        operationLogger.updated(saved, id);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public void delete(Long id) {
        MdLengthPremium entity = requireActive(id);
        entity.setDeletedFlag(true);
        repository.saveAndFlush(entity);
        operationLogger.deleted(entity, id);
    }

    /**
     * 正常状态加价映射：定尺名称 → 每吨加价，按 sortOrder 排序。
     *
     * <p>返回可变 Map 以便写入 Redis 缓存；表为空时返回空 Map，调用方据此回退配置兜底。</p>
     */
    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.CACHE_OPTIONS, key = "'length-premium:active'")
    public Map<String, Integer> activePremiumMap() {
        List<MdLengthPremium> rows = repository
                .findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL);
        Map<String, Integer> mapping = new LinkedHashMap<>();
        for (MdLengthPremium row : rows) {
            if (row.getPremium() != null) {
                mapping.putIfAbsent(row.getLengthName(), row.getPremium());
            }
        }
        return mapping;
    }

    private MdLengthPremium requireActive(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "定尺加价规则不存在"));
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

    private String normalizeLengthName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "定尺名称不能为空");
        }
        if (value.length() > MAX_LENGTH_NAME) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "定尺名称不能超过" + MAX_LENGTH_NAME + "个字符");
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

    private LengthPremiumResponse toResponse(MdLengthPremium entity) {
        return new LengthPremiumResponse(
                entity.getId(),
                entity.getLengthName(),
                entity.getPremium(),
                entity.getSortOrder(),
                entity.getStatus(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
