package com.leo.erp.market.pricelist.repository;

import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/** 值映射/别名仓储({@code md_value_alias})。 */
public interface ValueAliasRepository extends JpaRepository<ValueAlias, Long>,
        JpaSpecificationExecutor<ValueAlias> {

    Optional<ValueAlias> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByDimensionAndSourceValueAndDeletedFlagFalse(ValueAliasDimension dimension, String sourceValue);

    Optional<ValueAlias> findByDimensionAndSourceValueAndDeletedFlagFalse(ValueAliasDimension dimension,
                                                                          String sourceValue);

    /** 某维度的全部未删除映射(归一化查询按维度批量取; 按源值排序保证结果确定性)。 */
    List<ValueAlias> findByDimensionAndDeletedFlagFalseOrderBySourceValueAscIdAsc(ValueAliasDimension dimension);
}
