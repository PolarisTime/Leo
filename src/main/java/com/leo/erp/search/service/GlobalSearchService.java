package com.leo.erp.search.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.search.repository.GlobalSearchDocument;
import com.leo.erp.search.repository.GlobalSearchDocumentRepository;
import com.leo.erp.search.repository.GlobalSearchModuleAccess;
import com.leo.erp.search.web.GlobalSearchResponse;
import com.leo.erp.security.permission.ModulePermissionGuard;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.security.support.SecurityPrincipal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalSearchService {
    private static final int MAX_TOTAL_LIMIT = 50;
    private static final List<String> DEFAULT_MODULE_KEYS = List.of(
            "purchase-order",
            "purchase-inbound",
            "sales-order",
            "sales-outbound",
            "freight-bill",
            "customer-statement",
            "freight-statement",
            "receipt",
            "payment"
    );

    private final PermissionService permissionService;
    private final ModulePermissionGuard modulePermissionGuard;
    private final GlobalSearchDocumentRepository documentRepository;

    public GlobalSearchService(PermissionService permissionService,
                               ModulePermissionGuard modulePermissionGuard,
                               GlobalSearchDocumentRepository documentRepository) {
        this.permissionService = permissionService;
        this.modulePermissionGuard = modulePermissionGuard;
        this.documentRepository = documentRepository;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<GlobalSearchResponse> search(String keyword, int limit) {
        return search(keyword, limit, null);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<GlobalSearchResponse> search(String keyword, int limit, List<String> moduleKeys) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        if (normalizedKeyword.isBlank()) {
            return List.of();
        }

        Optional<Long> trackId = isLikelyTrackId(normalizedKeyword)
                ? parseTrackId(normalizedKeyword)
                : Optional.empty();
        if (isLikelyTrackId(normalizedKeyword) && trackId.isEmpty()) {
            return List.of();
        }

        SecurityPrincipal principal = currentPrincipal();
        List<GlobalSearchModuleAccess> moduleAccesses = resolveModuleKeys(normalizeModuleKeys(moduleKeys)).stream()
                .map(moduleKey -> resolveModuleAccess(principal, moduleKey))
                .flatMap(Optional::stream)
                .toList();
        if (moduleAccesses.isEmpty()) {
            return List.of();
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_TOTAL_LIMIT);
        return documentRepository.search(normalizedKeyword, trackId.orElse(null), normalizedLimit, moduleAccesses)
                .stream()
                .map(this::toResponse)
                .filter(Objects::nonNull)
                .limit(normalizedLimit)
                .toList();
    }

    private List<String> resolveModuleKeys(Set<String> requestedModuleKeys) {
        if (requestedModuleKeys == null || requestedModuleKeys.isEmpty()) {
            return DEFAULT_MODULE_KEYS;
        }
        return DEFAULT_MODULE_KEYS.stream()
                .filter(requestedModuleKeys::contains)
                .toList();
    }

    private Set<String> normalizeModuleKeys(List<String> moduleKeys) {
        if (moduleKeys == null || moduleKeys.isEmpty()) {
            return Set.of();
        }
        return moduleKeys.stream()
                .filter(Objects::nonNull)
                .flatMap(item -> List.of(item.split(",")).stream())
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<GlobalSearchModuleAccess> resolveModuleAccess(SecurityPrincipal principal, String moduleKey) {
        ModulePermissionGuard.PermissionCheck permissionCheck;
        try {
            permissionCheck = modulePermissionGuard.requireResourcePermission(
                    principal,
                    moduleKey,
                    ResourcePermissionCatalog.READ
            );
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.FORBIDDEN) {
                return Optional.empty();
            }
            throw ex;
        }

        String resource = permissionCheck.resource();
        if (!ResourcePermissionCatalog.isBusinessResource(resource)) {
            return Optional.of(GlobalSearchModuleAccess.all(moduleKey));
        }

        String dataScope = ResourcePermissionCatalog.normalizeDataScope(
                permissionService.getUserDataScope(principal.id(), resource, permissionCheck.action())
        );
        if (ResourcePermissionCatalog.SCOPE_ALL.equals(dataScope)) {
            return Optional.of(GlobalSearchModuleAccess.all(moduleKey));
        }

        Set<Long> ownerUserIds = permissionService.getDataScopeOwnerUserIds(principal.id(), dataScope);
        if (ownerUserIds == null) {
            ownerUserIds = Set.of(principal.id());
        }
        return Optional.of(new GlobalSearchModuleAccess(moduleKey, ownerUserIds));
    }

    private SecurityPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal;
    }

    private GlobalSearchResponse toResponse(GlobalSearchDocument document) {
        if (document.recordId() == null) {
            return null;
        }
        String trackId = String.valueOf(document.recordId());
        String primaryNo = Optional.ofNullable(document.primaryNo())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(trackId);
        return new GlobalSearchResponse(
                document.moduleKey(),
                moduleTitle(document.moduleKey()),
                trackId,
                primaryNo,
                Optional.ofNullable(document.summary()).orElse(""),
                document.matchedByTrackId()
        );
    }

    private String moduleTitle(String moduleKey) {
        return ResourcePermissionCatalog.resolveResourceByMenuCode(moduleKey)
                .map(ResourcePermissionCatalog::resourceTitle)
                .orElse(moduleKey);
    }

    private boolean isLikelyTrackId(String keyword) {
        return keyword != null && keyword.matches("^\\d{12,}$");
    }

    private Optional<Long> parseTrackId(String keyword) {
        try {
            return Optional.of(Long.parseLong(keyword));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
