package com.leo.erp.purchase.refund;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRefundMigrationTest {

    private static final String V12_MIGRATION = "/db/migration/V12__add_purchase_refund.sql";
    private static final String V13_MIGRATION = "/db/migration/V13__harden_purchase_refund_catalog_and_indexes.sql";

    @Test
    void v12ShouldCreatePurchaseRefundAggregateMatchingJpaEntities() throws IOException {
        String sql = readMigration(V12_MIGRATION);

        assertThat(sql)
                .contains("CREATE TABLE public.po_purchase_refund (")
                .contains("refund_no character varying(64) NOT NULL")
                .contains("source_purchase_order_id bigint NOT NULL")
                .contains("purchase_order_no character varying(64) NOT NULL")
                .contains("supplier_code character varying(64) NOT NULL")
                .contains("supplier_name character varying(128) NOT NULL")
                .contains("settlement_company_id bigint")
                .contains("settlement_company_name character varying(128)")
                .contains("refund_date date NOT NULL")
                .contains("total_quantity integer NOT NULL")
                .contains("total_weight numeric(18,8) NOT NULL")
                .contains("total_amount numeric(14,2) NOT NULL")
                .contains("status character varying(16) NOT NULL")
                .contains("operator_name character varying(32) NOT NULL")
                .contains("remark character varying(255)")
                .contains("version bigint NOT NULL")
                .contains("created_by bigint DEFAULT 0 NOT NULL")
                .contains("created_name character varying(64) DEFAULT 'system' NOT NULL")
                .contains("created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL")
                .contains("updated_by bigint")
                .contains("updated_name character varying(64)")
                .contains("updated_at timestamp without time zone")
                .contains("deleted_flag boolean DEFAULT false NOT NULL");

        assertThat(sql)
                .contains("CREATE TABLE public.po_purchase_refund_item (")
                .contains("refund_id bigint NOT NULL")
                .contains("source_purchase_order_item_id bigint NOT NULL")
                .contains("line_no integer NOT NULL")
                .contains("material_code character varying(64) NOT NULL")
                .contains("brand character varying(64) NOT NULL")
                .contains("category character varying(16) NOT NULL")
                .contains("material character varying(16) NOT NULL")
                .contains("spec character varying(64) NOT NULL")
                .contains("length character varying(32)")
                .contains("unit character varying(8) NOT NULL")
                .contains("warehouse_name character varying(128)")
                .contains("batch_no character varying(64)")
                .contains("quantity integer NOT NULL")
                .contains("quantity_unit character varying(8) NOT NULL")
                .contains("piece_weight_ton numeric(18,8) NOT NULL")
                .contains("pieces_per_bundle integer NOT NULL")
                .contains("weight_ton numeric(18,8) NOT NULL")
                .contains("unit_price numeric(12,2) NOT NULL")
                .contains("amount numeric(14,2) NOT NULL");
    }

    @Test
    void v12ShouldProtectRefundIntegrityAndProvideQueryIndexes() throws IOException {
        String sql = readMigration(V12_MIGRATION);

        assertThat(sql)
                .contains("CONSTRAINT chk_po_purchase_refund_status CHECK (status IN ('草稿', '已审核'))")
                .contains("CONSTRAINT chk_po_purchase_refund_totals_nonnegative CHECK")
                .contains("CONSTRAINT fk_po_purchase_refund_source_order FOREIGN KEY (source_purchase_order_id)")
                .contains("REFERENCES public.po_purchase_order(id)")
                .contains("CONSTRAINT fk_po_purchase_refund_item_head FOREIGN KEY (refund_id)")
                .contains("REFERENCES public.po_purchase_refund(id) ON DELETE CASCADE")
                .contains("CONSTRAINT fk_po_purchase_refund_item_source FOREIGN KEY (source_purchase_order_item_id)")
                .contains("REFERENCES public.po_purchase_order_item(id)")
                .containsPattern("(?s)CREATE UNIQUE INDEX uk_po_purchase_refund_source_active\\s+"
                        + "ON public\\.po_purchase_refund \\(source_purchase_order_id\\)\\s+"
                        + "WHERE deleted_flag = false;")
                .contains("CREATE INDEX idx_po_purchase_refund_supplier_date")
                .contains("ON public.po_purchase_refund (supplier_code, refund_date DESC)")
                .contains("CREATE INDEX idx_po_purchase_refund_status_date")
                .contains("CREATE INDEX idx_po_purchase_refund_item_refund_id")
                .contains("CREATE INDEX idx_po_purchase_refund_item_source_id")
                .contains("WHERE deleted_flag = false")
                .contains("SELECT 1 FROM public.sys_menu WHERE menu_code = 'purchase-refund'")
                .contains("SELECT 1 FROM public.sys_no_rule WHERE setting_code = 'RULE_PR'");
    }

    @Test
    void v13ShouldAddSearchIndexesAndRestoreCatalogWithoutResettingNumberSequence() throws IOException {
        String sql = readMigration(V13_MIGRATION);

        assertThat(sql)
                .contains("DROP INDEX public.idx_po_purchase_refund_supplier_date")
                .contains("ON public.po_purchase_refund (supplier_name, refund_date DESC)")
                .contains("CREATE INDEX idx_po_purchase_refund_settlement_date")
                .contains("ON public.po_purchase_refund (settlement_company_id, refund_date DESC)")
                .contains("CREATE INDEX idx_po_purchase_refund_refund_no_trgm")
                .contains("CREATE INDEX idx_po_purchase_refund_purchase_order_no_trgm")
                .contains("CREATE INDEX idx_po_purchase_refund_supplier_name_trgm")
                .contains("USING gin (refund_no public.gin_trgm_ops)")
                .contains("USING gin (purchase_order_no public.gin_trgm_ops)")
                .contains("USING gin (supplier_name public.gin_trgm_ops)")
                .contains("UPDATE public.sys_menu")
                .contains("icon = 'FileSyncOutlined'")
                .contains("UPDATE public.sys_no_rule")
                .contains("deleted_flag = false")
                .doesNotContain("current_period = NULL")
                .doesNotContain("next_serial_value = 1")
                .doesNotContain("current_period =")
                .doesNotContain("next_serial_value =");
    }

    @Test
    void v12ShouldSeedMenuActionsNumberRuleAndAdminOnlyPermissions() throws IOException {
        String sql = readMigration(V12_MIGRATION);
        String normalizedSql = sql.replaceAll("\\s+", " ");

        assertThat(normalizedSql)
                .contains("'purchase-refund', '采购退款单', 'purchase', '/purchase-refund', 'RollbackOutlined', 3")
                .contains("'purchase-refund', 'VIEW', '查看'")
                .contains("'purchase-refund', 'CREATE', '新增'")
                .contains("'purchase-refund', 'EDIT', '编辑'")
                .contains("'purchase-refund', 'DELETE', '删除'")
                .contains("'purchase-refund', 'AUDIT', '审核'")
                .contains("'purchase-refund', 'EXPORT', '导出'")
                .contains("'purchase-refund', 'PRINT', '打印'")
                .contains("'RULE_PR', '采购退款单编号规则', '采购退款单', 'PR{yyyy}{seq}'")
                .contains("role_code = 'ADMIN'")
                .doesNotContain("role_code = 'PURCHASER'")
                .doesNotContain("role_code = 'FINANCE'");
    }

    private String readMigration(String migration) throws IOException {
        try (var stream = getClass().getResourceAsStream(migration)) {
            assertThat(stream).as(migration).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
