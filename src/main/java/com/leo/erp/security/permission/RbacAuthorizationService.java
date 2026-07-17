package com.leo.erp.security.permission;

import lombok.extern.slf4j.Slf4j;
import org.casbin.jcasbin.main.SyncedEnforcer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service("rbac")
public class RbacAuthorizationService {

    private final SyncedEnforcer enforcer;

    public RbacAuthorizationService(SyncedEnforcer enforcer) {
        this.enforcer = enforcer;
    }

    public boolean check(String resourceCode, String actionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if (!(authentication.getPrincipal() instanceof com.leo.erp.security.support.SecurityPrincipal principal)) {
            return false;
        }
        return check(principal.id(), resourceCode, actionCode);
    }

    public boolean check(Long userId, String resourceCode, String actionCode) {
        if (userId == null) {
            return false;
        }
        String resource = ResourcePermissionCatalog.normalizeResource(resourceCode);
        String action = ResourcePermissionCatalog.normalizeAction(actionCode);
        if (resource.isBlank() || action.isBlank()) {
            return false;
        }
        try {
            return enforcer.enforce(String.valueOf(userId), resource, action);
        } catch (RuntimeException ex) {
            log.error("jCasbin authorization failed closed: userId={}, resource={}, action={}",
                    userId, resource, action, ex);
            return false;
        }
    }
}
