package com.leo.erp.market.quotation.service;

import com.leo.erp.common.support.KeyedLockRegistry;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 比价项目级配置: 按项目唯一, PUT 整体替换。
 * <p>
 * 同一项目的写入在本进程内串行化, 并对唯一键/乐观锁冲突做有限重试,
 * 避免并发 PUT(或前端重复提交)触发 409。
 */
@Slf4j
@Service
public class QuoteProjectConfigService {

    private static final int MAX_ATTEMPTS = 3;

    private final QuoteProjectConfigStore store;
    private final KeyedLockRegistry locks = new KeyedLockRegistry();

    public QuoteProjectConfigService(QuoteProjectConfigStore store) {
        this.store = store;
    }

    /** 查询项目配置; 未配置时返回默认空配置。 */
    public QuoteProjectConfigResponse find(Long projectId) {
        return store.find(projectId);
    }

    /** 保存项目配置(不存在则创建, 存在则整体替换; 幂等)。 */
    public QuoteProjectConfigResponse save(Long projectId, QuoteProjectConfigRequest request, Long expectedVersion) {
        return locks.execute(projectId, () -> saveWithRetry(projectId, request, expectedVersion));
    }

    private QuoteProjectConfigResponse saveWithRetry(Long projectId, QuoteProjectConfigRequest request,
                                                     Long expectedVersion) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                QuoteProjectConfigResponse saved = store.save(projectId, request, expectedVersion);
                // FORCE_INCREMENT 在写事务提交后才应用, 存储层 flush 后构造的版本会落后 1;
                // 以新事务回读权威版本, 保证对外 X-Resource-Version 与数据库一致。
                Long version = store.currentVersion(projectId);
                return version == null ? saved : saved.withVersion(version);
            } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException
                     | CannotAcquireLockException ex) {
                lastError = ex;
                log.warn("比价项目配置保存冲突, 重试 {}/{}: projectId={}, {}",
                        attempt, MAX_ATTEMPTS, projectId, ex.getMessage());
            }
        }
        throw lastError == null
                ? new IllegalStateException("比价项目配置保存失败")
                : lastError;
    }
}
