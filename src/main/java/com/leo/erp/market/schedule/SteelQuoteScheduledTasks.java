package com.leo.erp.market.schedule;

import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.service.SteelQuoteSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mysteel 行情定时抓取: 每日 10:35 / 13:05 / 16:35(Asia/Shanghai), 覆盖上午/中午/下午发布节奏。
 */
@Slf4j
@Component
public class SteelQuoteScheduledTasks {

    private final MysteelProperties properties;
    private final SteelQuoteSyncService steelQuoteSyncService;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    public SteelQuoteScheduledTasks(MysteelProperties properties, SteelQuoteSyncService steelQuoteSyncService) {
        this.properties = properties;
        this.steelQuoteSyncService = steelQuoteSyncService;
    }

    @Scheduled(cron = "${leo.market.steel-quote.sync.cron:0 35 10,13,16 * * *}",
            zone = "${leo.market.steel-quote.sync.zone:Asia/Shanghai}")
    public void syncSteelQuotes() {
        if (!properties.isEnabled() || !properties.getSync().isEnabled()) {
            return;
        }
        if (!syncRunning.compareAndSet(false, true)) {
            log.warn("跳过行情同步: 上一轮仍在执行");
            return;
        }
        try {
            LocalDate today = LocalDate.now(ZoneId.of(properties.getSync().getZone()));
            SteelQuoteSyncService.SyncResult result = steelQuoteSyncService.sync(today);
            log.info("定时行情同步完成: {} {} ({} 行, 新文章={})", result.articleDate(), result.articleTime(),
                    result.rowCount(), result.created());
        } catch (Exception ex) {
            // 定时任务兜底: 记录告警, 不让异常打穿调度线程
            log.error("定时行情同步失败", ex);
        } finally {
            syncRunning.set(false);
        }
    }
}
