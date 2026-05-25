package com.leo.erp.system.service;

import com.leo.erp.common.support.DateTimeFormatSupport;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.database.service.DatabaseStatusService;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HealthPageService {

    private final DatabaseStatusService databaseStatusService;
    private final PermissionService permissionService;
    private final HealthPageRenderer renderer;

    public HealthPageService(DatabaseStatusService databaseStatusService,
                             PermissionService permissionService,
                             HealthPageRenderer renderer) {
        this.databaseStatusService = databaseStatusService;
        this.permissionService = permissionService;
        this.renderer = renderer;
    }

    public String render() {
        String checkedAt = DateTimeFormatSupport.now();
        HealthPageRenderer.JvmStatus jvm = renderer.getJvmStatus();
        if (!isPrivilegedRequest()) {
            log.debug("public health page requested: appStatus=UP");
            return renderer.renderPublic(checkedAt, jvm);
        }
        DatabaseStatusResponse status = databaseStatusService.getStatus();
        String debugOutput = renderer.renderDebug(checkedAt, jvm, status.postgres(), status.redis());
        log.debug("health page requested: appStatus=UP, jvmVersion={}, pgStatus={}, redisStatus={}",
                jvm.javaVersion(), status.postgres().status(), status.redis().status());
        return renderer.renderDetailed(checkedAt, jvm, status.postgres(), status.redis(), debugOutput);
    }

    private boolean isPrivilegedRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return false;
        }
        return permissionService.can(principal.id(), "database", "read");
    }
}
