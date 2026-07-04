package com.leo.erp.system.menu.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuTest {

    @Test
    void shouldSetAndGetAllFields() {
        Menu menu = new Menu();
        menu.setId(1L);
        menu.setMenuCode("sys:user:list");
        menu.setMenuName("用户管理");
        menu.setParentCode("sys");
        menu.setRoutePath("/system/user");
        menu.setIcon("user");
        menu.setSortOrder(10);
        menu.setMenuType("MENU");
        menu.setStatus("启用");

        assertThat(menu.getId()).isEqualTo(1L);
        assertThat(menu.getMenuCode()).isEqualTo("sys:user:list");
        assertThat(menu.getMenuName()).isEqualTo("用户管理");
        assertThat(menu.getParentCode()).isEqualTo("sys");
        assertThat(menu.getRoutePath()).isEqualTo("/system/user");
        assertThat(menu.getIcon()).isEqualTo("user");
        assertThat(menu.getSortOrder()).isEqualTo(10);
        assertThat(menu.getMenuType()).isEqualTo("MENU");
        assertThat(menu.getStatus()).isEqualTo("启用");
    }

    @Test
    void shouldHandleNullValues() {
        Menu menu = new Menu();
        menu.setId(null);
        menu.setMenuCode(null);
        menu.setMenuName(null);
        menu.setParentCode(null);
        menu.setRoutePath(null);
        menu.setIcon(null);
        menu.setSortOrder(null);
        menu.setMenuType(null);
        menu.setStatus(null);

        assertThat(menu.getId()).isNull();
        assertThat(menu.getMenuCode()).isNull();
        assertThat(menu.getMenuName()).isNull();
        assertThat(menu.getParentCode()).isNull();
        assertThat(menu.getRoutePath()).isNull();
        assertThat(menu.getIcon()).isNull();
        assertThat(menu.getSortOrder()).isNull();
        assertThat(menu.getMenuType()).isNull();
        assertThat(menu.getStatus()).isNull();
    }

    @Test
    void shouldHandleRootMenuWithoutParent() {
        Menu menu = new Menu();
        menu.setId(1L);
        menu.setMenuCode("sys");
        menu.setMenuName("系统管理");
        menu.setParentCode(null);

        assertThat(menu.getParentCode()).isNull();
    }

    @Test
    void shouldSetAndGetMenuActionFields() {
        MenuAction action = new MenuAction();

        action.setId(1L);
        action.setMenuCode("system:company");
        action.setActionCode("read");
        action.setActionName("查看");

        assertThat(action.getId()).isEqualTo(1L);
        assertThat(action.getMenuCode()).isEqualTo("system:company");
        assertThat(action.getActionCode()).isEqualTo("read");
        assertThat(action.getActionName()).isEqualTo("查看");
    }
}
