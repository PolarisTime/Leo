package com.leo.erp.system.operationlog.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.mapper.OperationLogMapper;
import com.leo.erp.system.operationlog.repository.OperationLogRepository;
import com.leo.erp.system.operationlog.repository.OperationLogSummaryRepository;
import com.leo.erp.system.operationlog.support.OperationLogConstants;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import com.leo.erp.system.operationlog.web.dto.OperationLogSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
public class OperationLogService {

    /** 概览统计默认时间窗: 最近 7 天(含今天)。 */
    static final int DEFAULT_SUMMARY_DAYS = 7;
    /** 概览统计允许的最大时间窗: 31 天(含首尾)。 */
    static final int MAX_SUMMARY_DAYS = 31;
    private static final int TOP_FAILED_MODULE_LIMIT = 5;

    private final OperationLogRepository repository;
    private final OperationLogSummaryRepository summaryRepository;
    private final OperationLogMapper operationLogMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final AccountQuery accountQuery;

    public OperationLogService(OperationLogRepository repository,
                               OperationLogSummaryRepository summaryRepository,
                               OperationLogMapper operationLogMapper,
                               SnowflakeIdGenerator idGenerator,
                               AccountQuery accountQuery) {
        this.repository = repository;
        this.summaryRepository = summaryRepository;
        this.operationLogMapper = operationLogMapper;
        this.idGenerator = idGenerator;
        this.accountQuery = accountQuery;
    }

