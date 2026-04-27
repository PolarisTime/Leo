package com.leo.erp.security.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
class MenuVisibilityService {

    static final String MENU_CACHE_KEY = "leo:menu:all";
    static final Duration MENU_CACHE_TTL = Duration.ofMinutes(30);
    private static final TypeReference<List<MenuSnapshot>> MENU_LIST_TYPE = new TypeReference<>() { };

    private final MenuRepository menuRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    MenuVisibilityService(MenuRepository menuRepository, Optional<RedisJsonCacheSupport> redisJsonCacheSupport) {
        this.menuRepository = menuRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport == null ? null : redisJsonCacheSupport.orElse(null);
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
        if (redisJsonCacheSupport == null) {
            return menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder(StatusConstants.NORMAL).stream()
                    .map(MenuVisibilityService::toMenuSnapshot)
                    .toList();
        }
        return redisJsonCacheSupport.getOrLoad(
                MENU_CACHE_KEY,
                MENU_CACHE_TTL,
                MENU_LIST_TYPE,
                () -> menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder(StatusConstants.NORMAL).stream()
                        .map(MenuVisibilityService::toMenuSnapshot)
                        .toList()
        );
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
