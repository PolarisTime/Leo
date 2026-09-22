package com.leo.erp.market.schedule;

import com.leo.erp.market.service.SteelxQuoteSyncService;
import com.leo.erp.market.steelx.SteelxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 西本新干线行情定时抓取: 每日 10:20(Asia/Shanghai)抓取所有已配置地区。
 * 西本每天仅一个价(上午发布)。
 */
@Slf4j
@Component
public class SteelxQuoteScheduledTasks {

    private final SteelxProperties properties;
    private final SteelxQuoteSyncService steelxQuoteSyncService;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);

    public SteelxQuoteScheduledTasks(SteelxProperties properties,
                                     SteelxQuoteSyncService steelxQuoteSyncService) {
        this.properties = properties;
        this.steelxQuoteSyncService = steelxQuoteSyncService;
    }

    @Scheduled(cron = "${leo.market.steelx-quote.sync.cron:0 20 10 * * *}",
            zone = "${leo.market.steelx-quote.sync.zone:Asia/Shanghai}")
    public void syncSteelxQuotes() {
        if (!properties.isEnabled() || !properties.getSync().isEnabled()) {
            return;
        }
        if (!syncRunning.compareAndSet(false, true)) {
            log.warn("跳过西本行情同步: 上一轮仍在执行");
            return;
        }
        try {
            var results = steelxQuoteSyncService.syncAllRegions();
            log.info("定时西本行情同步完成: {} 个地区, 共 {} 行", results.size(),
                    results.stream().mapToInt(SteelxQuoteSyncService.SyncResult::rowCount).sum());
        } catch (Exception ex) {
            log.error("定时西本行情同步失败", ex);
        } finally {
            syncRunning.set(false);
        }
    }
}
