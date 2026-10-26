package com.leo.erp.master.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.sales.outbound.web.dto.SalesOutboundResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 四条业务响应的 JSON 契约：都带可空 {@code projectNameAbbr}。
 *
 * <p>前端把「项目名称」列渲染成「简称（全称）」，字段名一旦漂移就会静默退化成只显示全称，
 * 因此四个响应各自锁定一次字段名与可空性。</p>
 */
class ProjectNameAbbrSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        mapper = builder.build();
    }

    @Test
    void salesOrderResponse_shouldSerializeProjectNameAbbr() throws Exception {
        SalesOrderResponse filled = SalesOrderResponse.withProjectNameAbbr(salesOrder(), "项A");

        assertThat(mapper.writeValueAsString(filled)).contains("\"projectNameAbbr\":\"项A\"");
        assertThat(mapper.writeValueAsString(salesOrder())).contains("\"projectNameAbbr\":null");
    }

    @Test
    void salesOutboundResponse_shouldSerializeProjectNameAbbr() throws Exception {
        SalesOutboundResponse filled = SalesOutboundResponse.withProjectNameAbbr(salesOutbound(), "项A");

        assertThat(mapper.writeValueAsString(filled)).contains("\"projectNameAbbr\":\"项A\"");
        assertThat(mapper.writeValueAsString(salesOutbound())).contains("\"projectNameAbbr\":null");
    }

    @Test
    void salesReturnResponse_shouldSerializeProjectNameAbbr() throws Exception {
        SalesReturnResponse filled = SalesReturnResponse.withProjectNameAbbr(salesReturn(), "项A");

        assertThat(mapper.writeValueAsString(filled)).contains("\"projectNameAbbr\":\"项A\"");
        assertThat(mapper.writeValueAsString(salesReturn())).contains("\"projectNameAbbr\":null");
    }

    @Test
    void customerStatementResponse_shouldSerializeProjectNameAbbr() throws Exception {
        CustomerStatementResponse filled =
                CustomerStatementResponse.withProjectNameAbbr(customerStatement(), "项A");

        assertThat(mapper.writeValueAsString(filled)).contains("\"projectNameAbbr\":\"项A\"");
        assertThat(mapper.writeValueAsString(customerStatement())).contains("\"projectNameAbbr\":null");
    }

    private SalesOrderResponse salesOrder() {
        return new SalesOrderResponse(
                5L, "SO001", null, "客户A", "项目A",
                LocalDate.of(2026, 9, 1), "销售员A", new BigDecimal("1.000"), new BigDecimal("100.00"),
                "草稿", null, List.of());
    }

    private SalesOutboundResponse salesOutbound() {
        return new SalesOutboundResponse(
                5L, "OB001", "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A", null, null,
                LocalDate.of(2026, 9, 1), new BigDecimal("1.000"), new BigDecimal("100.00"),
                "草稿", false, null, List.of(), null);
    }

    private SalesReturnResponse salesReturn() {
        return new SalesReturnResponse(
                5L, "SR001", "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A", null, null,
                LocalDate.of(2026, 9, 1), new BigDecimal("1.000"), new BigDecimal("100.00"),
                "草稿", false, null, List.of(), null);
    }

    private CustomerStatementResponse customerStatement() {
        return new CustomerStatementResponse(
                5L, "CS001", "客户A", "项目A", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), "待确认", null, List.of());
    }
}
