package com.leo.erp.security.permission;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class MenuVisibilityService {

    private final MenuRepository menuRepository;

    MenuVisibilityService(MenuRepository menuRepository, Optional<RedisJsonCacheSupport> redisJsonCacheSupport) {
        this.menuRepository = menuRepository;
    }

    Set<String> getVisibleMenuCodes(Map<String, Set<String>> permissionMap) {
        Set<String> menuCodes = new LinkedHashSet<>(ResourcePermissionCatalog.resolveVisibleMenuCodes(permissionMap));
        List<Menu> allMenus = getActiveMenus();
        Map<String, String> parentMap = allMenus.stream()
                .filter(menu -> menu.getParentCode() != null)
                .collect(Collectors.toMap(Menu::getMenuCode, Menu::getParentCode, (left, right) -> left));

        Set<String> withParents = new LinkedHashSet<>(menuCodes);
        for (String code : menuCodes) {
            String parent = parentMap.get(code);
            while (parent != null) {
                withParents.add(parent);
                parent = parentMap.get(parent);
            }
        }
        return withParents;
    }

    List<Menu> getActiveMenus() {
        return loadActiveMenuSnapshots().stream()
                .map(snapshot -> {
                    Menu menu = new Menu();
                    menu.setMenuCode(snapshot.menuCode());
                    menu.setMenuName(snapshot.menuName());
                    menu.setParentCode(snapshot.parentCode());
                    menu.setRoutePath(snapshot.routePath());
                    menu.setIcon(snapshot.icon());
                    menu.setSortOrder(snapshot.sortOrder());
                    menu.setMenuType(snapshot.menuType());
                    menu.setStatus(snapshot.status());
                    return menu;
                })
                .toList();
    }

    private List<MenuSnapshot> loadActiveMenuSnapshots() {
        if (menuRepository == null) {
            return List.of();
        }
        return menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder(StatusConstants.NORMAL).stream()
                .map(MenuVisibilityService::toMenuSnapshot)
                .toList();
    }

    private static MenuSnapshot toMenuSnapshot(Menu menu) {
        return new MenuSnapshot(
                menu.getMenuCode(),
                menu.getMenuName(),
                menu.getParentCode(),
                menu.getRoutePath(),
                menu.getIcon(),
                menu.getSortOrder(),
                menu.getMenuType(),
                menu.getStatus()
        );
    }

    private record MenuSnapshot(
            String menuCode,
            String menuName,
            String parentCode,
            String routePath,
            String icon,
            Integer sortOrder,
            String menuType,
            String status
    ) {
    }
}
