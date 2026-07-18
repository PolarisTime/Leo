package com.leo.erp.search.service;

import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.search.repository.GlobalSearchDocument;
import com.leo.erp.search.repository.GlobalSearchDocumentRepository;
import com.leo.erp.search.web.GlobalSearchResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    private final GlobalSearchDocumentRepository documentRepository;
    private final ModuleCatalog moduleCatalog;

    public GlobalSearchService(GlobalSearchDocumentRepository documentRepository,
                               ModuleCatalog moduleCatalog) {
        this.documentRepository = documentRepository;
        this.moduleCatalog = moduleCatalog;
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

        List<String> allowedModuleKeys = resolveModuleKeys(normalizeModuleKeys(moduleKeys));
        if (allowedModuleKeys.isEmpty()) {
            return List.of();
        }

        int normalizedLimit = Math.min(Math.max(limit, 1), MAX_TOTAL_LIMIT);
        return documentRepository.search(normalizedKeyword, trackId.orElse(null), normalizedLimit, allowedModuleKeys)
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
        return moduleCatalog.resolveModuleName(moduleKey);
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
