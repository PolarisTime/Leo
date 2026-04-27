package com.leo.erp.security.permission;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

@Component
public class WorkflowTransitionGuard {

    private final ModulePermissionGuard modulePermissionGuard;

    public WorkflowTransitionGuard(ModulePermissionGuard modulePermissionGuard) {
        this.modulePermissionGuard = modulePermissionGuard;
    }

    public void assertAuditPermissionForProtectedValue(String moduleKey,
                                                       String currentValue,
                                                       String nextValue,
                                                       String... protectedValues) {
        assertAuditPermissionForProtectedValue(moduleKey, currentValue, nextValue, Arrays.asList(protectedValues));
    }

    public void assertAuditPermissionForProtectedValue(String moduleKey,
                                                       String currentValue,
                                                       String nextValue,
                                                       Collection<String> protectedValues) {
        String normalizedCurrentValue = normalize(currentValue);
        String normalizedNextValue = normalize(nextValue);
        if (normalizedNextValue.isEmpty() || Objects.equals(normalizedCurrentValue, normalizedNextValue)) {
            return;
        }

        boolean protectedTransition = protectedValues.stream()
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .anyMatch(normalizedNextValue::equals);
        if (!protectedTransition) {
            return;
        }

        modulePermissionGuard.requireResourcePermission(
                currentPrincipal(),
                moduleKey,
                ResourcePermissionCatalog.AUDIT
        );
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
