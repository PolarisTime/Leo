package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetEditLock;
import com.leo.erp.market.quotation.repository.QuoteSheetEditLockRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetEditLockResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 报价单编辑签出锁: 金额敏感单据的悲观编辑保护。
 * <p>TTL 默认 120 秒, 到期后可被他人抢占; 本人续约幂等。</p>
 * <p>签出/续约通过数据库行级排他锁({@code SELECT ... FOR UPDATE})串行化, 多副本部署下同样正确。</p>
 */
@Slf4j
@Service
public class QuoteSheetEditLockService {

    /** 默认锁租期(秒)。 */
    public static final long DEFAULT_TTL_SECONDS = 120;

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(DEFAULT_TTL_SECONDS);

    private final QuoteSheetEditLockRepository repository;
    private final QuoteSheetRepository sheetRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public QuoteSheetEditLockService(QuoteSheetEditLockRepository repository,
                                     QuoteSheetRepository sheetRepository,
                                     SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.sheetRepository = sheetRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 签出或续约(幂等)。
     * <ul>
     *   <li>无锁 / 已过期: 获取或抢占;</li>
     *   <li>本人持有: 续约;</li>
     *   <li>他人持有且未过期: 409。</li>
     * </ul>
     */
    @Transactional
    public QuoteSheetEditLockResponse acquire(Long sheetId, Long ownerId, String ownerName) {
        return acquire(sheetId, ownerId, ownerName, false);
    }

    /**
     * 签出或续约, 支持显式强制接管。
     *
     * @param force 为 true 时即使他人锁未过期也强制接管, 并在服务端记录操作日志
     */
    @Transactional
    public QuoteSheetEditLockResponse acquire(Long sheetId, Long ownerId, String ownerName, boolean force) {
        // 先锁父单据行: 对尚不存在的锁行做 FOR UPDATE 不会加锁, 只有锁住父行才能让并发首插串行。
        lockActiveSheet(sheetId);
        LocalDateTime now = LocalDateTime.now();
        QuoteSheetEditLock lock = repository.findBySheetIdForUpdate(sheetId).orElse(null);
        if (lock == null) {
            lock = new QuoteSheetEditLock();
            lock.setId(snowflakeIdGenerator.nextId());
            lock.setSheetId(sheetId);
        } else if (!lock.expiredAt(now) && !Objects.equals(lock.getOwnerId(), ownerId)) {
            if (!force) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "单据已被 " + lock.getOwnerName() + " 签出编辑");
            }
            log.info("报价单编辑锁强制接管: sheetId={} previousOwnerId={} previousOwner={} newOwnerId={} newOwner={}",
                    sheetId, lock.getOwnerId(), lock.getOwnerName(), ownerId, ownerName);
        }
        lock.setOwnerId(ownerId);
        lock.setOwnerName(ownerName);
        lock.setAcquiredAt(now);
        lock.setExpiresAt(now.plus(DEFAULT_TTL));
        try {
            return toResponse(repository.saveAndFlush(lock), ownerId);
        } catch (DataIntegrityViolationException ex) {
            // 并发首插兜底: 唯一约束 uk_quote_sheet_edit_lock_sheet 冲突后当前事务已被标记回滚,
            // 在同一事务内重查会再次失败, 因此直接抛出业务 409, 由调用方稍后重试。
            // 主路径已通过父单据行 PESSIMISTIC_WRITE(lockActiveSheet) 串行化同一单据的签出首插。
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "单据已被签出编辑，请稍后重试");
        }
    }

    /** 查询当前锁; 无锁或已过期返回 {@code locked=false}。 */
    @Transactional(readOnly = true)
    public QuoteSheetEditLockResponse find(Long sheetId, Long ownerId) {
        requireSheet(sheetId);
        LocalDateTime now = LocalDateTime.now();
        return repository.findBySheetId(sheetId)
                .filter(lock -> !lock.expiredAt(now))
                .map(lock -> toResponse(lock, ownerId))
                .orElseGet(() -> QuoteSheetEditLockResponse.unlocked(sheetId, DEFAULT_TTL_SECONDS));
    }

    /** 释放锁: 本人或无锁时幂等; 他人未过期锁返回 409。 */
    @Transactional
    public void release(Long sheetId, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        // 加锁读后再删: 避免无锁读到旧 owner、删除时误删他人刚抢占的新锁。
        repository.findBySheetIdForUpdate(sheetId).ifPresent(lock -> {
            if (!lock.expiredAt(now) && !Objects.equals(lock.getOwnerId(), ownerId)) {
                throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "单据已被 " + lock.getOwnerName() + " 签出编辑");
            }
            repository.delete(lock);
        });
    }

    /** 校验可写: 存在他人未过期锁时 409; 本人或无锁/已过期不受影响。 */
    @Transactional(readOnly = true)
    public void ensureWritable(Long sheetId, Long ownerId) {
        LocalDateTime now = LocalDateTime.now();
        repository.findBySheetId(sheetId)
                .filter(lock -> !lock.expiredAt(now))
                .filter(lock -> !Objects.equals(lock.getOwnerId(), ownerId))
                .ifPresent(lock -> {
                    throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                            "单据已被 " + lock.getOwnerName() + " 签出编辑");
                });
    }

    private QuoteSheetEditLockResponse toResponse(QuoteSheetEditLock lock, Long requesterId) {
        return new QuoteSheetEditLockResponse(
                lock.getSheetId(),
                true,
                lock.getOwnerId(),
                lock.getOwnerName(),
                lock.getAcquiredAt(),
                lock.getExpiresAt(),
                Objects.equals(lock.getOwnerId(), requesterId),
                DEFAULT_TTL_SECONDS);
    }

    private void requireSheet(Long sheetId) {
        if (sheetRepository.findByIdAndDeletedFlagFalse(sheetId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在");
        }
    }

    /** 加父单据行级排他锁, 串行化同一单据的签出首插。 */
    private void lockActiveSheet(Long sheetId) {
        if (sheetRepository.findActiveForUpdate(sheetId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在");
        }
    }
}
