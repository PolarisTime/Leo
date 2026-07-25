package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttachmentFileRepository extends JpaRepository<AttachmentFile, Long> {

    Optional<AttachmentFile> findByIdAndDeletedFlagFalse(Long id);

    List<AttachmentFile> findAllByOrderByIdAsc();

    List<AttachmentFile> findAllByIdInAndDeletedFlagFalse(Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attachment
              from AttachmentFile attachment
             where attachment.id in :ids
               and attachment.deletedFlag = false
               and (attachment.ownerUserId = :ownerUserId
                    or (attachment.ownerUserId is null and attachment.createdBy = :ownerUserId))
             order by attachment.id
            """)
    List<AttachmentFile> findAllOwnedByForUpdate(
            @Param("ids") Collection<Long> ids,
            @Param("ownerUserId") Long ownerUserId
    );
}
