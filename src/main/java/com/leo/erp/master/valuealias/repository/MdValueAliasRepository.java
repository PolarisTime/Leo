package com.leo.erp.master.valuealias.repository;

import com.leo.erp.master.valuealias.domain.entity.MdValueAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 值映射/别名仓储。
 *
 * <p>查询一律带 {@code deletedFlag = false}，与部分唯一索引
 * {@code uk_value_alias_dimension_source} 的口径保持一致——软删记录既不参与归一，
 * 也不占用源写法。</p>
 */
public interface MdValueAliasRepository extends JpaRepository<MdValueAlias, Long>,
        JpaSpecificationExecutor<MdValueAlias> {

    Optional<MdValueAlias> findByIdAndDeletedFlagFalse(Long id);

    List<MdValueAlias> findByDeletedFlagFalseOrderByDimensionAscSourceValueAsc();

    List<MdValueAlias> findByDimensionAndDeletedFlagFalseOrderBySourceValueAsc(String dimension);

    boolean existsByDimensionAndSourceValueAndDeletedFlagFalse(String dimension, String sourceValue);

    boolean existsByDimensionAndSourceValueAndDeletedFlagFalseAndIdNot(String dimension, String sourceValue, Long id);
}
