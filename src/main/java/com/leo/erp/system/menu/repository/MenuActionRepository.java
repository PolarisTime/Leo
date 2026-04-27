package com.leo.erp.system.menu.repository;

import com.leo.erp.system.menu.domain.entity.MenuAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuActionRepository extends JpaRepository<MenuAction, Long> {

    List<MenuAction> findByMenuCodeAndDeletedFlagFalse(String menuCode);

    List<MenuAction> findByDeletedFlagFalse();

    List<MenuAction> findByDeletedFlagFalseOrderByMenuCodeAscActionCodeAsc();

    boolean existsByMenuCodeAndActionCodeAndDeletedFlagFalse(String menuCode, String actionCode);
}
