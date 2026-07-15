package com.leo.erp.system.operationlog.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ReliableEventMetrics {

    private final JdbcTemplate jdbcTemplate;
    private final AtomicLong incompleteCount = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();

    public ReliableEventMetrics(JdbcTemplate jdbcTemplate, MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        Gauge.builder("leo.business.events.incomplete", incompleteCount, AtomicLong::get)
                .description("未完成的Spring Modulith业务事件数量")
                .register(meterRegistry);
        Gauge.builder("leo.business.events.oldest.age", oldestAgeSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .description("最早未完成业务事件的等待秒数")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${leo.observability.event-metrics-refresh-interval:30s}")
    public void refresh() {
        EventPublicationMetrics metrics = jdbcTemplate.queryForObject(
                """
                SELECT count(*) AS incomplete_count,
                       COALESCE(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - min(publication_date))), 0)::bigint
                           AS oldest_age_seconds
                FROM event_publication
                WHERE completion_date IS NULL
                """,
                (resultSet, rowNum) -> new EventPublicationMetrics(
                        resultSet.getLong("incomplete_count"),
                        resultSet.getLong("oldest_age_seconds")
                )
        );
        if (metrics == null) {
            return;
        }
        incompleteCount.set(metrics.incompleteCount());
        oldestAgeSeconds.set(metrics.oldestAgeSeconds());
    }

    private record EventPublicationMetrics(long incompleteCount, long oldestAgeSeconds) {
    }
}
