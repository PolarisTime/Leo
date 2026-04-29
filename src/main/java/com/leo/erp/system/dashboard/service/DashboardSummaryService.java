package com.leo.erp.system.dashboard.service;

import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DashboardSummaryService {

    private static final String DASHBOARD_CACHE_PREFIX = "leo:dashboard:";
    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofMinutes(2);

    private final UserAccountRepository userAccountRepository;
    private final CompanySettingRepository companySettingRepository;
    private final MenuRepository menuRepository;
    private final PermissionService permissionService;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final MaterialRepository materialRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final String appName;

    @Autowired
    public DashboardSummaryService(UserAccountRepository userAccountRepository,
                                   CompanySettingRepository companySettingRepository,
                                   MenuRepository menuRepository,
                                   PermissionService permissionService,
                                   RefreshTokenSessionRepository refreshTokenSessionRepository,
                                   MaterialRepository materialRepository,
                                   SupplierRepository supplierRepository,
                                   CustomerRepository customerRepository,
                                   RedisJsonCacheSupport redisJsonCacheSupport,
                                   @Value("${spring.application.name:leo}") String appName) {
        this.userAccountRepository = userAccountRepository;
        this.companySettingRepository = companySettingRepository;
        this.menuRepository = menuRepository;
        this.permissionService = permissionService;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.materialRepository = materialRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.appName = appName;
    }

    public DashboardSummaryService(UserAccountRepository userAccountRepository,
                                   CompanySettingRepository companySettingRepository,
                                   MenuRepository menuRepository,
                                   PermissionService permissionService,
                                   RefreshTokenSessionRepository refreshTokenSessionRepository,
                                   String appName) {
        this(userAccountRepository, companySettingRepository, menuRepository, permissionService, refreshTokenSessionRepository, null, null, null, null, appName);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long userId) {
        if (redisJsonCacheSupport == null) {
            return buildSummary(userId);
        }
        return redisJsonCacheSupport.getOrLoad(
                dashboardCacheKey(userId),
                DASHBOARD_CACHE_TTL,
                DashboardSummaryResponse.class,
                () -> buildSummary(userId)
        );
    }

    public void evictCache(Long userId) {
        if (redisJsonCacheSupport == null || userId == null) {
            return;
        }
        redisJsonCacheSupport.delete(dashboardCacheKey(userId));
    }

    public void evictAllCache() {
        if (redisJsonCacheSupport == null) {
            return;
        }
        redisJsonCacheSupport.deleteByPattern(DASHBOARD_CACHE_PREFIX + "*");
    }

    private DashboardSummaryResponse buildSummary(Long userId) {
        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        LocalDateTime now = LocalDateTime.now();

        Set<String> visibleMenuCodes = new LinkedHashSet<>(permissionService.getVisibleMenuCodes(userId));
        visibleMenuCodes.add("dashboard");
        List<Menu> visibleMenus = resolveVisibleMenus(visibleMenuCodes);
        long moduleCount = visibleMenus.stream()
                .filter(menu -> "菜单".equals(menu.getMenuType()))
                .filter(menu -> StringUtils.hasText(menu.getRoutePath()))
                .count();
        long actionCount = permissionService.getUserPermissionMap(userId)
                .values()
                .stream()
                .mapToLong(Set::size)
                .sum();
        long activeSessionCount = refreshTokenSessionRepository
                .countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(userId, now);

        long materialCount = materialRepository != null ? materialRepository.countByDeletedFlagFalse() : 0L;
        long supplierCount = supplierRepository != null ? supplierRepository.countByDeletedFlagFalse() : 0L;
        long customerCount = customerRepository != null ? customerRepository.countByDeletedFlagFalse() : 0L;

        return new DashboardSummaryResponse(
                appName,
                resolveCompanyName(),
                user.getUserName(),
                user.getLoginName(),
                user.getRoleName(),
                visibleMenus.size(),
                moduleCount,
                actionCount,
                activeSessionCount,
                Boolean.TRUE.equals(user.getTotpEnabled()),
                user.getLastLoginDate(),
                now,
                materialCount,
                supplierCount,
                customerCount
        );
    }

    private List<Menu> resolveVisibleMenus(Set<String> visibleMenuCodes) {
        return permissionService.getActiveMenus().stream()
                .filter(menu -> visibleMenuCodes.contains(menu.getMenuCode()))
                .toList();
    }

    private String resolveCompanyName() {
        return companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc("正常")
                .map(CompanySetting::getCompanyName)
                .or(() -> companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc().map(CompanySetting::getCompanyName))
                .orElse(null);
    }

    private String dashboardCacheKey(Long userId) {
        return DASHBOARD_CACHE_PREFIX + userId;
    }
}
