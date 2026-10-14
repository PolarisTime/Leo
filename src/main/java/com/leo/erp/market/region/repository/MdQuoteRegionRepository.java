package com.leo.erp.market.region.repository;

import com.leo.erp.market.region.domain.entity.MdQuoteRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 取价地区仓储。查询一律带 {@code deletedFlag = false}，与部分唯一索引口径一致。
 */
public interface MdQuoteRegionRepository extends JpaRepository<MdQuoteRegion, Long>,
        JpaSpecificationExecutor<MdQuoteRegion> {

    Optional<MdQuoteRegion> findByIdAndDeletedFlagFalse(Long id);

    List<MdQuoteRegion> findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(String status);

    List<MdQuoteRegion> findByDeletedFlagFalseOrderBySortOrderAsc();

    boolean existsByRegionNameAndDeletedFlagFalse(String regionName);

    boolean existsByRegionNameAndDeletedFlagFalseAndIdNot(String regionName, Long id);

    boolean existsBySlugAndDeletedFlagFalse(String slug);

    boolean existsBySlugAndDeletedFlagFalseAndIdNot(String slug, Long id);
}
