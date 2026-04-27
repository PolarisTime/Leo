package com.leo.erp.system.menu.repository;

import com.leo.erp.system.menu.domain.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByDeletedFlagFalseOrderBySortOrderAscIdAsc();

    List<Menu> findByStatusAndDeletedFlagFalseOrderBySortOrder(String status);
}
