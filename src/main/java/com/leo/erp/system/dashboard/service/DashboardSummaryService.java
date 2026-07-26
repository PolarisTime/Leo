package com.leo.erp.system.dashboard.service;

import com.leo.erp.auth.api.AccountQuery;
import com.leo.erp.auth.api.SessionStatisticsQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.MasterDataStatisticsQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.dashboard.web.dto.DashboardSummaryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class DashboardSummaryService {

    private static final String DASHBOARD_CACHE_PREFIX = "leo:dashboard:";
    private static final Duration DASHBOARD_CACHE_TTL = Duration.ofMinutes(10);

    private final AccountQuery accountQuery;
    private final CompanySettingRepository companySettingRepository;
    private final SessionStatisticsQuery sessionStatisticsQuery;
    private final MasterDataStatisticsQuery masterDataStatisticsQuery;
    private final RedisJsonCacheSupport redisJsonCacheSupport;
    private final String appName;

    public DashboardSummaryService(AccountQuery accountQuery,
                                   CompanySettingRepository companySettingRepository,
                                   SessionStatisticsQuery sessionStatisticsQuery,
                                   MasterDataStatisticsQuery masterDataStatisticsQuery,
                                   RedisJsonCacheSupport redisJsonCacheSupport,
                                   @Value("${spring.application.name:leo}") String appName) {
        this.accountQuery = accountQuery;
        this.companySettingRepository = companySettingRepository;
        this.sessionStatisticsQuery = sessionStatisticsQuery;
        this.masterDataStatisticsQuery = masterDataStatisticsQuery;
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
        AccountQuery.AccountSnapshot account = accountQuery.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        LocalDateTime now = LocalDateTime.now();
        long activeSessionCount = sessionStatisticsQuery.countActiveSessions(userId, now);

        MasterDataStatisticsQuery.MasterDataStatistics statistics = masterDataStatisticsQuery.countActiveRecords();
        return new DashboardSummaryResponse(
                appName,
                resolveCompanyName(),
                account.userName(),
                account.loginName(),
                activeSessionCount,
                account.lastLoginAt(),
                now,
                statistics.materialCount(),
                statistics.supplierCount(),
                statistics.customerCount()
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
