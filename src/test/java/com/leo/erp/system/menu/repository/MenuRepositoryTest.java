package com.leo.erp.system.menu.repository;

import com.leo.erp.system.menu.domain.entity.Menu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MenuRepositoryTest {

    @Mock
    private MenuRepository repository;

    @Test
    void findByDeletedFlagFalseOrderBySortOrderAscIdAsc_shouldReturnNonDeletedMenus() {
        Menu menu1 = new Menu();
        menu1.setId(1L);
        menu1.setMenuCode("MENU001");
        menu1.setMenuName("菜单A");
        menu1.setSortOrder(1);
        menu1.setDeletedFlag(false);

        Menu menu2 = new Menu();
        menu2.setId(2L);
        menu2.setMenuCode("MENU002");
        menu2.setMenuName("菜单B");
        menu2.setSortOrder(2);
        menu2.setDeletedFlag(false);

        when(repository.findByDeletedFlagFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of(menu1, menu2));

        List<Menu> result = repository.findByDeletedFlagFalseOrderBySortOrderAscIdAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMenuCode()).isEqualTo("MENU001");
        assertThat(result.get(1).getMenuCode()).isEqualTo("MENU002");
    }

    @Test
    void findByDeletedFlagFalseOrderBySortOrderAscIdAsc_shouldReturnEmptyWhenAllDeleted() {
        when(repository.findByDeletedFlagFalseOrderBySortOrderAscIdAsc()).thenReturn(List.of());

        List<Menu> result = repository.findByDeletedFlagFalseOrderBySortOrderAscIdAsc();

        assertThat(result).isEmpty();
    }

    @Test
    void findByStatusAndDeletedFlagFalseOrderBySortOrder_shouldReturnMatchingMenus() {
        Menu menu1 = new Menu();
        menu1.setId(1L);
        menu1.setMenuCode("MENU001");
        menu1.setMenuName("菜单A");
        menu1.setStatus("ACTIVE");
        menu1.setSortOrder(2);
        menu1.setDeletedFlag(false);

        Menu menu2 = new Menu();
        menu2.setId(2L);
        menu2.setMenuCode("MENU002");
        menu2.setMenuName("菜单B");
        menu2.setStatus("ACTIVE");
        menu2.setSortOrder(1);
        menu2.setDeletedFlag(false);

        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrder("ACTIVE"))
                .thenReturn(List.of(menu2, menu1));

        List<Menu> result = repository.findByStatusAndDeletedFlagFalseOrderBySortOrder("ACTIVE");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMenuCode()).isEqualTo("MENU002");
        assertThat(result.get(1).getMenuCode()).isEqualTo("MENU001");
    }
}
