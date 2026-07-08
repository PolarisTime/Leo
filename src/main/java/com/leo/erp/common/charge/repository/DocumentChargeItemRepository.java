package com.leo.erp.common.charge.repository;

import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentChargeItemRepository extends JpaRepository<DocumentChargeItem, Long> {

    List<DocumentChargeItem> findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(
            String moduleKey,
            Long documentId
    );
}
