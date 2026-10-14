package com.leo.erp.market.lengthpremium.repository;

import com.leo.erp.market.lengthpremium.domain.entity.MdLengthPremium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * 定尺加价仓储。查询一律带 {@code deletedFlag = false}，与部分唯一索引口径一致。
 */
public interface MdLengthPremiumRepository extends JpaRepository<MdLengthPremium, Long>,
        JpaSpecificationExecutor<MdLengthPremium> {

    Optional<MdLengthPremium> findByIdAndDeletedFlagFalse(Long id);

    List<MdLengthPremium> findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(String status);

    boolean existsByLengthNameAndDeletedFlagFalse(String lengthName);

    boolean existsByLengthNameAndDeletedFlagFalseAndIdNot(String lengthName, Long id);
}
