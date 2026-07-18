package com.leo.erp.system.menu.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.menu.repository.MenuRepository;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.web.dto.MenuTreeResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public List<MenuTreeResponse> getMenuTree() {
        List<Menu> menus = menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder(StatusConstants.NORMAL);

        List<MenuTreeResponse> roots = new ArrayList<>();
        Map<String, List<MenuTreeResponse>> childrenMap = new HashMap<>();

        for (Menu menu : menus) {
            MenuTreeResponse node = new MenuTreeResponse(
                    menu.getMenuCode(),
                    menu.getMenuName(),
                    menu.getParentCode(),
                    menu.getRoutePath(),
                    menu.getIcon(),
                    menu.getSortOrder(),
                    menu.getMenuType(),
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
