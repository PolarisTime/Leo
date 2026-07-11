package com.leo.erp.finance.receivablepayable.repository;

import com.leo.erp.common.api.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
class ReceivablePayableFreightCarrierIdentityPostgresTest {

    private static final long BASE_ID = 8_790_000_000_000_000_000L;
    private static final String CARRIER_NAME = "同名物流商稳定身份测试";
    private static final String CARRIER_CODE_A = "TEST-FREIGHT-CARRIER-A";
    private static final String CARRIER_CODE_B = "TEST-FREIGHT-CARRIER-B";
    private static final long SETTLEMENT_COMPANY_ID = BASE_ID + 100;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ReceivablePayableQueryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ReceivablePayableQueryRepository(new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    @Test
    void shouldUseFreightBillCarrierCodeWhenActiveCarriersShareTheSameName() {
        insertCarrier(BASE_ID, CARRIER_CODE_A);
        insertCarrier(BASE_ID + 1, CARRIER_CODE_B);
        insertFreightBill(BASE_ID + 2, CARRIER_CODE_B);

        var result = repository.page(
                PageQuery.of(0, 20, "counterpartyCode", "asc"),
                "应付",
                "物流商",
                SETTLEMENT_COMPANY_ID,
                "未对账",
                null,
                CARRIER_NAME
        );

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.counterpartyCode()).isEqualTo(CARRIER_CODE_B);
            assertThat(row.id()).isEqualTo(
                    "应付:物流商:未对账:" + SETTLEMENT_COMPANY_ID + ":" + CARRIER_CODE_B
            );
            assertThat(row.recognizedAmount()).isEqualByComparingTo("120.00");
        });
    }

    private void insertCarrier(long id, String carrierCode) {
        jdbcTemplate.update("""
                INSERT INTO md_carrier (
                    id, carrier_code, carrier_name, status, deleted_flag, created_by
                ) VALUES (?, ?, ?, '正常', FALSE, 0)
                """, id, carrierCode, CARRIER_NAME);
    }

    private void insertFreightBill(long id, String carrierCode) {
        jdbcTemplate.update("""
                INSERT INTO lg_freight_bill (
                    id, bill_no, carrier_code, carrier_name, customer_name, project_name,
                    bill_time, unit_price, total_weight, total_freight, status,
                    settlement_company_id, settlement_company_name, deleted_flag, created_by
                ) VALUES (?, ?, ?, ?, '稳定身份测试客户', '稳定身份测试项目', ?, 120, 1, ?, '已审核',
                          ?, '稳定身份测试结算主体', FALSE, 0)
                """, id, "TEST-FREIGHT-BILL-" + id, carrierCode, CARRIER_NAME,
                LocalDateTime.of(2026, 7, 11, 10, 0), new BigDecimal("120.00"), SETTLEMENT_COMPANY_ID);
    }
}
