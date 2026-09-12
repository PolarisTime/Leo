package com.leo.erp.market.service;

import com.leo.erp.market.web.dto.SteelQuoteBackfillStatusResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 行情补数任务: 单线程串行执行, 支持状态查询; 同一时间只允许一个补数任务。
 */
@Service
public class SteelQuoteBackfillService {

    private final SteelQuoteSyncService syncService;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SteelQuoteBackfillStatusResponse status =
            new SteelQuoteBackfillStatusResponse(false, null, null, null, null, 0, 0, 0, java.util.List.of());

    public SteelQuoteBackfillService(SteelQuoteSyncService syncService) {
        this.syncService = syncService;
        this.executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                task -> {
                    Thread thread = new Thread(task, "steel-quote-backfill");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /** 当前/最近一次补数状态。 */
    public SteelQuoteBackfillStatusResponse status() {
        return status;
    }

    /** 提交补数任务; 已有任务进行中返回 false。 */
    public boolean submit(LocalDate from, LocalDate to) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Instant startedAt = Instant.now();
        status = new SteelQuoteBackfillStatusResponse(true, from, to, startedAt, null, 0, 0, 0,
                java.util.List.of());
        executor.submit(() -> run(from, to, startedAt));
        return true;
    }

    private void run(LocalDate from, LocalDate to, Instant startedAt) {
        try {
            SteelQuoteSyncService.BackfillResult result = syncService.backfill(from, to);
            status = new SteelQuoteBackfillStatusResponse(false, from, to, startedAt, Instant.now(),
                    result.syncedDays(), result.failedDays(), result.totalRows(), result.failures());
        } catch (RuntimeException ex) {
            status = new SteelQuoteBackfillStatusResponse(false, from, to, startedAt, Instant.now(), 0, 0, 0,
                    java.util.List.of());
            throw ex;
        } finally {
            running.set(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
