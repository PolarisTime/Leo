package com.leo.erp.system.printtemplate.repository;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrintTemplateRepository extends JpaRepository<PrintTemplate, Long> {

    List<PrintTemplate> findAllByBillTypeAndDeletedFlagFalseOrderByIsDefaultDescUpdatedAtDescIdDesc(String billType);

    Optional<PrintTemplate> findFirstByBillTypeAndIsDefaultAndDeletedFlagFalse(String billType, String isDefault);

    Optional<PrintTemplate> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByBillTypeAndTemplateNameAndDeletedFlagFalse(String billType, String templateName);
}
