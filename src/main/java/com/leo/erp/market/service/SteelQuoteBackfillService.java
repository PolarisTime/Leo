package com.leo.erp.market.service;

import com.leo.erp.common.support.MdcContextSupport;
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
    private final SteelxQuoteSyncService steelxSyncService;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SteelQuoteBackfillStatusResponse status =
            new SteelQuoteBackfillStatusResponse(false, null, null, null, null, 0, 0, 0, java.util.List.of());

    public SteelQuoteBackfillService(SteelQuoteSyncService syncService,
                                     SteelxQuoteSyncService steelxSyncService) {
        this.syncService = syncService;
        this.steelxSyncService = steelxSyncService;
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
        return submit(from, to, null, null);
    }

    /** 提交补数任务; source=STEELX 时按地区逐日抓取(历史 URL)。 */
    public boolean submit(LocalDate from, LocalDate to, String source, String region) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        Instant startedAt = Instant.now();
        status = new SteelQuoteBackfillStatusResponse(true, from, to, startedAt, null, 0, 0, 0,
                java.util.List.of());
        boolean steelx = "STEELX".equalsIgnoreCase(source);
        // 捕获提交线程的 MDC(traceId/spanId), 使后台补数日志与触发请求同链路。
        executor.submit(MdcContextSupport.wrap(() -> {
            if (steelx) {
                runSteelx(from, to, startedAt, region);
            } else {
                run(from, to, startedAt);
            }
        }));
        return true;
    }

    /** 西本补数: 逐日(跳过周末)按地区抓取历史报价; 已有则幂等复用。 */
    private void runSteelx(LocalDate from, LocalDate to, Instant startedAt, String region) {
        int syncedDays = 0;
        int failedDays = 0;
        int totalRows = 0;
        java.util.List<SteelQuoteSyncService.BackfillFailure> failures = new java.util.ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            java.time.DayOfWeek dow = date.getDayOfWeek();
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                continue;
            }
            try {
                SteelxQuoteSyncService.SyncResult result = (region == null || region.isBlank())
                        ? steelxSyncService.syncAllRegions(date).get(0)
                        : steelxSyncService.syncRegion(region, date);
                syncedDays++;
                totalRows += result.rowCount();
            } catch (RuntimeException ex) {
                failedDays++;
                failures.add(new SteelQuoteSyncService.BackfillFailure(date, ex.getMessage()));
            }
        }
        status = new SteelQuoteBackfillStatusResponse(false, from, to, startedAt, Instant.now(),
                syncedDays, failedDays, totalRows, failures);
        running.set(false);
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
