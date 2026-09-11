package com.leo.erp.market.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 行情补数: 启动时按配置补最近 N 天。
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
        LocalDate to = LocalDate.now(zone);
        LocalDate from = to.minusDays(days - 1L);
        log.info("行情补数开始: {} ~ {} (最近 {} 天)", from, to, days);
        SteelQuoteSyncService.BackfillResult result = syncService.backfill(from, to);
        log.info("行情补数结束: 成功 {} 天, 失败 {} 天, 共 {} 行",
                result.syncedDays(), result.failedDays(), result.totalRows());
    }
}
