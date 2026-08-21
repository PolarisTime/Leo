package com.leo.erp.system.printtemplate.repository;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintTemplateRepository extends JpaRepository<PrintTemplate, Long> {

    List<PrintTemplate> findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc(String billType);

    List<PrintTemplate> findAllBySyncModeAndDeletedFlagFalse(String syncMode);

    Optional<PrintTemplate> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse(
            String billType,
            Long settlementCompanyId,
            String templateName
    );

    boolean existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse(
            String billType,
            Long settlementCompanyId,
            String templateCode
    );
}
