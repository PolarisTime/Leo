package com.leo.erp.security.permission;

import com.leo.erp.security.support.SecurityPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MachineSubjectRbacGuard {

    public static final String SUBJECT_HEADER = "X-Machine-Subject-Id";
    private static final String MCP_RESOURCE = "mcp_service";

    private final RbacAuthorizationService rbac;

    public MachineSubjectRbacGuard(RbacAuthorizationService rbac) {
        this.rbac = rbac;
    }

    public void requireRead(String claimedSubjectId) {
        require(claimedSubjectId, ResourcePermissionCatalog.READ);
    }

    public void requireWrite(String claimedSubjectId) {
        require(claimedSubjectId, ResourcePermissionCatalog.WRITE);
    }

    private void require(String claimedSubjectId, String action) {
        SecurityPrincipal principal = currentPrincipal();
        String authenticatedSubjectId = String.valueOf(principal.id());
        if (claimedSubjectId == null
                || !Objects.equals(authenticatedSubjectId, claimedSubjectId.trim())) {
            throw new AccessDeniedException("机器主体与已认证主体不一致");
        }
        if (!rbac.check(principal.id(), MCP_RESOURCE, action)) {
            throw new AccessDeniedException("机器主体无 MCP 访问权限");
        }
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof SecurityPrincipal principal) {
            return principal;
        }
        throw new AccessDeniedException("机器主体未认证");
    }
}