    @Transactional(readOnly = true)
    public Page<OperationLogResponse> page(PageQuery query, OperationLogFilter filter) {
        String keyword = trimToNull(filter.keyword());
        String moduleName = trimToNull(filter.moduleName());
        String actionType = trimToNull(filter.actionType());
        String resultStatus = normalizeResultStatus(filter.resultStatus());
        String authType = trimToNull(filter.authType());
        String operatorName = trimToNull(filter.operatorName());
        String businessNo = trimToNull(filter.businessNo());
        String category = normalizeCategory(filter.category());
        Integer minDurationMs = normalizeMinDurationMs(filter.minDurationMs());
        Integer statusCode = normalizeStatusCode(filter.statusCode());
        validateDateRange(filter.startDate(), filter.endDate());
        Long recordId = filter.recordId();

        Specification<OperationLog> spec = (root, q, cb) -> {
            var predicate = cb.conjunction();
            if (recordId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("recordId"), recordId));
            }
            if (keyword != null) {
                predicate = cb.and(predicate, Specs.<OperationLog>keywordLike(
                        keyword,
                        "logNo", "operatorName", "loginName", "businessNo", "requestPath", "remark"
                ).toPredicate(root, q, cb));
            }
            if (moduleName != null) {
                predicate = cb.and(predicate, cb.equal(root.get("moduleName"), moduleName));
            }
            if (actionType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("actionType"), actionType));
            }
            if (resultStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("resultStatus"), resultStatus));
            }
            if (authType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("authType"), authType));
            }
            if (operatorName != null) {
                // 同时匹配操作人姓名与登录名: 前端「我的操作」传的是登录名, 只匹配姓名会查不到
                predicate = cb.and(predicate, cb.or(
                        Specs.containsIgnoreCase(cb, root.<String>get("operatorName"), operatorName),
                        Specs.containsIgnoreCase(cb, root.<String>get("loginName"), operatorName)
                ));
            }
            if (businessNo != null) {
                predicate = cb.and(predicate, Specs.containsIgnoreCase(cb, root.<String>get("businessNo"), businessNo));
            }
            if (category != null) {
                predicate = cb.and(predicate, cb.equal(root.get("operationCategory"), category));
            }
            if (minDurationMs != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(
                        root.<Integer>get("durationMs"), minDurationMs));
            }
            if (statusCode != null) {
                predicate = cb.and(predicate, cb.equal(root.get("statusCode"), statusCode));
            }
            if (filter.startDate() != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(
                        root.<LocalDateTime>get("operationTime"), filter.startDate().atStartOfDay()));
            }
            if (filter.endDate() != null) {
                predicate = cb.and(predicate, cb.lessThan(
                        root.<LocalDateTime>get("operationTime"), filter.endDate().plusDays(1).atStartOfDay()));
            }
            return predicate;
        };

        return repository.findAll(spec, query.toPageable("operationTime"))
                .map(operationLogMapper::toResponse);
    }

    /**
     * 概览统计: 总量/成功/失败/平均耗时 + 按天趋势 + 失败模块 Top5。
     *
     * <p>聚合在数据库完成({@link OperationLogSummaryRepository}), 不把明细行拉回内存;
     * 日期闭区间, 不传时默认最近 7 天, 超过 31 天返回 422。</p>
     */
    @Transactional(readOnly = true)
    public OperationLogSummaryResponse summary(OperationLogSummaryFilter filter) {
        LocalDate endDate = filter.endDate() != null ? filter.endDate() : LocalDate.now();
        LocalDate startDate = filter.startDate() != null
                ? filter.startDate()
                : endDate.minusDays(DEFAULT_SUMMARY_DAYS - 1L);
        validateDateRange(startDate, endDate);
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > MAX_SUMMARY_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "summary 时间范围不能超过31天");
        }

        OperationLogSummaryRepository.SummaryCriteria criteria = new OperationLogSummaryRepository.SummaryCriteria(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                trimToNull(filter.moduleName()),
                trimToNull(filter.actionType()),
                normalizeResultStatus(filter.resultStatus()),
                normalizeCategory(filter.category()),
                trimToNull(filter.operatorName())
        );

        OperationLogSummaryRepository.SummaryCounts counts = summaryRepository.count(criteria);
        List<OperationLogSummaryResponse.DailyCount> byDay = summaryRepository.countByDay(criteria).stream()
                .map(item -> new OperationLogSummaryResponse.DailyCount(item.date(), item.total(), item.failed()))
                .toList();
        List<OperationLogSummaryResponse.FailedModule> topFailedModules = summaryRepository
                .topFailedModules(criteria, TOP_FAILED_MODULE_LIMIT).stream()
                .map(item -> new OperationLogSummaryResponse.FailedModule(item.moduleName(), item.count()))
                .toList();

        return new OperationLogSummaryResponse(
                counts.total(),
                counts.successCount(),
                counts.failedCount(),
                counts.avgDurationMs(),
                byDay,
                topFailedModules
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(OperationLogCommand command) {
        if (command.eventId() != null && repository.existsByEventId(command.eventId())) {
            return;
        }
        OperatorSnapshot operator = resolveOperator(command);

        OperationLog entity = new OperationLog();
        long id = idGenerator.nextId();
        entity.setId(id);
        entity.setLogNo("OP" + id);
        entity.setOperatorId(operator.operatorId());
        entity.setOperatorName(operator.operatorName());
        entity.setLoginName(operator.loginName());
        entity.setModuleName(command.moduleName());
        entity.setActionType(command.actionType());
        entity.setBusinessNo(trimToNull(command.businessNo()));
        entity.setRecordId(command.recordId());
        entity.setModuleKey(trimToNull(command.moduleKey()));
        entity.setRequestMethod(command.requestMethod());
        entity.setRequestPath(command.requestPath());
        entity.setClientIp(trimToNull(command.clientIp()));
        entity.setResultStatus(command.resultStatus());
        entity.setOperationTime(LocalDateTime.now());
        entity.setAuthType(resolveAuthType(command.authType()));
        entity.setEventId(command.eventId());
        entity.setTraceId(truncate(command.traceId(), 64));
        entity.setAggregateType(truncate(command.aggregateType(), 64));
        entity.setEventVersion(command.eventVersion());
        entity.setRemark(truncate(command.remark(), 255));
        entity.setStatusCode(command.statusCode());
        entity.setDurationMs(normalizeDurationMs(command.durationMs()));
        entity.setUserAgent(truncate(command.userAgent(), 512));
        entity.setErrorCode(truncate(command.errorCode(), 64));
        entity.setErrorMessage(truncate(command.errorMessage(), 4000));
        // 已在采集层脱敏并保证是合法 JSON(总体 <=8KB); 这里不再截断, 否则会破坏 JSON
        entity.setRequestBody(trimToNull(command.requestBody()));
        entity.setOperationCategory(resolveOperationCategory(command.operationCategory()));
        repository.save(entity);
    }

    private OperatorSnapshot resolveOperator(OperationLogCommand command) {
        String explicitOperatorName = trimToNull(command.operatorName());
        String explicitLoginName = trimToNull(command.loginName());
        if (explicitOperatorName != null || explicitLoginName != null || command.operatorId() != null) {
            return new OperatorSnapshot(
                    command.operatorId(),
                    explicitOperatorName != null ? explicitOperatorName : defaultOperatorName(explicitLoginName),
                    explicitLoginName != null ? explicitLoginName : defaultLoginName(explicitOperatorName)
            );
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return new OperatorSnapshot(0L, "system", "system");
        }

        AccountQuery.AccountSnapshot account = accountQuery.findById(principal.id()).orElse(null);
        String operatorName = account == null || account.userName() == null || account.userName().isBlank()
                ? principal.username()
                : account.userName();
        return new OperatorSnapshot(principal.id(), operatorName, principal.username());
    }

    private String defaultOperatorName(String explicitLoginName) {
        return explicitLoginName != null ? explicitLoginName : "system";
    }

    private String defaultLoginName(String explicitOperatorName) {
        return explicitOperatorName != null ? explicitOperatorName : "system";
    }

    private String resolveAuthType(String explicitAuthType) {
        String normalizedAuthType = trimToNull(explicitAuthType);
        if (normalizedAuthType != null) {
            return truncate(normalizedAuthType, 16);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? "WEB" : "SYSTEM";
    }

    /** 操作类别: 缺省归为业务类; 非法值同样归为业务类, 保证 NOT NULL 列可写入。 */
    private String resolveOperationCategory(String explicitCategory) {
        String normalized = trimToNull(explicitCategory);
        if (normalized == null) {
            return OperationLogConstants.CATEGORY_BUSINESS;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (OperationLogConstants.CATEGORY_SESSION.equals(upper)
                || OperationLogConstants.CATEGORY_SYSTEM.equals(upper)) {
            return upper;
        }
        return OperationLogConstants.CATEGORY_BUSINESS;
    }

    private Integer normalizeDurationMs(Integer durationMs) {
        if (durationMs == null) {
            return null;
        }
        return Math.max(durationMs, 0);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeResultStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        if (!OperationLogConstants.RESULT_SUCCESS.equals(normalized)
                && !OperationLogConstants.RESULT_FAILURE.equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "resultStatus 不合法");
        }
        return normalized;
    }

    private String normalizeCategory(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!OperationLogConstants.CATEGORY_SESSION.equals(upper)
                && !OperationLogConstants.CATEGORY_BUSINESS.equals(upper)
                && !OperationLogConstants.CATEGORY_SYSTEM.equals(upper)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "category 不合法");
        }
        return upper;
    }

    private Integer normalizeMinDurationMs(Integer minDurationMs) {
        if (minDurationMs == null) {
            return null;
        }
        if (minDurationMs < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "minDurationMs 不能为负数");
        }
        return minDurationMs;
    }

    private Integer normalizeStatusCode(Integer statusCode) {
        if (statusCode == null) {
            return null;
        }
        if (statusCode < 100 || statusCode > 599) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "statusCode 必须在100到599之间");
        }
        return statusCode;
    }

    private void validateDateRange(LocalDate startTime, LocalDate endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "startTime 不能晚于 endTime");
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    private record OperatorSnapshot(Long operatorId, String operatorName, String loginName) {
    }
}
