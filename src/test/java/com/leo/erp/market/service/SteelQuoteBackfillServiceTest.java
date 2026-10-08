package com.leo.erp.market.service;

import com.leo.erp.market.QuoteNotPublishedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteelQuoteBackfillServiceTest {

    @Mock
    private SteelQuoteSyncService syncService;

    @Mock
    private SteelxQuoteSyncService steelxSyncService;

    @InjectMocks
    private SteelQuoteBackfillService service;

    @Test
    void 同一时间仅允许一个补数任务并记录状态() throws Exception {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 10);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(syncService.backfill(any(), any())).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new SteelQuoteSyncService.BackfillResult(from, to, 2, 1, 0, 100,
                    List.of(LocalDate.of(2026, 9, 6)), List.of());
        });

        assertThat(service.submit(from, to)).isTrue();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(service.status().running()).isTrue();
        assertThat(service.submit(from, to)).isFalse();

        release.countDown();
        waitUntilIdle();
        assertThat(service.status().running()).isFalse();
        assertThat(service.status().syncedDays()).isEqualTo(2);
        assertThat(service.status().skippedDays()).isEqualTo(1);
        assertThat(service.status().skippedDates()).containsExactly(LocalDate.of(2026, 9, 6));
        assertThat(service.status().totalRows()).isEqualTo(100);
    }

    @Test
    void 西本补数把该日无报价记为跳过而不是失败() throws Exception {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 2);
        when(steelxSyncService.syncRegion(any(), any()))
                .thenThrow(new QuoteNotPublishedException("西本杭州未解析到任何价格行"));

        assertThat(service.submit(from, to, "STEELX", "杭州")).isTrue();
        waitUntilIdle();

        assertThat(service.status().syncedDays()).isZero();
        assertThat(service.status().failedDays()).isZero();
        assertThat(service.status().skippedDays()).isEqualTo(2);
        assertThat(service.status().skippedDates()).containsExactly(from, to);
        assertThat(service.status().failures()).isEmpty();
    }

    private void waitUntilIdle() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (service.status().running() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
