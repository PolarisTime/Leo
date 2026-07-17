package com.leo.erp.system.operationlog.event;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.security.support.SecurityPrincipal;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class BusinessOperationEventPublisher {

    private static final int EVENT_VERSION = 1;

    private final ApplicationEventPublisher eventPublisher;
    private final UserAccountRepository userAccountRepository;

    public BusinessOperationEventPublisher(ApplicationEventPublisher eventPublisher,
                                           UserAccountRepository userAccountRepository) {
        this.eventPublisher = eventPublisher;
        this.userAccountRepository = userAccountRepository;
    }

    public BusinessOperationEvent publish(String eventType,
                                          String moduleKey,
                                          String moduleName,
                                          String actionType,
                                          String aggregateType,
                                          Long aggregateId,
                                          String businessNo,
                                          String remark) {
        OperatorSnapshot operator = resolveOperator();
        BusinessOperationEvent event = new BusinessOperationEvent(
                UUID.randomUUID(),
                EVENT_VERSION,
                Instant.now(),
                eventType,
                moduleKey,
                moduleName,
                actionType,
                aggregateType,
                aggregateId,
                businessNo,
                operator.operatorId(),
                operator.operatorName(),
                operator.loginName(),
                operator.authType(),
                normalizeTraceId(MDC.get("traceId")),
                remark
        );
        eventPublisher.publishEvent(event);
        return event;
    }

    private OperatorSnapshot resolveOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return new OperatorSnapshot(0L, "system", "system", "SYSTEM");
        }

        UserAccount account = userAccountRepository.findByIdAndDeletedFlagFalse(principal.id()).orElse(null);
        String operatorName = account == null || account.getUserName() == null || account.getUserName().isBlank()
                ? principal.username()
                : account.getUserName();
        return new OperatorSnapshot(principal.id(), operatorName, principal.username(), "WEB");
    }

    private String normalizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        String normalized = traceId.trim();
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private record OperatorSnapshot(Long operatorId, String operatorName, String loginName, String authType) {
    }
}
