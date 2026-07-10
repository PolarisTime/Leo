package com.leo.erp.common.concurrency;

import com.leo.erp.finance.invoiceissue.domain.entity.InvoiceIssue;
import com.leo.erp.finance.invoicereceipt.domain.entity.InvoiceReceipt;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import jakarta.persistence.Column;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoreAggregateOptimisticLockContractTest {

    private static final List<Class<?>> CORE_AGGREGATES = List.of(
            PurchaseOrder.class,
            PurchaseInbound.class,
            SalesOrder.class,
            SalesOutbound.class,
            InvoiceIssue.class,
            InvoiceReceipt.class
    );

    @Test
    void coreAggregatesShouldExposeDedicatedVersionColumn() throws Exception {
        for (Class<?> aggregate : CORE_AGGREGATES) {
            var versionField = aggregate.getDeclaredField("version");

            assertThat(versionField.getAnnotation(Version.class))
                    .as("%s.version", aggregate.getSimpleName())
                    .isNotNull();
            assertThat(versionField.getType())
                    .as("%s.version type", aggregate.getSimpleName())
                    .isEqualTo(Long.class);
            assertThat(versionField.getAnnotation(Column.class).name())
                    .as("%s.version column", aggregate.getSimpleName())
                    .isEqualTo("version");
        }
    }

    @Test
    void v10ShouldAddNonNullVersionColumnsWithoutRewritingBaseline() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V10__add_core_aggregate_versions.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql)
                    .contains("ALTER TABLE public.po_purchase_order")
                    .contains("ALTER TABLE public.po_purchase_inbound")
                    .contains("ALTER TABLE public.so_sales_order")
                    .contains("ALTER TABLE public.so_sales_outbound")
                    .contains("ALTER TABLE public.fm_invoice_issue")
                    .contains("ALTER TABLE public.fm_invoice_receipt")
                    .contains("ADD COLUMN version bigint NOT NULL DEFAULT 0");
        }
    }
}
