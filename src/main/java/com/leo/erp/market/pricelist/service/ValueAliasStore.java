package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.api.ApiFieldError;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.repository.ValueAliasRepository;
import com.leo.erp.market.pricelist.web.dto.ValueAliasRequest;
import com.leo.erp.market.pricelist.web.dto.ValueAliasResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 值映射/别名的增删改查(软删除)。
 *
 * <p>校验口径:</p>
 * <ul>
 *   <li>维度必须是 {@code CATEGORY}/{@code MATERIAL}/{@code LENGTH}/{@code BRAND} 之一, 否则 422;</li>
 *   <li>源值/目标值必填、≤{@code varchar(64)}, 自映射(归一后源值 == 目标值)422 —— 自映射无意义;</li>
 *   <li>同维度下同源值的未删除记录唯一: 应用层先查并返回 409, 并发下由部分唯一索引
 *       {@code uk_value_alias_dimension_source} 兜底并统一映射为 409; 软删后可重建同源值;</li>
 *   <li>定尺维度的源值/目标值写入前按定尺口径结构归一({@code 9m}/{@code 9 M} → {@code 9米},
 *       {@code -}/空 → 空串), 与归一化查表键同口径, 避免同一写法在表里各存一行。</li>
 * </ul>
 *
 * <p><b>缓存</b>: 任何写操作都以 {@code @CacheEvict(allEntries = true)} 失效
 * {@link CacheConfig#CACHE_OPTIONS}(与 {@code ProjectService} 同口径), 因此"改完映射立刻生效"。</p>
 */
@Service
public class ValueAliasStore {

    private static final int VALUE_MAX_LENGTH = 64;
    private static final String DEFAULT_SORT = "id";

    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(ValueAliasStore.class);
    private final ValueAliasRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ValueAliasStore(ValueAliasRepository repository, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public ValueAliasResponse detail(Long id) {
        return toResponse(requireActive(id, "值映射不存在"));
    }

    /**
     * 分页查询。
     *
     * @param dimension 维度筛选, 空表示不限(非法维度 → 422)
     * @param keyword   源值/目标值模糊匹配, 空表示不限
     */
    @Transactional(readOnly = true)
    public Page<ValueAliasResponse> page(PageQuery query, String dimension, String keyword) {
        ValueAliasDimension dimensionFilter = parseDimensionOrNull(dimension);
        Specification<ValueAlias> specification = Specs.<ValueAlias>notDeleted()
                .and(Specs.keywordLike(keyword, "sourceValue", "targetValue"))
                .and((root, criteriaQuery, builder) -> dimensionFilter == null
                        ? builder.conjunction()
                        : builder.equal(root.get("dimension"), dimensionFilter));
        return repository.findAll(specification, query.toPageable(DEFAULT_SORT)).map(ValueAliasStore::toResponse);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public ValueAliasResponse create(ValueAliasRequest request) {
        ValueAliasDimension dimension = parseDimension(request.dimension());
        String sourceValue = requireValue(request.sourceValue(), "源值", dimension);
        String targetValue = requireValue(request.targetValue(), "目标值", dimension);
        rejectSelfMapping(sourceValue, targetValue);
        if (repository.existsByDimensionAndSourceValueAndDeletedFlagFalse(dimension, sourceValue)) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                    "该维度下源值已存在映射: " + dimension + " / " + sourceValue);
        }
        ValueAlias entity = new ValueAlias();
        long id = snowflakeIdGenerator.nextId();
        entity.setId(id);
        entity.setDimension(dimension);
        entity.setSourceValue(sourceValue);
        entity.setTargetValue(targetValue);
        entity.setRemark(blankToNull(request.remark()));
        ValueAlias saved = repository.saveAndFlush(entity);
        operationLogger.created(saved, id);
        return toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public ValueAliasResponse update(Long id, ValueAliasRequest request) {
        ValueAlias entity = requireActive(id, "值映射不存在");
        ValueAliasDimension dimension = parseDimension(request.dimension());
        String sourceValue = requireValue(request.sourceValue(), "源值", dimension);
        String targetValue = requireValue(request.targetValue(), "目标值", dimension);
        rejectSelfMapping(sourceValue, targetValue);
        boolean keyChanged = entity.getDimension() != dimension
                || !Objects.equals(entity.getSourceValue(), sourceValue);
        if (keyChanged) {
            repository.findByDimensionAndSourceValueAndDeletedFlagFalse(dimension, sourceValue)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                                "该维度下源值已存在映射: " + dimension + " / " + sourceValue);
                    });
        }
        entity.setDimension(dimension);
        entity.setSourceValue(sourceValue);
        entity.setTargetValue(targetValue);
        entity.setRemark(blankToNull(request.remark()));
        ValueAlias saved = repository.saveAndFlush(entity);
        operationLogger.updated(saved, id);
        return toResponse(saved);
    }

    /**
     * 软删除(幂等): 已软删的记录重复删除同样返回成功(204), 记录从未存在过才是 404。
     * <p>软删后同 (维度, 源值) 可重新创建。</p>
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_OPTIONS, allEntries = true)
    public void delete(Long id) {
        ValueAlias entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "值映射不存在"));
        if (entity.isDeletedFlag()) {
            return;
        }
        entity.setDeletedFlag(true);
        repository.saveAndFlush(entity);
        operationLogger.deleted(entity, id);
    }

    /** 解析维度: 空/非法都是 422(不是 400, 与"非法维度 → 422"契约一致)。 */
    private static ValueAliasDimension parseDimension(String raw) {
        try {
            return ValueAliasDimension.parse(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, ex.getMessage());
        }
    }

    /** 维度筛选: 空表示不筛选, 非法仍是 422。 */
    private static ValueAliasDimension parseDimensionOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : parseDimension(raw);
    }

    /**
     * 取值校验 + 定尺维度按定尺口径结构归一。
     * <p>定尺维度的源值/目标值不能归一成空串({@code -}/空/空白都表示"无定尺", 不能作为映射键)。</p>
     */
    private static String requireValue(String raw, String field, ValueAliasDimension dimension) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, field + "不能为空");
        }
        if (trimmed.length() > VALUE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    field + "长度不能超过" + VALUE_MAX_LENGTH + "个字符");
        }
        if (dimension != ValueAliasDimension.LENGTH) {
            return trimmed;
        }
        String canonical = MaterialSpecCatalogQuery.normalizeLength(trimmed);
        if (canonical.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    field + "不能归一为无定尺(空值/- 不作为定尺映射值)");
        }
        return canonical;
    }

    /** 自映射无意义: 归一后源值与目标值相同直接 422(数据库 CHECK 兜底)。 */
    private static void rejectSelfMapping(String sourceValue, String targetValue) {
        if (sourceValue.equals(targetValue)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "源值与目标值不能相同(自映射无意义)",
                    List.of(new ApiFieldError("targetValue", "SelfMapping", "源值与目标值不能相同(自映射无意义)")));
        }
    }

    private ValueAlias requireActive(Long id, String message) {
        if (id == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "id 不能为空");
        }
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, message));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static ValueAliasResponse toResponse(ValueAlias entity) {
        return new ValueAliasResponse(
                entity.getId(),
                entity.getDimension() == null ? null : entity.getDimension().name(),
                entity.getSourceValue(),
                entity.getTargetValue(),
                entity.getRemark(),
                entity.getCreatedName(),
                entity.getCreatedAt(),
                entity.getUpdatedName(),
                entity.getUpdatedAt());
    }
}
