package com.leo.erp.common.charge.repository;

import com.leo.erp.common.charge.domain.entity.DocumentChargeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentChargeItemRepository extends JpaRepository<DocumentChargeItem, Long> {

    List<DocumentChargeItem> findByModuleKeyAndDocumentIdAndDeletedFlagFalseOrderByLineNoAscIdAsc(
            String moduleKey, Long documentId);

    /**
     * 按多个单据批量查询费用行: 排序口径与单条查询一致
     * ({@code line_no ASC, id ASC}), 保证拖拽调整顺序后批量读取仍按提交顺序返回。
     */
    List<DocumentChargeItem> findByModuleKeyAndDocumentIdInAndDeletedFlagFalseOrderByLineNoAscIdAsc(
            String moduleKey, Collection<Long> documentIds);
}
