package com.leo.erp.system.menu.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.menu.service.MenuService;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/system/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/tree")
    public ApiResponse<List<MenuTreeResponse>> tree(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ApiResponse.success(menuService.getMenuTree(principal.id()));
    }
}
