package com.leo.erp.finance.receivablepayable;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.finance.receivablepayable.repository.ReceivablePayableQueryRepository;
import com.leo.erp.finance.receivablepayable.service.ReceivablePayableService;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableDetailResponse;
import com.leo.erp.finance.receivablepayable.web.dto.ReceivablePayableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ReceivablePayableStableIdentityContractTest {

    @Test
    void shouldBuildLedgerOnlyFromTypedPartyAndSourceIds() {
        RecordingJdbcTemplate jdbc = new RecordingJdbcTemplate();
        ReceivablePayableQueryRepository repository = new ReceivablePayableQueryRepository(jdbc);

        repository.page(PageQuery.of(0, 20, "id", "desc"), null, null, null, null, null, null);

        assertThat(jdbc.sql)
                .contains("counterparty_id")
                .contains("statement_item.source_sales_order_item_id")
                .contains("statement_item.source_inbound_item_id")
                .contains("statement_item.source_freight_bill_id")
                .contains("allocation.source_customer_statement_id")
                .contains("allocation.source_supplier_statement_id")
                .contains("allocation.source_freight_statement_id")
                .contains("ledger.counterparty_id")
                .contains("ledger.settlement_company_id")
                .doesNotContain(
                        "customer_by_name",
                        "supplier_by_name",
                        "carrier_by_name",
                        "MD5(",
                        "statement_item.source_no AS order_no",
                        "statement_item.source_no AS inbound_no",
                        "statement_item.source_no AS bill_no"
                );
    }

    @Test
    void shouldRejectLegacyNameAndCodeSummaryKeys() {
        ReceivablePayableService service = new ReceivablePayableService(
                mock(ReceivablePayableQueryRepository.class),
                mock(ExcelExportService.class)
        );

        assertThatThrownBy(() -> service.detail("应收:客户:未对账:none:CUS001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
        assertThatThrownBy(() -> service.detail(
                "应收:客户:未对账:none:name:abcdefabcdefabcdefabcdef12345678"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收应付汇总ID不合法");
    }

    @Test
    void shouldExposeCounterpartyIdInSummaryAndDetailContracts() {
        assertThat(ReceivablePayableResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("counterpartyId");
        assertThat(ReceivablePayableDetailResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .contains("counterpartyId");
    }

    private static final class RecordingJdbcTemplate extends NamedParameterJdbcTemplate {

        private String sql;

        private RecordingJdbcTemplate() {
            super(noopDataSource());
        }

        @Override
        public <T> T queryForObject(String sql,
                                    org.springframework.jdbc.core.namedparam.SqlParameterSource params,
                                    Class<T> requiredType) {
            this.sql = sql;
            return requiredType.cast(0L);
        }

        private static DataSource noopDataSource() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[]{DataSource.class},
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
