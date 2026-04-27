package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentFileRepository extends JpaRepository<AttachmentFile, Long> {

    Optional<AttachmentFile> findByIdAndDeletedFlagFalse(Long id);

    List<AttachmentFile> findAllByIdInAndDeletedFlagFalse(Collection<Long> ids);
}
