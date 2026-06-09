package com.leo.erp.system.printtemplate.repository;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintTemplateRepository extends JpaRepository<PrintTemplate, Long> {

    List<PrintTemplate> findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc(String billType);

    List<PrintTemplate> findAllBySyncModeAndDeletedFlagFalse(String syncMode);

    Optional<PrintTemplate> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByBillTypeAndTemplateNameAndDeletedFlagFalse(String billType, String templateName);

    boolean existsByBillTypeAndTemplateCodeAndDeletedFlagFalse(String billType, String templateCode);
}
