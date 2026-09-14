package com.leo.erp.security.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * 权限码目录测试：常量取值、目录完整性（防新增常量漏登记）与拼接助手。
 */
class PermissionCodesTest {

    @Test
    void namedConstraints_shouldHaveExpectedResourceActionValues() {
        assertThat(PermissionCodes.SALES_ORDERS_READ).isEqualTo("sales-orders:read");
        assertThat(PermissionCodes.SALES_RETURNS_READ).isEqualTo("sales-returns:read");
        assertThat(PermissionCodes.SALES_RETURNS_CREATE).isEqualTo("sales-returns:create");
        assertThat(PermissionCodes.SALES_RETURNS_UPDATE).isEqualTo("sales-returns:update");
        assertThat(PermissionCodes.SALES_RETURNS_DELETE).isEqualTo("sales-returns:delete");
        assertThat(PermissionCodes.SALES_RETURNS_AUDIT).isEqualTo("sales-returns:audit");
        assertThat(PermissionCodes.INVENTORY_READ).isEqualTo("inventory:read");
        assertThat(PermissionCodes.INVENTORY_BACKFILL).isEqualTo("inventory:backfill");
        assertThat(PermissionCodes.MATERIALS_READ).isEqualTo("materials:read");
        assertThat(PermissionCodes.MATERIALS_UPDATE).isEqualTo("materials:update");
        assertThat(PermissionCodes.MATERIAL_IMPORTS_IMPORT).isEqualTo("material-imports:import");
        assertThat(PermissionCodes.MATERIAL_IMPORTS_PREVIEW).isEqualTo("material-imports:preview");
        assertThat(PermissionCodes.IMPORT_BATCHES_ROLLBACK).isEqualTo("import-batches:rollback");
        assertThat(PermissionCodes.CUSTOMER_STATEMENTS_READ).isEqualTo("customer-statements:read");
        assertThat(PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM).isEqualTo("customer-statements:confirm");
        assertThat(PermissionCodes.SYSTEM_ADMIN).isEqualTo("system:admin");
    }

    @Test
    void fieldLevelExamples_shouldUseThreeSegmentFormat() {
        assertThat(PermissionCodes.SALES_ORDERS_READ_AMOUNT).isEqualTo("sales-orders:read:amount");
        assertThat(PermissionCodes.SALES_ORDERS_UPDATE_UNIT_PRICE).isEqualTo("sales-orders:update:unit-price");
        assertThat(PermissionCodes.INVENTORY_READ_COST).isEqualTo("inventory:read:cost");
    }

    @Test
    void resourcesAndActions_shouldStayAlignedWithPaths() {
        assertThat(PermissionCodes.Resources.SALES_RETURNS).isEqualTo("sales-returns");
        assertThat(PermissionCodes.Resources.CUSTOMER_STATEMENTS).isEqualTo("customer-statements");
        assertThat(PermissionCodes.Resources.FREIGHT_STATEMENTS).isEqualTo("freight-statements");
        assertThat(PermissionCodes.Resources.PRINT_TEMPLATES).isEqualTo("print-templates");
        assertThat(PermissionCodes.Resources.ATTACHMENT_UPLOAD_SESSIONS).isEqualTo("attachment-upload-sessions");
        assertThat(PermissionCodes.Resources.CASH_LEDGER).isEqualTo("cash-ledger");
        assertThat(PermissionCodes.Resources.USER_ACCOUNTS).isEqualTo("user-accounts");
        assertThat(PermissionCodes.Actions.AUDIT).isEqualTo("audit");
        assertThat(PermissionCodes.Actions.BACKFILL).isEqualTo("backfill");
        assertThat(PermissionCodes.Actions.SYNC).isEqualTo("sync");
    }

