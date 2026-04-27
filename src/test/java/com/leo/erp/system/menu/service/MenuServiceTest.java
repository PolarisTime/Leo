package com.leo.erp.system.menu.service;

import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.domain.entity.MenuAction;
import com.leo.erp.system.menu.repository.MenuActionRepository;
import com.leo.erp.system.menu.repository.MenuRepository;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MenuServiceTest {

    @Test
    void shouldBuildVisibleMenuTreeWithOnlyAllowedActions() {
        MenuService service = new MenuService(
                menuRepository(List.of(
                        menu(1L, "system", "系统设置", null, null, "SettingOutlined", 1, "目录"),
                        menu(2L, "print-templates", "打印模板", "system", "/print-templates", "PrinterOutlined", 2, "菜单"),
                        menu(3L, "database-management", "数据库管理", "system", "/database-management", "DatabaseOutlined", 3, "菜单")
                )),
                menuActionRepository(List.of(
                        action(21L, "print-templates", "VIEW"),
                        action(22L, "print-templates", "EDIT"),
                        action(31L, "database-management", "VIEW")
                )),
                new StubPermissionService(
                        List.of(
                                menu(1L, "system", "系统设置", null, null, "SettingOutlined", 1, "目录"),
                                menu(2L, "print-templates", "打印模板", "system", "/print-templates", "PrinterOutlined", 2, "菜单"),
                                menu(3L, "database-management", "数据库管理", "system", "/database-management", "DatabaseOutlined", 3, "菜单")
                        ),
                        Set.of("system", "print-templates"),
                        Map.of("print-template", Set.of("read"))
                )
        );

        List<MenuTreeResponse> tree = service.getMenuTree(1L);

        assertThat(tree).hasSize(1);
        MenuTreeResponse root = tree.get(0);
        assertThat(root.menuCode()).isEqualTo("system");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).menuCode()).isEqualTo("print-templates");
        assertThat(root.children().get(0).routePath()).isEqualTo("/print-templates");
        assertThat(root.children().get(0).actions()).containsExactly("read");
    }

    @Test
    void shouldReturnEmptyTreeWhenUserHasNoVisibleMenus() {
        MenuService service = new MenuService(
                menuRepository(List.of(menu(1L, "system", "系统设置", null, null, "SettingOutlined", 1, "目录"))),
                menuActionRepository(List.of()),
                new StubPermissionService(List.of(), Set.of(), Map.of())
        );

        assertThat(service.getMenuTree(2L)).isEmpty();
    }

    private MenuRepository menuRepository(List<Menu> menus) {
        return (MenuRepository) Proxy.newProxyInstance(
                MenuRepository.class.getClassLoader(),
                new Class[]{MenuRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusAndDeletedFlagFalseOrderBySortOrder" -> menus;
                    case "toString" -> "MenuRepositoryStub";
                    default -> null;
                }
        );
    }

    private MenuActionRepository menuActionRepository(List<MenuAction> actions) {
        return (MenuActionRepository) Proxy.newProxyInstance(
                MenuActionRepository.class.getClassLoader(),
                new Class[]{MenuActionRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedFlagFalse" -> actions;
                    case "toString" -> "MenuActionRepositoryStub";
                    default -> null;
                }
        );
    }

    private Menu menu(Long id,
                      String code,
                      String name,
                      String parentCode,
                      String routePath,
                      String icon,
                      Integer sortOrder,
                      String menuType) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setParentCode(parentCode);
        menu.setRoutePath(routePath);
        menu.setIcon(icon);
        menu.setSortOrder(sortOrder);
        menu.setMenuType(menuType);
        menu.setStatus("正常");
        return menu;
    }

    private MenuAction action(Long id, String menuCode, String actionCode) {
        MenuAction action = new MenuAction();
        action.setId(id);
        action.setMenuCode(menuCode);
        action.setActionCode(actionCode);
        action.setActionName(actionCode);
        return action;
    }

    private static final class StubPermissionService extends PermissionService {

        private final Set<String> menuCodes;
        private final Map<String, Set<String>> permissionMap;
        private final List<Menu> activeMenus;

        private StubPermissionService(List<Menu> activeMenus,
                                      Set<String> menuCodes,
                                      Map<String, Set<String>> permissionMap) {
            super(null, null, null, null, null, null);
            this.activeMenus = activeMenus;
            this.menuCodes = menuCodes;
            this.permissionMap = permissionMap;
        }

        @Override
        public Set<String> getVisibleMenuCodes(Long userId) {
            return menuCodes;
        }

        @Override
        public Map<String, Set<String>> getUserPermissionMap(Long userId) {
            return permissionMap;
        }

        @Override
        public List<Menu> getActiveMenus() {
            return activeMenus;
        }

    }
}
