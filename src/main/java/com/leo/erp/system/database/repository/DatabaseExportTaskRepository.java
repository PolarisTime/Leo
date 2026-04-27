package com.leo.erp.system.database.repository;

import com.leo.erp.system.database.domain.entity.DatabaseExportTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DatabaseExportTaskRepository extends JpaRepository<DatabaseExportTask, Long> {

    Optional<DatabaseExportTask> findByIdAndDeletedFlagFalse(Long id);

    boolean existsByStatusInAndDeletedFlagFalse(Collection<String> statuses);

    List<DatabaseExportTask> findTop20ByDeletedFlagFalseOrderByCreatedAtDescIdDesc();

    List<DatabaseExportTask> findByStatusAndExpiresAtBeforeAndDeletedFlagFalse(String status, LocalDateTime expiresAt);

    List<DatabaseExportTask> findByStatusInAndDeletedFlagFalse(Collection<String> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DatabaseExportTask task
               set task.downloadToken = null
             where task.id = :id
               and task.downloadToken = :token
               and task.deletedFlag = false
               and task.status = :status
               and task.expiresAt is not null
               and task.expiresAt >= :now
            """)
    int consumeDownloadToken(@Param("id") Long id,
                             @Param("token") String token,
                             @Param("status") String status,
                             @Param("now") LocalDateTime now);
}
