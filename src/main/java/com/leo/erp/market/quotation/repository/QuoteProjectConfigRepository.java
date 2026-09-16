package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface QuoteProjectConfigRepository extends JpaRepository<QuoteProjectConfig, Long> {

    Optional<QuoteProjectConfig> findByProjectIdAndDeletedFlagFalse(Long projectId);
}
