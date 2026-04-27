package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AttachmentBindingRepository extends JpaRepository<AttachmentBinding, Long> {

    List<AttachmentBinding> findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(String moduleKey, Long recordId);

    List<AttachmentBinding> findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(String moduleKey, Collection<Long> recordIds);

    List<AttachmentBinding> findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(String moduleKey, Long attachmentId);

    void deleteByModuleKeyAndRecordIdAndDeletedFlagFalse(String moduleKey, Long recordId);
}
