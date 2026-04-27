package com.leo.erp.system.menu.web.dto;

import java.util.List;

public record MenuTreeResponse(
        String menuCode,
        String menuName,
        String parentCode,
        String routePath,
        String icon,
        Integer sortOrder,
        String menuType,
        List<String> actions,
        List<MenuTreeResponse> children
) {
}
