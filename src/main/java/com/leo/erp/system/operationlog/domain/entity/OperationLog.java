package com.leo.erp.system.operationlog.domain.entity;

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
@Table(name = "sys_operation_log")
public class OperationLog {

    @Id
    private Long id;

    @Column(name = "log_no", nullable = false, unique = true, length = 64)
    private String logNo;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "login_name", nullable = false, length = 64)
    private String loginName;

    @Column(name = "module_name", nullable = false, length = 64)
    private String moduleName;

    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    @Column(name = "business_no", length = 128)
    private String businessNo;

    @Column(name = "request_method", nullable = false, length = 16)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "result_status", nullable = false, length = 16)
    private String resultStatus;

    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    @Column(name = "remark", length = 255)
    private String remark;
}
