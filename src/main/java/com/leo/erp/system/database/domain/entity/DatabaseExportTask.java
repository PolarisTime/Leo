package com.leo.erp.system.database.domain.entity;

import com.leo.erp.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "sys_database_export_task")
public class DatabaseExportTask extends AuditableEntity {

    @Id
    private Long id;

    @Column(name = "task_no", nullable = false, length = 64, unique = true)
    private String taskNo;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "download_token", length = 64)
    private String downloadToken;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;
}
