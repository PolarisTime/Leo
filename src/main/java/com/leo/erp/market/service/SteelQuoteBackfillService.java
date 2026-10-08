package com.leo.erp.market.service;

import com.leo.erp.common.support.MdcContextSupport;
import com.leo.erp.market.QuoteNotPublishedException;
import com.leo.erp.market.web.dto.SteelQuoteBackfillStatusResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
public class SteelQuoteBackfillService {

    private final SteelQuoteSyncService syncService;
    private final SteelxQuoteSyncService steelxSyncService;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile SteelQuoteBackfillStatusResponse status =
            new SteelQuoteBackfillStatusResponse(false, null, null, null, null,
                    0, 0, 0, 0, java.util.List.of(), java.util.List.of());

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
        status = new SteelQuoteBackfillStatusResponse(true, from, to, startedAt, null,
                0, 0, 0, 0, java.util.List.of(), java.util.List.of());
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

    /**
     * 西本补数: 逐日按地区抓取历史报价; 已有则幂等复用。
     *
     * <p>不再按周末盲跳（周六站点确实有报价），由站点是否发布决定结果：
     * 价格表为空（休市/节假日）记为跳过，而不是失败。</p>
     */
    private void runSteelx(LocalDate from, LocalDate to, Instant startedAt, String region) {
        int syncedDays = 0;
        int skippedDays = 0;
        int failedDays = 0;
        int totalRows = 0;
        java.util.List<LocalDate> skippedDates = new java.util.ArrayList<>();
        java.util.List<SteelQuoteSyncService.BackfillFailure> failures = new java.util.ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            try {
                if (region == null || region.isBlank()) {
                    // 全量地区: 逐地区容错, 入库总行数按所有成功地区累加(不再只看第一个地区)。
                    java.util.List<SteelxQuoteSyncService.SyncResult> results =
                            steelxSyncService.syncAllRegions(date);
                    syncedDays++;
                    totalRows += results.stream()
                            .mapToInt(SteelxQuoteSyncService.SyncResult::rowCount).sum();
                } else {
                    SteelxQuoteSyncService.SyncResult result = steelxSyncService.syncRegion(region, date);
                    syncedDays++;
                    totalRows += result.rowCount();
                }
            } catch (QuoteNotPublishedException ex) {
                skippedDays++;
                skippedDates.add(date);
                log.info("西本补数跳过(该日无报价): {} - {}", date, ex.getMessage());
            } catch (RuntimeException ex) {
                failedDays++;
                failures.add(new SteelQuoteSyncService.BackfillFailure(date, ex.getMessage()));
            }
        }
        status = new SteelQuoteBackfillStatusResponse(false, from, to, startedAt, Instant.now(),
                syncedDays, skippedDays, failedDays, totalRows, skippedDates, failures);
        running.set(false);
    }

    private void run(LocalDate from, LocalDate to, Instant startedAt) {
        try {
            SteelQuoteSyncService.BackfillResult result = syncService.backfill(from, to);
            status = new SteelQuoteBackfillStatusResponse(false, from, to, startedAt, Instant.now(),
                    result.syncedDays(), result.skippedDays(), result.failedDays(), result.totalRows(),
                    result.skippedDates(), result.failures());
        } catch (RuntimeException ex) {
            status = new SteelQuoteBackfillStatusResponse(false, from, to, startedAt, Instant.now(),
                    0, 0, 0, 0, java.util.List.of(), java.util.List.of());
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
