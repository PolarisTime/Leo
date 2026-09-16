package com.leo.erp.market.quotation.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * 比价报价单门面: 读直连 {@link QuoteSheetStore}, 写按单据 id 在进程内串行化并对
 * 唯一键/乐观锁冲突做有限重试; 版本不匹配的 409 由存储层直接抛出, 不参与重试。
 * <p>编辑签出锁由 {@link QuoteSheetEditLockService} 在写前校验(他人未过期锁 -> 409)。</p>
 */
@Slf4j
@Service
public class QuoteSheetService {

    private static final int MAX_ATTEMPTS = 3;

    private final QuoteSheetStore store;
    private final QuoteSheetEditLockService editLockService;
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public QuoteSheetService(QuoteSheetStore store, QuoteSheetEditLockService editLockService) {
        this.store = store;
        this.editLockService = editLockService;
    }

    public QuoteSheetResponse create(QuoteSheetRequest request) {
        return store.create(request);
    }

    public QuoteSheetResponse detail(Long id) {
        return store.detail(id);
    }

    public Page<QuoteSheetResponse> page(PageQuery query, LocalDate orderDate, Long projectId, String keyword) {
        return store.page(query, orderDate, projectId, keyword);
    }

    public void delete(Long id) {
        store.delete(id);
    }

    public QuoteSheetResponse update(Long id, QuoteSheetRequest request, Long expectedVersion, Long ownerId) {
        editLockService.ensureWritable(id, ownerId);
        return withLock(id, () -> store.update(id, request, expectedVersion));
    }

    public QuoteSheetResponse.ItemResponse addItem(Long sheetId, QuoteSheetRequest.ItemRequest request,
                                                   Long expectedVersion, Long ownerId) {
        editLockService.ensureWritable(sheetId, ownerId);
        return withLock(sheetId, () -> store.addItem(sheetId, request, expectedVersion));
    }

    public QuoteSheetResponse.ItemResponse updateItem(Long sheetId, Long itemId,
                                                      QuoteSheetRequest.ItemRequest request,
                                                      Long expectedVersion, Long ownerId) {
        editLockService.ensureWritable(sheetId, ownerId);
        return withLock(sheetId, () -> store.updateItem(sheetId, itemId, request, expectedVersion));
    }

    public void deleteItem(Long sheetId, Long itemId, Long expectedVersion, Long ownerId) {
        editLockService.ensureWritable(sheetId, ownerId);
        withLock(sheetId, () -> {
            store.deleteItem(sheetId, itemId, expectedVersion);
            return null;
        });
    }

    private <T> T withLock(Long sheetId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(sheetId, key -> new ReentrantLock());
        lock.lock();
        try {
            RuntimeException lastError = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    return action.get();
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException
                         | CannotAcquireLockException ex) {
                    lastError = ex;
                    log.warn("比价报价单保存冲突, 重试 {}/{}: sheetId={}, {}",
                            attempt, MAX_ATTEMPTS, sheetId, ex.getMessage());
                }
            }
            throw lastError == null ? new IllegalStateException("比价报价单保存失败") : lastError;
        } finally {
            lock.unlock();
        }
    }
}
