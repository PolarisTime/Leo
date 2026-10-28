package com.leo.erp.system.operationlog.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;
import java.util.UUID;

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

    @Column(name = "business_no", length = 64)
    private String businessNo;

    @Column(name = "record_id")
    private Long recordId;

    @Column(name = "module_key", length = 64)
    private String moduleKey;

    @Column(name = "request_method", nullable = false, length = 8)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "result_status", nullable = false, length = 16)
    private String resultStatus;

    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    @Column(name = "auth_type", length = 16)
    private String authType;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "aggregate_type", length = 64)
    private String aggregateType;

    @Column(name = "event_version")
    private Integer eventVersion;

    @Column(name = "remark", length = 255)
    private String remark;

    /** HTTP 响应状态码(100..599); 历史数据为空。 */
    @Column(name = "status_code")
    private Integer statusCode;

    /** 请求处理耗时(毫秒); 历史数据为空。 */
    @Column(name = "duration_ms")
    private Integer durationMs;

    /** 请求 User-Agent 头, 截断 512 字符。 */
    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** 失败时的结构化错误码: BusinessException 业务码或异常类简名; 成功为空。 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 失败时的用户可读错误信息; 成功为空。 */
    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /**
     * 脱敏后的请求体 JSON 文本(列类型 jsonb)。
     *
     * <p>读写都显式 cast: 与 {@code md_material_history} 等既有 jsonb 列的映射方式一致,
     * 避免 Hibernate 依方言推断 json/jsonb 带来的隐式转换差异。</p>
     */
    @ColumnTransformer(read = "request_body::text", write = "?::jsonb")
    @Column(name = "request_body", columnDefinition = "jsonb")
    private String requestBody;

    /** 操作类别: SESSION/BUSINESS/SYSTEM, 默认 BUSINESS。 */
    @Column(name = "operation_category", nullable = false, length = 16)
    private String operationCategory;
}
