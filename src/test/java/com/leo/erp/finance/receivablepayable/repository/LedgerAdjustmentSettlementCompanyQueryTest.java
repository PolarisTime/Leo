package com.leo.erp.finance.receivablepayable.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LedgerAdjustmentSettlementCompanyQueryTest {

    @Test
    void shouldProjectLedgerAdjustmentsIntoTheirActualSettlementCompany() {
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(
                mock(NamedParameterJdbcTemplate.class)
        );

        String sql = ReflectionTestUtils.invokeMethod(repository, "ledgerCte");

        assertThat(sql)
                .contains("adjustment.settlement_company_id")
                .contains("adjustment.settlement_company_name")
                .contains("adjustment.counterparty_id")
                .doesNotContain("CAST(NULL AS BIGINT) AS settlement_company_id")
                .doesNotContain("CAST(NULL AS VARCHAR) AS settlement_company_name")
                .contains("source.settlement_company_id IS NOT NULL")
                .contains("ledger.settlement_company_id");
    }
}
