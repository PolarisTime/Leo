package com.leo.erp.common.json;

import com.leo.erp.common.support.PrecisionConstants;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FieldPrecisionResolver} 契约测试：金额、重量、使用率必须命中唯一精度规则，
 * 且命中顺序不能把「吨位运费金额」误判成重量、把「amountUsageRate」截成 2 位。
 */
class FieldPrecisionResolverTest {

    @Test
    void shouldResolveUsageRateBeforeAmount() {
        assertThat(FieldPrecisionResolver.resolveScale("amountUsageRate"))
                .isEqualTo(PrecisionConstants.USAGE_RATE_SCALE);
        assertThat(FieldPrecisionResolver.resolveScale("tonnageUsageRate"))
                .isEqualTo(PrecisionConstants.USAGE_RATE_SCALE);
    }

    @Test
    void shouldResolveMoneyNamesToAmountScale() {
        String[] moneyNames = {
                "totalAmount", "unitPrice", "freightAmount", "lengthPremium",
                "avgUnitCost", "unitCost", "serviceFee", "periodIncome",
                "periodExpense", "openingBalance", "closingBalance", "runningBalance"
        };
        for (String name : moneyNames) {
            assertThat(FieldPrecisionResolver.resolveScale(name))
                    .as(name)
                    .isEqualTo(PrecisionConstants.AMOUNT_SCALE);
        }
    }

    @Test
    void shouldResolveWeightAndTonnageToWeightScale() {
        String[] weightNames = {
                "totalWeight", "pieceWeightTon", "orderTonnage", "remainingTonnage",
                "unreconciledTonnage", "totalTonnage", "contractTonnage"
        };
        for (String name : weightNames) {
            assertThat(FieldPrecisionResolver.resolveScale(name))
                    .as(name)
                    .isEqualTo(PrecisionConstants.WEIGHT_SCALE);
        }
        // 吨位运费是金额（含 tonnage 但更应先命中金额规则），不能被当成重量。
        assertThat(FieldPrecisionResolver.resolveScale("tonnageFreightAmount"))
                .isEqualTo(PrecisionConstants.AMOUNT_SCALE);
    }

    @Test
    void shouldLeaveUnrelatedFieldsUntouched() {
        assertThat(FieldPrecisionResolver.resolveScale("discount")).isNull();
        assertThat(FieldPrecisionResolver.resolveScale("remark")).isNull();
        assertThat(FieldPrecisionResolver.resolveScale(null)).isNull();
    }
}
