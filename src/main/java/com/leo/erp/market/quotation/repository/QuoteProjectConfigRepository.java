package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuoteProjectConfigRepository extends JpaRepository<QuoteProjectConfig, Long> {

    Optional<QuoteProjectConfig> findByProjectIdAndDeletedFlagFalse(Long projectId);

    /** 只读取版本号(不初始化 brands 集合), 供写事务提交后回读权威版本。 */
    @Query("select config.version from QuoteProjectConfig config"
            + " where config.projectId = :projectId and config.deletedFlag = false")
    Long findVersionByProjectId(@Param("projectId") Long projectId);
}
