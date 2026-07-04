package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSummaryServiceTest {

    @Test
    void shouldBuildRealtimeSummaryFromRepositories() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        CompanySettingRepository companySettingRepository = mock(CompanySettingRepository.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        UserRoleBindingService userRoleBindingService = mock(UserRoleBindingService.class);
        RefreshTokenSessionRepository refreshTokenSessionRepository = mock(RefreshTokenSessionRepository.class);

        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("leo");
        user.setUserName("Leo");
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
        Menu purchaseOrder = menu("purchase-order", "/purchase-order", "菜单");
        when(menuRepository.findByStatusAndDeletedFlagFalseOrderBySortOrder("正常"))
                .thenReturn(List.of(dashboard, purchase, purchaseOrder));

        when(permissionService.getVisibleMenuCodes(1L)).thenReturn(Set.of("dashboard", "purchase", "purchase-order"));
        when(permissionService.getUserPermissionMap(1L)).thenReturn(Map.of(
                "dashboard", Set.of("read"),
                "purchase-order", Set.of("read", "update")
        ));
        when(permissionService.getActiveMenus()).thenReturn(List.of(dashboard, purchase, purchaseOrder));

        when(refreshTokenSessionRepository.countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(
                any(Long.class), any(LocalDateTime.class)))
                .thenReturn(2L);

        RoleSetting mockRole = new RoleSetting();
        mockRole.setRoleName("系统管理员");
        when(userRoleBindingService.resolveRolesForUser(1L)).thenReturn(List.of(mockRole));

        DashboardSummaryService service = new DashboardSummaryService(
                userAccountRepository,
                companySettingRepository,
                menuRepository,
                permissionService,
                userRoleBindingService,
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

    @Test
    void shouldBuildSummaryAndEvictDashboardKeys() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        CompanySettingRepository companySettingRepository = mock(CompanySettingRepository.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        UserRoleBindingService userRoleBindingService = mock(UserRoleBindingService.class);
        RefreshTokenSessionRepository refreshTokenSessionRepository = mock(RefreshTokenSessionRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        RedisJsonCacheSupport redisJsonCacheSupport = mock(RedisJsonCacheSupport.class);

        UserAccount user = new UserAccount();
        user.setLoginName("leo");
        user.setUserName("Leo");
        user.setTotpEnabled(Boolean.FALSE);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));

        CompanySetting fallbackCompany = new CompanySetting();
        fallbackCompany.setCompanyName("备用公司");
        when(companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc("正常"))
                .thenReturn(Optional.empty());
        when(companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc())
                .thenReturn(Optional.of(fallbackCompany));

        when(permissionService.getVisibleMenuCodes(1L)).thenReturn(Collections.emptySet());
        when(permissionService.getUserPermissionMap(1L)).thenReturn(Map.of());
        Menu dashboard = menu("dashboard", "/dashboard", "菜单");
        when(permissionService.getActiveMenus()).thenReturn(List.of(dashboard));
        when(refreshTokenSessionRepository.countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(
                eq(1L), any(LocalDateTime.class)))
                .thenReturn(1L);
        when(userRoleBindingService.resolveRolesForUser(1L)).thenThrow(new IllegalStateException("角色异常"));
        when(materialRepository.countByDeletedFlagFalse()).thenReturn(10L);
        when(supplierRepository.countByDeletedFlagFalse()).thenReturn(20L);
        when(customerRepository.countByDeletedFlagFalse()).thenReturn(30L);

        DashboardSummaryService service = new DashboardSummaryService(
                userAccountRepository,
                companySettingRepository,
                menuRepository,
                permissionService,
                userRoleBindingService,
                refreshTokenSessionRepository,
                materialRepository,
                supplierRepository,
                customerRepository,
                redisJsonCacheSupport,
                "leo"
        );

        DashboardSummaryResponse response = service.getSummary(1L);
        service.evictCache(1L);
        service.evictCache(null);
        service.evictAllCache();

        assertThat(response.companyName()).isEqualTo("备用公司");
        assertThat(response.roleName()).isEmpty();
        assertThat(response.visibleMenuCount()).isEqualTo(1);
        assertThat(response.moduleCount()).isEqualTo(1);
        assertThat(response.materialCount()).isEqualTo(10L);
        assertThat(response.supplierCount()).isEqualTo(20L);
        assertThat(response.customerCount()).isEqualTo(30L);
        verify(redisJsonCacheSupport).delete("leo:dashboard:1");
        verify(redisJsonCacheSupport, never()).delete("leo:dashboard:null");
        verify(redisJsonCacheSupport).deleteByPattern("leo:dashboard:*");
    }

    @Test
    void shouldDeclareSpringCacheAnnotationsForDashboardSummary() throws Exception {
        Method getSummary = DashboardSummaryService.class.getDeclaredMethod("getSummary", Long.class);
        Cacheable cacheable = getSummary.getAnnotation(Cacheable.class);
        assertThat(cacheable.value()).containsExactly(CacheConfig.CACHE_HOT);
        assertThat(cacheable.key()).isEqualTo("'leo:dashboard:' + #userId");

        Method evictCache = DashboardSummaryService.class.getDeclaredMethod("evictCache", Long.class);
        CacheEvict cacheEvict = evictCache.getAnnotation(CacheEvict.class);
        assertThat(cacheEvict.value()).containsExactly(CacheConfig.CACHE_HOT);
        assertThat(cacheEvict.key()).isEqualTo("'leo:dashboard:' + #userId");
    }

    @Test
    void shouldSkipEvictionWhenCacheIsUnavailable() {
        DashboardSummaryService service = new DashboardSummaryService(
                mock(UserAccountRepository.class),
                mock(CompanySettingRepository.class),
                mock(MenuRepository.class),
                mock(PermissionService.class),
                mock(UserRoleBindingService.class),
                mock(RefreshTokenSessionRepository.class),
                "leo"
        );

        service.evictCache(1L);
        service.evictAllCache();
    }

    @Test
    void shouldRejectSummaryWhenUserIsMissing() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());
        DashboardSummaryService service = new DashboardSummaryService(
                userAccountRepository,
                mock(CompanySettingRepository.class),
                mock(MenuRepository.class),
                mock(PermissionService.class),
                mock(UserRoleBindingService.class),
                mock(RefreshTokenSessionRepository.class),
                "leo"
        );

        assertThatThrownBy(() -> service.getSummary(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void shouldJoinMultipleRoleNames() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        CompanySettingRepository companySettingRepository = mock(CompanySettingRepository.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        PermissionService permissionService = mock(PermissionService.class);
        UserRoleBindingService userRoleBindingService = mock(UserRoleBindingService.class);
        RefreshTokenSessionRepository refreshTokenSessionRepository = mock(RefreshTokenSessionRepository.class);

        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setLoginName("leo");
        user.setUserName("Leo");
        when(userAccountRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(user));
        when(companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc("正常")).thenReturn(Optional.empty());
        when(companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc()).thenReturn(Optional.empty());
        when(permissionService.getVisibleMenuCodes(1L)).thenReturn(Set.of());
        when(permissionService.getUserPermissionMap(1L)).thenReturn(Map.of());
        when(permissionService.getActiveMenus()).thenReturn(List.of(menu("dashboard", "/dashboard", "菜单")));
        RoleSetting admin = new RoleSetting();
        admin.setRoleName("系统管理员");
        RoleSetting auditor = new RoleSetting();
        auditor.setRoleName("审计员");
        when(userRoleBindingService.resolveRolesForUser(1L)).thenReturn(List.of(admin, auditor));

        DashboardSummaryService service = new DashboardSummaryService(
                userAccountRepository,
                companySettingRepository,
                menuRepository,
                permissionService,
                userRoleBindingService,
                refreshTokenSessionRepository,
                "leo"
        );

        DashboardSummaryResponse response = service.getSummary(1L);

        assertThat(response.roleName()).isEqualTo("系统管理员,审计员");
    }

    private Menu menu(String menuCode, String routePath, String menuType) {
        Menu menu = new Menu();
        menu.setMenuCode(menuCode);
        menu.setRoutePath(routePath);
        menu.setMenuType(menuType);
        return menu;
    }
}
