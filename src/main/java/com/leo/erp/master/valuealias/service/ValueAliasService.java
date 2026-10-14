package com.leo.erp.master.valuealias.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.CrudOperationLogger;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.valuealias.domain.entity.MdValueAlias;
import com.leo.erp.master.valuealias.repository.MdValueAliasRepository;
import com.leo.erp.master.valuealias.web.dto.ValueAliasRequest;
import com.leo.erp.master.valuealias.web.dto.ValueAliasResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 值映射/别名维护服务。
 *
 * <p>本服务补齐 {@code md_value_alias} 的「最后一公里」：该表在 V171 已建好（含维度约束、
 * 自映射约束、部分唯一索引与种子数据），权限码也已登记，但一直没有端点与页面，导致归一规则
 * 只能改代码。这里提供完整 CRUD 与供归一化消费方批量取用的映射查询。</p>
 *
 * <p>三条不变量在此显式校验，给出可读的 422 提示；数据库约束仍然兜底：
 * 维度必须属于白名单、源写法与目标写法不同（自映射无意义）、同维度内源写法未删除唯一。</p>
 */
@Service
public class ValueAliasService {

    private static final int MAX_VALUE_LENGTH = 64;

    private final MdValueAliasRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final CrudOperationLogger operationLogger = CrudOperationLogger.forOwner(ValueAliasService.class);

    public ValueAliasService(MdValueAliasRepository repository, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public Page<ValueAliasResponse> page(PageQuery query, String dimension, String keyword) {
        String normalizedDimension = ValueAliasDimension.parse(dimension)
                .map(Enum::name)
                .orElse(null);
        if (dimension != null && !dimension.isBlank() && normalizedDimension == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "维度必须是 CATEGORY / MATERIAL / LENGTH / BRAND 之一");
        }
        Specification<MdValueAlias> spec = Specs.<MdValueAlias>notDeleted()
                .and(Specs.equalIfPresent("dimension", normalizedDimension))
                .and(Specs.keywordLike(keyword, "sourceValue", "targetValue", "remark"));
        return repository.findAll(spec, query.toPageable("id")).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ValueAliasResponse detail(Long id) {
        return toResponse(requireActive(id));
    }

    @Transactional
    public ValueAliasResponse create(ValueAliasRequest request) {
        String dimension = ValueAliasDimension.require(request.dimension()).name();
        String sourceValue = normalizeValue(request.sourceValue(), "源写法");
        String targetValue = normalizeValue(request.targetValue(), "目标写法");
        assertNotSelfMapping(sourceValue, targetValue);
        if (repository.existsByDimensionAndSourceValueAndDeletedFlagFalse(dimension, sourceValue)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "该维度下源写法「" + sourceValue + "」已存在映射，请改为编辑既有记录");
        }
        MdValueAlias entity = new MdValueAlias();
        long entityId = snowflakeIdGenerator.nextId();
        entity.setId(entityId);
        entity.setDimension(dimension);
        entity.setSourceValue(sourceValue);
        entity.setTargetValue(targetValue);
        entity.setRemark(trimToNull(request.remark()));
        MdValueAlias saved = repository.saveAndFlush(entity);
        operationLogger.created(saved, entityId);
        return toResponse(saved);
    }

    @Transactional
    public ValueAliasResponse update(Long id, ValueAliasRequest request) {
        MdValueAlias entity = requireActive(id);
        String dimension = ValueAliasDimension.require(request.dimension()).name();
        String sourceValue = normalizeValue(request.sourceValue(), "源写法");
        String targetValue = normalizeValue(request.targetValue(), "目标写法");
        assertNotSelfMapping(sourceValue, targetValue);
        if (repository.existsByDimensionAndSourceValueAndDeletedFlagFalseAndIdNot(dimension, sourceValue, id)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "该维度下源写法「" + sourceValue + "」已被其他记录占用");
        }
        entity.setDimension(dimension);
        entity.setSourceValue(sourceValue);
        entity.setTargetValue(targetValue);
        entity.setRemark(trimToNull(request.remark()));
        MdValueAlias saved = repository.saveAndFlush(entity);
        operationLogger.updated(saved, id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        MdValueAlias entity = requireActive(id);
        entity.setDeletedFlag(true);
        repository.saveAndFlush(entity);
        operationLogger.deleted(entity, id);
    }

    /**
     * 按维度取「源写法 → 目标写法」映射，供归一化消费方使用。
     *
     * <p>返回不可变快照，调用方不应缓存跨请求复用；同维度源写法唯一由部分唯一索引保证，
     * 因此这里不会出现键冲突（若出现说明数据被绕过约束写入，此时保留先出现的一条）。</p>
     */
    @Transactional(readOnly = true)
    public Map<String, String> mappingOf(ValueAliasDimension dimension) {
        List<MdValueAlias> rows = repository
                .findByDimensionAndDeletedFlagFalseOrderBySourceValueAsc(dimension.name());
        Map<String, String> mapping = new LinkedHashMap<>();
        for (MdValueAlias row : rows) {
            mapping.putIfAbsent(row.getSourceValue(), row.getTargetValue());
        }
        return Map.copyOf(mapping);
    }

    private MdValueAlias requireActive(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "值映射不存在"));
    }

    private void assertNotSelfMapping(String sourceValue, String targetValue) {
        if (Objects.equals(sourceValue, targetValue)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "源写法与目标写法相同，自映射没有意义");
        }
    }

    /** 边界空白按 trim 清理后校验非空与长度，与数据库 varchar(64) 对齐。 */
    private String normalizeValue(String raw, String fieldLabel) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldLabel + "不能为空");
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    fieldLabel + "不能超过" + MAX_VALUE_LENGTH + "个字符");
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

    private ValueAliasResponse toResponse(MdValueAlias entity) {
        return new ValueAliasResponse(
                entity.getId(),
                entity.getDimension(),
                entity.getSourceValue(),
                entity.getTargetValue(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
