package com.leo.erp.inventory.api;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 库存事务类型枚举与 DB {@code chk_inv_transaction_type} 值域一致性测试。
 */
class InventoryTransactionTypeTest {

    private static final Set<String> DB_ALLOWED_VALUES = Set.of(
            "PURCHASE_IN",
            "SALES_OUT",
            "SALES_RETURN_IN",
            "PURCHASE_RETURN_OUT",
            "TRANSFER",
            "COUNT_ADJUST"
    );

    @Test
    void enumShouldCoverAllDatabaseCheckValues() {
        for (String value : DB_ALLOWED_VALUES) {
            assertThat(InventoryTransactionType.valueOf(value).name()).isEqualTo(value);
        }
    }

    @Test
    void fixedDirectionTypesShouldExposeDirection() {
        assertThat(InventoryTransactionType.PURCHASE_IN.direction()).isEqualTo((short) 1);
        assertThat(InventoryTransactionType.SALES_OUT.direction()).isEqualTo((short) -1);
        assertThat(InventoryTransactionType.SALES_RETURN_IN.direction()).isEqualTo((short) 1);
        assertThat(InventoryTransactionType.PURCHASE_RETURN_OUT.direction()).isEqualTo((short) -1);
    }

    @Test
    void variableDirectionTypesShouldRejectImplicitDirection() {
        assertThatThrownBy(InventoryTransactionType.TRANSFER::direction)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(InventoryTransactionType.COUNT_ADJUST::direction)
                .isInstanceOf(IllegalStateException.class);
    }
}
