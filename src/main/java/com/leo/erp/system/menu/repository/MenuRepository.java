package com.leo.erp.system.menu.repository;

import com.leo.erp.system.menu.domain.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {

    List<Menu> findByDeletedFlagFalseOrderBySortOrderAscIdAsc();

    List<Menu> findByStatusAndDeletedFlagFalseOrderBySortOrder(String status);

    @Query("""
            select concat(count(m), ':', coalesce(max(coalesce(m.updatedAt, m.createdAt)), current_timestamp))
            from Menu m
            where m.status = :status and m.deletedFlag = false
            """)
    String activeMenuCacheSignature(String status);
}
