package com.leo.erp.purchase.order.audit;

import org.javers.core.Javers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PurchaseOrderSnapshotAuditListener {

    private final Javers javers;
    private final JdbcTemplate jdbcTemplate;

    public PurchaseOrderSnapshotAuditListener(Javers javers, JdbcTemplate jdbcTemplate) {
        this.javers = javers;
        this.jdbcTemplate = jdbcTemplate;
    }

    @ApplicationModuleListener(id = "purchase-order-javers-v1")
    public void record(PurchaseOrderSnapshotEvent event) {
        int claimed = jdbcTemplate.update(
                """
                INSERT INTO jv_business_event (event_id, aggregate_type, aggregate_id, action_type)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """,
                event.operation().eventId(),
                event.operation().aggregateType(),
                event.operation().aggregateId(),
                event.operation().actionType()
        );
        if (claimed == 0) {
            return;
        }

        javers.commit(
                event.operation().loginName(),
                event.snapshot(),
                commitProperties(event)
        );
    }

    private Map<String, String> commitProperties(PurchaseOrderSnapshotEvent event) {
        Map<String, String> properties = new HashMap<>();
        properties.put("eventId", event.operation().eventId().toString());
        properties.put("eventType", event.operation().eventType());
        properties.put("actionType", event.operation().actionType());
        properties.put("aggregateId", String.valueOf(event.operation().aggregateId()));
        if (event.operation().traceId() != null) {
            properties.put("traceId", event.operation().traceId());
        }
        return properties;
    }
}
