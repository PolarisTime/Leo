package com.leo.erp.system.operationlog.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.domain.entity.OperationLog;
import com.leo.erp.system.operationlog.repository.OperationLogRepository;
import com.leo.erp.system.operationlog.mapper.OperationLogMapper;
import com.leo.erp.system.operationlog.web.dto.OperationLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Service
public class OperationLogService {

    private static final String ROLE_ACTION_EDITOR_MODULE_NAME = "角色权限配置";
    private static final List<String> ROLE_ACTION_EDITOR_MODULE_ALIASES = List.of(
            ROLE_ACTION_EDITOR_MODULE_NAME,
            "角色设置"
    );

    private final OperationLogRepository repository;
    private final OperationLogMapper operationLogMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final UserAccountRepository userAccountRepository;

    public OperationLogService(OperationLogRepository repository,
                               OperationLogMapper operationLogMapper,
                               SnowflakeIdGenerator idGenerator,
                               UserAccountRepository userAccountRepository) {
        this.repository = repository;
        this.operationLogMapper = operationLogMapper;
        this.idGenerator = idGenerator;
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public Page<OperationLogResponse> page(PageQuery query,
                                           String keyword,
                                           String moduleName,
                                           String actionType,
                                           String resultStatus,
                                           LocalDate startTime,
                                           LocalDate endTime) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedModuleName = trimToNull(moduleName);
        String normalizedActionType = trimToNull(actionType);
        String normalizedResultStatus = normalizeResultStatus(resultStatus);
        validateDateRange(startTime, endTime);
        Specification<OperationLog> spec = (root, q, cb) -> {
            var predicate = cb.conjunction();
            if (normalizedKeyword != null) {
                String pattern = "%" + normalizedKeyword + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(root.get("logNo"), pattern),
                        cb.like(root.get("operatorName"), pattern),
                        cb.like(root.get("loginName"), pattern),
                        cb.like(root.get("businessNo"), pattern),
                        cb.like(root.get("requestPath"), pattern),
                        cb.like(root.get("remark"), pattern)
                ));
            }
            if (normalizedModuleName != null) {
                if (ROLE_ACTION_EDITOR_MODULE_NAME.equals(normalizedModuleName)) {
                    predicate = cb.and(predicate, root.get("moduleName").in(ROLE_ACTION_EDITOR_MODULE_ALIASES));
                } else {
                    predicate = cb.and(predicate, cb.equal(root.get("moduleName"), normalizedModuleName));
                }
            }
            if (normalizedActionType != null) {
                predicate = cb.and(predicate, cb.equal(root.get("actionType"), normalizedActionType));
            }
            if (normalizedResultStatus != null) {
                predicate = cb.and(predicate, cb.equal(root.get("resultStatus"), normalizedResultStatus));
            }
            if (startTime != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("operationTime"), startTime.atStartOfDay()));
            }
            if (endTime != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("operationTime"), endTime.plusDays(1).atStartOfDay()));
            }
            return predicate;
        };

        return repository.findAll(spec, query.toPageable("operationTime"))
                .map(operationLogMapper::toResponse);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(OperationLogCommand command) {
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
        entity.setRequestMethod(command.requestMethod());
        entity.setRequestPath(command.requestPath());
        entity.setClientIp(trimToNull(command.clientIp()));
        entity.setResultStatus(command.resultStatus());
        entity.setOperationTime(LocalDateTime.now());
        entity.setRemark(truncate(command.remark(), 255));
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

        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(principal.id()).orElse(null);
        String operatorName = user == null || user.getUserName() == null || user.getUserName().isBlank()
                ? principal.username()
                : user.getUserName();
        return new OperatorSnapshot(principal.id(), operatorName, principal.username());
    }

    private String defaultOperatorName(String explicitLoginName) {
        return explicitLoginName != null ? explicitLoginName : "system";
    }

    private String defaultLoginName(String explicitOperatorName) {
        return explicitOperatorName != null ? explicitOperatorName : "system";
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
        if (!"成功".equals(normalized) && !"失败".equals(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "resultStatus 不合法");
        }
        return normalized;
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
