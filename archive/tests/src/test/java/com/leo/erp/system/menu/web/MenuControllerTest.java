package com.leo.erp.system.menu.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.menu.service.MenuService;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuControllerTest {

    private final MenuService menuService = mock(MenuService.class);
    private final MenuController controller = new MenuController(menuService);

    @Test
    void treeReturnsMenuTree() {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        when(principal.id()).thenReturn(1L);
        MenuTreeResponse item = mock(MenuTreeResponse.class);
        when(menuService.getMenuTree(eq(1L))).thenReturn(List.of(item));

        ApiResponse<List<MenuTreeResponse>> response = controller.tree(principal);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).hasSize(1);
    }
}
