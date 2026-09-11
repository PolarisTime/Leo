package com.leo.erp.market.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 行情补数: 启动时按配置同步最近 N 天(跳过周末)。
 * 通过 leo.market.steel-quote.backfill-days 开启(默认 0 关闭), 用于新环境/缺口补数据。
 */
@Slf4j
@Component
public class SteelQuoteBackfillRunner implements ApplicationRunner {

    private final SteelQuoteSyncService syncService;
    private final int days;
    private final ZoneId zone;

    public SteelQuoteBackfillRunner(SteelQuoteSyncService syncService,
                                    @Value("${leo.market.steel-quote.backfill-days:0}") int days,
                                    @Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        this.syncService = syncService;
        this.days = days;
        this.zone = ZoneId.of(timezone);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (days <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(zone);
        log.info("行情补数开始: 最近 {} 天 (截止 {})", days, today);
        int success = 0;
        int failed = 0;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            try {
                SteelQuoteSyncService.SyncResult result = syncService.sync(date);
                success++;
                log.info("行情补数成功: {} {} ({} 行)", date, result.period(), result.rowCount());
            } catch (Exception ex) {
                failed++;
                log.warn("行情补数失败: {} - {}", date, ex.getMessage());
            }
        }
        log.info("行情补数结束: 成功 {} 天, 失败 {} 天", success, failed);
    }
}
