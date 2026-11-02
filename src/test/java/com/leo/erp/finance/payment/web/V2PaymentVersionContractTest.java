package com.leo.erp.finance.payment.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.finance.payment.PaymentProperties;
import com.leo.erp.finance.payment.service.PaymentService;
import com.leo.erp.finance.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 付款单的「资源版本前置条件」HTTP 契约（质量报告 P1-2，财务单据扩展）。
 *
 * <p>与采购/销售订单同一套契约（同一个 {@code ResourceVersionPrecondition} 解析器、同一 properties 模式）：</p>
 * <ol>
 *   <li>缺 {@code X-Resource-Version}（且未配置关闭）→ <b>428</b>，且不触达 service；</li>
 *   <li>版本与单据当前 {@code @Version} 不一致 → <b>412</b>；</li>
 *   <li>版本一致 → <b>200</b>，响应体带更新后的 {@code version}（全局 Long→字符串，输出 {@code "4"}）。</li>
 * </ol>
 *
 * <p>用生产 {@link JacksonConfig} 装配转换器，因此 {@code version} 的字符串形态与线上一致。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2PaymentVersionContractTest {

    private static final long PAYMENT_ID = 9223372036854775807L;

    @Mock
    private PaymentService paymentService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void put_withoutVersionHeader_returns428AndNeverCallsService() throws Exception {
        mockMvc(true).perform(put("/v2.0/payments/" + PAYMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));

        verify(paymentService, never()).update(anyLong(), any(), any());
    }

    @Test
    void put_withStaleVersion_returns412() throws Exception {
        when(paymentService.update(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "付款单版本已变更，请刷新后重试"));

        mockMvc(true).perform(put("/v2.0/payments/" + PAYMENT_ID)
                        .header("X-Resource-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(4120));
    }

    @Test
    void put_withMatchingVersion_returns200WithNewVersionAsString() throws Exception {
        when(paymentService.update(eq(PAYMENT_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/payments/" + PAYMENT_ID)
                        .header("X-Resource-Version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("4"))
                .andExpect(jsonPath("$.id").value(String.valueOf(PAYMENT_ID)));

        verify(paymentService).update(eq(PAYMENT_ID), any(), eq(3L));
    }

    @Test
    void put_withOnlyIfMatchAlias_returns428AndNeverCallsService() throws Exception {
        mockMvc(true).perform(put("/v2.0/payments/" + PAYMENT_ID)
                        .header("If-Match", "W/\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));

        verify(paymentService, never()).update(anyLong(), any(), any());
    }

    @Test
    void put_withoutVersionHeader_whenNotRequired_stillForwardsNullVersion() throws Exception {
        when(paymentService.update(eq(PAYMENT_ID), any(), isNull())).thenReturn(response("草稿", 1L));

        mockMvc(false).perform(put("/v2.0/payments/" + PAYMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void detail_exposesVersionSoClientCanSendItBack() throws Exception {
        when(paymentService.detail(PAYMENT_ID)).thenReturn(response("草稿", 7L));

        mockMvc(true).perform(get("/v2.0/payments/" + PAYMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("7"));
    }

    private MockMvc mockMvc(boolean requireResourceVersion) {
        PaymentProperties properties = new PaymentProperties();
        properties.setRequireResourceVersion(requireResourceVersion);
        return MockMvcBuilders
                .standaloneSetup(new V2PaymentController(paymentService, properties))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /** 通过校验的最小整体替换请求。 */
    private String requestBody() {
        return """
                {"counterpartyType":"SUPPLIER_PAYMENT","counterpartyName":"供应商A",
                 "accountId":"40","paymentDate":"2026-09-01","payType":"BANK_TRANSFER",
                 "amount":100.00,"status":"草稿","operatorName":"操作员","items":[]}
                """;
    }

    private PaymentResponse response(String status, Long version) {
        return new PaymentResponse(
                PAYMENT_ID, "PAY001", "SUPPLIER_PAYMENT", 11L, "STATEMENT_SETTLEMENT", "SUP001", "供应商A",
                null, null, null, "SUP001", "供应商A", 30L, "结算主体", 40L, null, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), status, false,
                "操作员", null, List.of(), version);
    }
}
