package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardSummaryServiceTest {

    @Test
    void shouldBuildRealtimeSummaryFromRepositories() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        CompanySettingRepository companySettingRepository = mock(CompanySettingRepository.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        RefreshTokenSessionRepository refreshTokenSessionRepository = mock(RefreshTokenSessionRepository.class);

        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("leo");
        user.setUserName("Leo");
        user.setRoleName("系统管理员");
        user.setTotpEnabled(Boolean.TRUE);
        user.setStatus(UserStatus.NORMAL);
        user.setLastLoginDate(LocalDateTime.of(2026, 4, 26, 9, 30));
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        CompanySetting company = new CompanySetting();
        company.setCompanyName("演示公司");
        when(companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc("正常"))
                .thenReturn(Optional.of(company));

        Menu dashboard = menu("dashboard", "/dashboard", "菜单");
        Menu purchase = menu("purchase", null, "目录");
        Menu purchaseOrder = menu("purchase-orders", "/purchase-orders", "菜单");
        when(menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder("正常"))
                .thenReturn(List.of(dashboard, purchase, purchaseOrder));

        when(permissionService.getVisibleMenuCodes(1L)).thenReturn(Set.of("dashboard", "purchase", "purchase-orders"));
        when(permissionService.getUserPermissionMap(1L)).thenReturn(Map.of(
                "dashboard", Set.of("read"),
                "purchase-order", Set.of("read", "update")
        ));
        when(permissionService.getActiveMenus()).thenReturn(List.of(dashboard, purchase, purchaseOrder));

        when(refreshTokenSessionRepository.countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(
                any(Long.class), any(LocalDateTime.class)))
                .thenReturn(2L);

        DashboardSummaryService service = new DashboardSummaryService(
                userAccountRepository,
                companySettingRepository,
                menuRepository,
                permissionService,
                refreshTokenSessionRepository,
                "leo"
        );

        DashboardSummaryResponse response = service.getSummary(1L);

        assertThat(response.appName()).isEqualTo("leo");
        assertThat(response.companyName()).isEqualTo("演示公司");
        assertThat(response.userName()).isEqualTo("Leo");
        assertThat(response.loginName()).isEqualTo("leo");
        assertThat(response.roleName()).isEqualTo("系统管理员");
        assertThat(response.visibleMenuCount()).isEqualTo(3);
        assertThat(response.moduleCount()).isEqualTo(2);
        assertThat(response.actionCount()).isEqualTo(3);
        assertThat(response.activeSessionCount()).isEqualTo(2);
        assertThat(response.totpEnabled()).isTrue();
        assertThat(response.lastLoginAt()).isEqualTo(LocalDateTime.of(2026, 4, 26, 9, 30));
        assertThat(response.serverTime()).isNotNull();
    }

    private Menu menu(String menuCode, String routePath, String menuType) {
        Menu menu = new Menu();
        menu.setMenuCode(menuCode);
        menu.setRoutePath(routePath);
        menu.setMenuType(menuType);
        return menu;
    }
}
