package com.leo.erp.system.operationlog.repository;

import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {
    boolean existsByEventId(UUID eventId);
}