    @Test
    void catalog_shouldCoverFullRolloutResources() {
        assertThat(PermissionCodes.SALES_ORDERS_CONFIRM).isEqualTo("sales-orders:confirm");
        assertThat(PermissionCodes.SALES_ORDERS_COMPLETE).isEqualTo("sales-orders:complete");
        assertThat(PermissionCodes.FREIGHT_STATEMENTS_AUDIT).isEqualTo("freight-statements:audit");
        assertThat(PermissionCodes.MATERIAL_CATEGORIES_UPDATE).isEqualTo("material-categories:update");
        assertThat(PermissionCodes.MATERIAL_EXPORTS_EXPORT).isEqualTo("material-exports:export");
        assertThat(PermissionCodes.CASH_LEDGER_READ).isEqualTo("cash-ledger:read");
        assertThat(PermissionCodes.CASH_LEDGER_EXPORT).isEqualTo("cash-ledger:export");
        assertThat(PermissionCodes.STEEL_QUOTE_SYNCS_SYNC).isEqualTo("steel-quote-syncs:sync");
        assertThat(PermissionCodes.STEEL_QUOTE_BACKFILLS_BACKFILL).isEqualTo("steel-quote-backfills:backfill");
        assertThat(PermissionCodes.PRINT_EXPORTS_PRINT).isEqualTo("print-exports:print");
        assertThat(PermissionCodes.PRINT_PREVIEWS_READ).isEqualTo("print-previews:read");
        assertThat(PermissionCodes.ATTACHMENT_UPLOAD_SESSIONS_COMPLETE)
                .isEqualTo("attachment-upload-sessions:complete");
        assertThat(PermissionCodes.ATTACHMENT_MANIFEST_EXPORTS_EXPORT)
                .isEqualTo("attachment-manifest-exports:export");
        assertThat(PermissionCodes.CODE_ISSUANCES_CREATE).isEqualTo("code-issuances:create");
        assertThat(PermissionCodes.USER_ACCOUNTS_UPDATE).isEqualTo("user-accounts:update");
    }

    @Test
    void catalog_shouldContainEveryResourceWildcardHelperResult() {
        assertThat(PermissionCodes.ofResourceWildcard("sales-returns"))
                .isEqualTo(PermissionCodes.Resources.SALES_RETURNS + ":*");
        assertThat(PermissionCodes.ofResourceWildcard("freight-statements"))
                .isEqualTo(PermissionCodes.Resources.FREIGHT_STATEMENTS + ":*");
    }

    @Test
    void of_shouldComposeResourceAndAction() {
        assertThat(PermissionCodes.of("sales-returns", "update"))
                .isEqualTo(PermissionCodes.SALES_RETURNS_UPDATE);
        assertThat(PermissionCodes.of("inventory", "read")).isEqualTo(PermissionCodes.INVENTORY_READ);
    }

    @Test
    void of_shouldComposeFieldLevelCode() {
        assertThat(PermissionCodes.of("sales-orders", "read", "amount"))
                .isEqualTo(PermissionCodes.SALES_ORDERS_READ_AMOUNT);
        assertThat(PermissionCodes.of("inventory", "read", "cost"))
                .isEqualTo(PermissionCodes.INVENTORY_READ_COST);
    }

    @Test
    void ofResourceWildcard_shouldComposeResourceWildcard() {
        assertThat(PermissionCodes.ofResourceWildcard("sales-orders")).isEqualTo("sales-orders:*");
    }

    @Test
    void of_shouldRejectBlankResourceOrAction() {
        assertThatThrownBy(() -> PermissionCodes.of("", "read"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionCodes.of("sales", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionCodes.of(null, "read"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PermissionCodes.of("sales-orders", "read", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalog_shouldContainEveryPublicConstant() throws Exception {
        for (Field field : PermissionCodes.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)
                    && field.getType() == String.class) {
                String value = (String) field.get(null);
                assertThat(PermissionCodes.all())
                        .as("常量 %s 未纳入 PermissionCodes.all()", field.getName())
                        .contains(value);
            }
        }
    }

    @Test
    void catalog_shouldUseResourceActionFieldFormat() {
        assertThat(PermissionCodes.all())
                .allMatch(code -> PermissionCodes.WILDCARD.equals(code)
                        || code.matches("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)?$"));
    }

    @Test
    void catalog_shouldCoverPilotFineGrainedActions() {
        assertThat(PermissionCodes.all()).contains(
                PermissionCodes.SALES_RETURNS_READ,
                PermissionCodes.SALES_RETURNS_CREATE,
                PermissionCodes.SALES_RETURNS_UPDATE,
                PermissionCodes.SALES_RETURNS_DELETE,
                PermissionCodes.SALES_RETURNS_AUDIT,
                PermissionCodes.INVENTORY_BACKFILL,
                PermissionCodes.MATERIAL_IMPORTS_IMPORT,
                PermissionCodes.MATERIAL_IMPORTS_PREVIEW,
                PermissionCodes.IMPORT_BATCHES_ROLLBACK,
                PermissionCodes.CUSTOMER_STATEMENTS_CONFIRM
        );
    }
}
