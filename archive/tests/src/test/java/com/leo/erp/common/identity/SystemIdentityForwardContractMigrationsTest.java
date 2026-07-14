package com.leo.erp.common.identity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemIdentityForwardContractMigrationsTest {

    private static final Pattern ALTER_TABLE_BLOCK = Pattern.compile(
            "ALTER TABLE\\s+public\\.(\\w+)(.*?)(?=ALTER TABLE\\s+public\\.|\\z)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern FOREIGN_KEY = Pattern.compile(
            "ADD CONSTRAINT\\s+(\\w+)\\s+FOREIGN KEY\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern VALIDATED_CONSTRAINT = Pattern.compile(
            "VALIDATE CONSTRAINT\\s+(\\w+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INDEX = Pattern.compile(
            "CREATE\\s+(?:UNIQUE\\s+)?INDEX\\s+(\\w+)\\s+ON\\s+public\\.(\\w+)\\s*"
                    + "(?:USING\\s+\\w+\\s*)?\\(([^;]*?)\\)\\s*(WHERE\\s+[^;]*)?;",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Test
    void shouldValidateEveryStableIdentityForeignKeyExactlyOnce() throws IOException {
        Set<String> addedForeignKeys = extractForeignKeys(
                read("/db/migration/V38__add_stable_identity_foreign_keys.sql")
        ).stream()
                .map(ForeignKeyDefinition::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> validatedForeignKeys = extractValidatedConstraints(
                read("/db/migration/V40__validate_stable_identity_foreign_keys.sql")
        );

        assertThat(validatedForeignKeys)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(addedForeignKeys);
    }

    @Test
    void shouldProvideAFullLeadingIndexForEveryStableIdentityForeignKey() throws IOException {
        List<ForeignKeyDefinition> foreignKeys = extractForeignKeys(
                read("/db/migration/V38__add_stable_identity_foreign_keys.sql")
        );
        List<IndexDefinition> indexes = extractIndexes(String.join("\n",
                read("/db/migration/V1__baseline.sql"),
                read("/db/migration/V12__add_purchase_refund.sql"),
                read("/db/migration/V16__add_purchase_prepayment_and_supplier_refund_receipt.sql"),
                read("/db/migration/V26__add_identity_supporting_indexes.sql"),
                read("/db/migration/V36__add_constraint_supporting_indexes.sql"),
                read("/db/migration/V43__add_missing_stable_identity_fk_indexes.sql")
        ));

        List<String> missingIndexes = foreignKeys.stream()
                .filter(foreignKey -> indexes.stream().noneMatch(index -> index.supports(foreignKey)))
                .map(foreignKey -> foreignKey.name() + " -> " + foreignKey.table()
                        + "(" + String.join(", ", foreignKey.columns()) + ")")
                .toList();

        assertThat(missingIndexes).isEmpty();
    }

    @Test
    void shouldProvideFullLeadingIndexesForLegacyIdentityForeignKeys() throws IOException {
        List<ForeignKeyDefinition> legacyForeignKeys = List.of(
                new ForeignKeyDefinition(
                        "fk_po_purchase_refund_source_order",
                        "po_purchase_refund",
                        List.of("source_purchase_order_id")
                ),
                new ForeignKeyDefinition(
                        "fk_fm_payment_source_purchase_order",
                        "fm_payment",
                        List.of("source_purchase_order_id")
                ),
                new ForeignKeyDefinition(
                        "fk_fm_supplier_refund_receipt_refund",
                        "fm_supplier_refund_receipt",
                        List.of("purchase_refund_id")
                ),
                new ForeignKeyDefinition(
                        "fk_role_conflict_conflict_role",
                        "sys_role_conflict",
                        List.of("conflict_role_id")
                )
        );
        List<IndexDefinition> indexes = extractIndexes(String.join("\n",
                read("/db/migration/V1__baseline.sql"),
                read("/db/migration/V12__add_purchase_refund.sql"),
                read("/db/migration/V16__add_purchase_prepayment_and_supplier_refund_receipt.sql"),
                read("/db/migration/V36__add_constraint_supporting_indexes.sql"),
                read("/db/migration/V47__add_legacy_identity_fk_indexes.sql")
        ));

        List<String> missingIndexes = legacyForeignKeys.stream()
                .filter(foreignKey -> indexes.stream().noneMatch(index -> index.supports(foreignKey)))
                .map(foreignKey -> foreignKey.name() + " -> " + foreignKey.table()
                        + "(" + String.join(", ", foreignKey.columns()) + ")")
                .toList();

        assertThat(missingIndexes).isEmpty();
    }

    @Test
    void shouldAddOnlyTheThreeMissingLegacyIdentityForeignKeyIndexes() throws IOException {
        String sql = read("/db/migration/V47__add_legacy_identity_fk_indexes.sql");

        assertThat(sql)
                .doesNotContain("CREATE UNIQUE INDEX")
                .doesNotContain("fm_payment");
        assertThat(extractOnlyCreateIndexStatements(sql)).containsExactly(
                new IndexDefinition(
                        "idx_po_purchase_refund_source_purchase_order_id_fk",
                        "po_purchase_refund",
                        List.of("source_purchase_order_id"),
                        true
                ),
                new IndexDefinition(
                        "idx_fm_supplier_refund_receipt_purchase_refund_id_fk",
                        "fm_supplier_refund_receipt",
                        List.of("purchase_refund_id"),
                        true
                ),
                new IndexDefinition(
                        "idx_sys_role_conflict_conflict_role_id_fk",
                        "sys_role_conflict",
                        List.of("conflict_role_id"),
                        true
                )
        );
    }

    @Test
    void shouldRejectNonIndexSqlWhenCheckingLegacyIdentityIndexMigration() throws IOException {
        String sql = read("/db/migration/V47__add_legacy_identity_fk_indexes.sql")
                + "\n-- accidental statement\nSELECT 1;\n";

        assertThatThrownBy(() -> extractOnlyCreateIndexStatements(sql))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREATE INDEX");
    }

    @Test
    void shouldAddMissingSemanticIdentityChecksAsNotValid() throws IOException {
        String sql = read("/db/migration/V44__add_missing_stable_identity_checks.sql");

        assertThat(sql)
                .contains("chk_st_customer_stmt_item_source_identity_nn")
                .contains("CHECK (source_sales_order_item_id IS NOT NULL) NOT VALID")
                .contains("chk_st_supplier_stmt_item_source_identity_nn")
                .contains("CHECK (source_inbound_item_id IS NOT NULL) NOT VALID")
                .contains("chk_fm_invoice_issue_item_source_identity_nn")
                .contains("chk_fm_invoice_receipt_item_source_identity_nn")
                .contains("CHECK (source_purchase_order_item_id IS NOT NULL) NOT VALID")
                .contains("chk_fm_ledger_adjustment_typed_party_project")
                .contains("counterparty_type IN ('客户', '供应商', '物流商')")
                .contains("counterparty_id IS NOT NULL")
                .contains("counterparty_type = '客户'")
                .contains("project_id IS NULL")
                .contains("NULLIF(BTRIM(project_name), '') IS NULL")
                .contains("NOT VALID")
                .doesNotContain("VALIDATE CONSTRAINT")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldValidateForwardChecksInASeparateMigration() throws IOException {
        String sql = read("/db/migration/V45__validate_missing_stable_identity_checks.sql");

        assertThat(sql)
                .contains("VALIDATE CONSTRAINT chk_st_customer_stmt_item_source_identity_nn")
                .contains("VALIDATE CONSTRAINT chk_st_supplier_stmt_item_source_identity_nn")
                .contains("VALIDATE CONSTRAINT chk_fm_invoice_issue_item_source_identity_nn")
                .contains("VALIDATE CONSTRAINT chk_fm_invoice_receipt_item_source_identity_nn")
                .contains("VALIDATE CONSTRAINT chk_fm_ledger_adjustment_typed_party_project")
                .doesNotContain("ADD CONSTRAINT")
                .doesNotContain("SET NOT NULL");
    }

    @Test
    void shouldEnforceOnlySemanticallyRequiredDirectSourcesAfterValidation() throws IOException {
        String sql = read("/db/migration/V46__enforce_required_direct_source_not_null.sql");

        assertThat(sql)
                .containsPattern("(?s)ALTER TABLE public\\.st_customer_statement_item.*"
                        + "ALTER COLUMN source_sales_order_item_id SET NOT NULL")
                .containsPattern("(?s)ALTER TABLE public\\.st_supplier_statement_item.*"
                        + "ALTER COLUMN source_inbound_item_id SET NOT NULL")
                .containsPattern("(?s)ALTER TABLE public\\.fm_invoice_issue_item.*"
                        + "ALTER COLUMN source_sales_order_item_id SET NOT NULL")
                .containsPattern("(?s)ALTER TABLE public\\.fm_invoice_receipt_item.*"
                        + "ALTER COLUMN source_purchase_order_item_id SET NOT NULL")
                .doesNotContain("ALTER TABLE public.fm_payment_allocation")
                .doesNotContain("ALTER TABLE public.fm_receipt")
                .doesNotContain("ALTER TABLE public.fm_payment")
                .doesNotContain("source_supplier_statement_id SET NOT NULL")
                .doesNotContain("source_freight_statement_id SET NOT NULL")
                .doesNotContain("source_customer_statement_id SET NOT NULL")
                .doesNotContain("source_purchase_order_id SET NOT NULL")
                .doesNotContain("UPDATE ")
                .doesNotContain("FOREIGN KEY");
    }

    @Test
    void shouldKeepTypedSettlementSourcesStrictWithoutMakingEitherPaymentSourceMandatory() throws IOException {
        String checks = read("/db/migration/V39__add_stable_identity_checks.sql");
        String notNull = read("/db/migration/V42__enforce_stable_identity_not_null.sql");

        assertThat(checks)
                .contains("num_nonnulls(source_supplier_statement_id, source_freight_statement_id) = 1")
                .contains("source_statement_id = COALESCE(")
                .contains("source_customer_statement_id IS NOT NULL")
                .contains("source_statement_id = source_customer_statement_id");
        assertThat(notNull)
                .containsPattern("(?s)ALTER TABLE public\\.fm_receipt_allocation.*"
                        + "ALTER COLUMN source_customer_statement_id SET NOT NULL")
                .doesNotContain("source_supplier_statement_id SET NOT NULL")
                .doesNotContain("source_freight_statement_id SET NOT NULL")
                .doesNotContain("ALTER COLUMN source_customer_statement_id SET NOT NULL,\n"
                        + "    ALTER COLUMN source_purchase_order_id SET NOT NULL");
    }

    private List<ForeignKeyDefinition> extractForeignKeys(String sql) {
        List<ForeignKeyDefinition> definitions = new ArrayList<>();
        Matcher tableMatcher = ALTER_TABLE_BLOCK.matcher(sql);
        while (tableMatcher.find()) {
            String table = normalize(tableMatcher.group(1));
            Matcher foreignKeyMatcher = FOREIGN_KEY.matcher(tableMatcher.group(2));
            while (foreignKeyMatcher.find()) {
                definitions.add(new ForeignKeyDefinition(
                        normalize(foreignKeyMatcher.group(1)),
                        table,
                        normalizeColumns(foreignKeyMatcher.group(2))
                ));
            }
        }
        return definitions;
    }

    private List<String> extractValidatedConstraints(String sql) {
        List<String> constraints = new ArrayList<>();
        Matcher matcher = VALIDATED_CONSTRAINT.matcher(sql);
        while (matcher.find()) {
            constraints.add(normalize(matcher.group(1)));
        }
        return constraints;
    }

    private List<IndexDefinition> extractIndexes(String sql) {
        List<IndexDefinition> definitions = new ArrayList<>();
        Matcher matcher = INDEX.matcher(sql);
        while (matcher.find()) {
            definitions.add(new IndexDefinition(
                    normalize(matcher.group(1)),
                    normalize(matcher.group(2)),
                    normalizeColumns(matcher.group(3)),
                    matcher.group(4) == null
            ));
        }
        return definitions;
    }

    private List<IndexDefinition> extractOnlyCreateIndexStatements(String sql) {
        List<String> statements = splitStatementsAfterRemovingComments(sql);
        if (statements.size() != 3) {
            throw new IllegalArgumentException("expected exactly three CREATE INDEX statements");
        }
        if (statements.stream().anyMatch(statement -> !statement.matches("(?is)^CREATE\\s+INDEX\\b.*"))) {
            throw new IllegalArgumentException("migration may contain only CREATE INDEX statements");
        }

        List<IndexDefinition> definitions = extractIndexes(String.join(";\n", statements) + ";");
        if (definitions.size() != statements.size()) {
            throw new IllegalArgumentException("every CREATE INDEX statement must be parseable");
        }
        return definitions;
    }

    private List<String> splitStatementsAfterRemovingComments(String sql) {
        String withoutComments = sql
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)--[^\\r\\n]*", "");
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }

    private List<String> normalizeColumns(String columns) {
        return Arrays.stream(columns.split(","))
                .map(this::normalize)
                .toList();
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String read(String resource) throws IOException {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record ForeignKeyDefinition(String name, String table, List<String> columns) {
    }

    private record IndexDefinition(String name, String table, List<String> columns, boolean full) {

        private boolean supports(ForeignKeyDefinition foreignKey) {
            return full
                    && table.equals(foreignKey.table())
                    && columns.size() >= foreignKey.columns().size()
                    && columns.subList(0, foreignKey.columns().size()).equals(foreignKey.columns());
        }
    }
}
