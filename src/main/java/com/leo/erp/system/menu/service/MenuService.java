package com.leo.erp.system.menu.service;

import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuActionRepository;
import com.leo.erp.system.menu.repository.MenuRepository;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MenuService {

    private final PermissionService permissionService;

    public MenuService(MenuRepository menuRepository,
                       MenuActionRepository menuActionRepository,
                       PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public List<MenuTreeResponse> getMenuTree(Long userId) {
        Set<String> visibleMenuCodes = permissionService.getVisibleMenuCodes(userId);
        if (visibleMenuCodes.isEmpty()) {
            return List.of();
        }
        Map<String, Set<String>> permissionMap = permissionService.getUserPermissionMap(userId);
        List<Menu> menus = permissionService.getActiveMenus().stream()
                .filter(menu -> visibleMenuCodes.contains(menu.getMenuCode()))
                .toList();

        List<MenuTreeResponse> roots = new ArrayList<>();
        Map<String, List<MenuTreeResponse>> childrenMap = new HashMap<>();

        for (Menu menu : menus) {
            List<String> actions = ResourcePermissionCatalog.resolveResourceByMenuCode(menu.getMenuCode())
                    .map(resource -> permissionMap.getOrDefault(resource, Set.of()).stream().toList())
                    .orElse(List.of());
            MenuTreeResponse node = new MenuTreeResponse(
                    menu.getMenuCode(),
                    menu.getMenuName(),
                    menu.getParentCode(),
                    menu.getRoutePath(),
                    menu.getIcon(),
                    menu.getSortOrder(),
                    menu.getMenuType(),
                    actions,
                    new ArrayList<>()
            );

            if (menu.getParentCode() == null) {
                roots.add(node);
            } else {
                childrenMap.computeIfAbsent(menu.getParentCode(), key -> new ArrayList<>()).add(node);
            }
        }

        for (MenuTreeResponse root : roots) {
            setChildren(root, childrenMap);
        }
        return roots;
    }

    private void setChildren(MenuTreeResponse node, Map<String, List<MenuTreeResponse>> childrenMap) {
        List<MenuTreeResponse> children = childrenMap.getOrDefault(node.menuCode(), List.of());
        node.children().addAll(children);
        for (MenuTreeResponse child : children) {
            setChildren(child, childrenMap);
        }
    }
}
