package com.leo.erp.system.permission.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.system.menu.repository.MenuActionRepository;
import com.leo.erp.system.menu.repository.MenuRepository;
import com.leo.erp.system.permission.web.dto.PermissionEntryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PermissionEntryService {

    @Autowired
    public PermissionEntryService(MenuRepository menuRepository,
                                  MenuActionRepository menuActionRepository,
                                  com.leo.erp.security.permission.PermissionService permissionService) {
    }

    public PermissionEntryService(MenuRepository menuRepository,
                                  MenuActionRepository menuActionRepository) {
    }

    @Transactional(readOnly = true)
    public Page<PermissionEntryResponse> page(PageQuery query, String keyword) {
        List<PermissionEntryResponse> rows = buildPermissionEntries().stream()
                .filter(entry -> matches(entry, keyword))
                .sorted(buildComparator(query))
                .toList();
        return toPage(rows, query);
    }

    @Transactional(readOnly = true)
    public PermissionEntryResponse detail(Long id) {
        return buildPermissionEntries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "权限不存在"));
    }

    private List<PermissionEntryResponse> buildPermissionEntries() {
        return ResourcePermissionCatalog.entries().stream()
                .flatMap(entry -> entry.actions().stream().map(action -> new PermissionEntryResponse(
                        permissionId(entry.code(), action.code()),
                        entry.code() + ":" + action.code(),
                        entry.title() + action.title(),
                        entry.group(),
                        ResourcePermissionCatalog.READ.equals(action.code()) ? "资源权限" : "操作权限",
                        action.title(),
                        "全部",
                        entry.code(),
                        "正常",
                        "系统资源动作定义"
                )))
                .toList();
    }

    private long permissionId(String resource, String action) {
        return Integer.toUnsignedLong((resource + ":" + action).hashCode());
    }

    private boolean matches(PermissionEntryResponse entry, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(entry.permissionCode(), value)
                || contains(entry.permissionName(), value)
                || contains(entry.moduleName(), value)
                || contains(entry.permissionType(), value)
                || contains(entry.actionName(), value)
                || contains(entry.resourceKey(), value);
    }

    private Comparator<PermissionEntryResponse> buildComparator(PageQuery query) {
        Comparator<PermissionEntryResponse> comparator = switch (query.sortBy() == null ? "" : query.sortBy()) {
            case "permissionCode" -> Comparator.comparing(PermissionEntryResponse::permissionCode, Comparator.nullsLast(String::compareToIgnoreCase));
            case "permissionName" -> Comparator.comparing(PermissionEntryResponse::permissionName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "moduleName" -> Comparator.comparing(PermissionEntryResponse::moduleName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "permissionType" -> Comparator.comparing(PermissionEntryResponse::permissionType, Comparator.nullsLast(String::compareToIgnoreCase));
            case "actionName" -> Comparator.comparing(PermissionEntryResponse::actionName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "status" -> Comparator.comparing(PermissionEntryResponse::status, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(PermissionEntryResponse::id, Comparator.nullsLast(Long::compareTo));
        };
        return "asc".equalsIgnoreCase(query.direction()) ? comparator : comparator.reversed();
    }

    private Page<PermissionEntryResponse> toPage(List<PermissionEntryResponse> rows, PageQuery query) {
        int start = Math.min(query.page() * query.size(), rows.size());
        int end = Math.min(start + query.size(), rows.size());
        return new PageImpl<>(rows.subList(start, end), query.toPageable("id"), rows.size());
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }
}
