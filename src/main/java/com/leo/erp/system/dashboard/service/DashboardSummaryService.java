package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.RefreshTokenSessionRepository;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import com.leo.erp.system.menu.domain.entity.Menu;
import com.leo.erp.system.menu.repository.MenuRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DashboardSummaryService {

    private static final String DASHBOARD_CACHE_PREFIX = "leo:dashboard:";
    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofMinutes(10);

    private final UserAccountRepository userAccountRepository;
    private final CompanySettingRepository companySettingRepository;
    private final MenuRepository menuRepository;
    private final RefreshTokenSessionRepository refreshTokenSessionRepository;
    private final MaterialRepository materialRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final String appName;

    public DashboardSummaryService(UserAccountRepository userAccountRepository,
                                   CompanySettingRepository companySettingRepository,
                                   MenuRepository menuRepository,
                                   RefreshTokenSessionRepository refreshTokenSessionRepository,
                                   MaterialRepository materialRepository,
                                   SupplierRepository supplierRepository,
                                   CustomerRepository customerRepository,
                                   RedisJsonCacheSupport redisJsonCacheSupport,
                                   @Value("${spring.application.name:leo}") String appName) {
        this.userAccountRepository = userAccountRepository;
        this.companySettingRepository = companySettingRepository;
        this.menuRepository = menuRepository;
        this.refreshTokenSessionRepository = refreshTokenSessionRepository;
        this.materialRepository = materialRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
        this.appName = appName;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(Long userId) {
        String cacheKey = dashboardCacheKey(userId);
        var cached = redisJsonCacheSupport.read(cacheKey, DashboardSummaryResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }
        DashboardSummaryResponse summary = buildSummary(userId);
        redisJsonCacheSupport.write(cacheKey, summary, DASHBOARD_CACHE_TTL);
        return summary;
    }

    public void evictCache(Long userId) {
        if (userId != null) {
            redisJsonCacheSupport.delete(dashboardCacheKey(userId));
        }
    }

    public void evictAllCache() {
        redisJsonCacheSupport.deleteByPattern(DASHBOARD_CACHE_PREFIX + "*");
    }

    private DashboardSummaryResponse buildSummary(Long userId) {
        UserAccount user = userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        LocalDateTime now = LocalDateTime.now();
        List<Menu> visibleMenus = menuRepository
                .findByStatusAndDeletedFlagFalseOrderBySortOrder(StatusConstants.NORMAL);
        long moduleCount = visibleMenus.stream()
                .filter(menu -> "菜单".equals(menu.getMenuType()))
                .filter(menu -> StringUtils.hasText(menu.getRoutePath()))
                .count();
        long activeSessionCount = refreshTokenSessionRepository
                .countByUserIdAndDeletedFlagFalseAndRevokedAtIsNullAndExpiresAtAfter(userId, now);

        return new DashboardSummaryResponse(
                appName,
                resolveCompanyName(),
                user.getUserName(),
                user.getLoginName(),
                visibleMenus.size(),
                moduleCount,
                activeSessionCount,
                user.getLastLoginDate(),
                now,
                materialRepository.countByDeletedFlagFalse(),
                supplierRepository.countByDeletedFlagFalse(),
                customerRepository.countByDeletedFlagFalse()
        );
    }

    private String resolveCompanyName() {
        return companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc(StatusConstants.NORMAL)
                .map(CompanySetting::getCompanyName)
                .or(() -> companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc()
                        .map(CompanySetting::getCompanyName))
                .orElse(null);
    }

    private String dashboardCacheKey(Long userId) {
        return DASHBOARD_CACHE_PREFIX + userId;
    }
}
