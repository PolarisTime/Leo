package com.leo.erp.finance.receipt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.finance.receipt.ReceiptProperties;
import com.leo.erp.finance.receipt.service.ReceiptService;
import com.leo.erp.finance.receipt.web.dto.ReceiptResponse;
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
 * 收款单的「资源版本前置条件」HTTP 契约（质量报告 P1-2，财务单据扩展）。
 *
 * <p>三态与采购/销售订单、付款单完全一致：缺头 428、版本不一致 412、一致 200 + 新 version；
 * 另覆盖 {@code If-Match} 兼容别名、开关关闭时透传 null、详情响应暴露 version。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2ReceiptVersionContractTest {

    private static final long RECEIPT_ID = 9223372036854775807L;

    @Mock
    private ReceiptService receiptService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void put_withoutVersionHeader_returns428AndNeverCallsService() throws Exception {
        mockMvc(true).perform(put("/v2.0/receipts/" + RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));

        verify(receiptService, never()).update(anyLong(), any(), any());
    }

    @Test
    void put_withStaleVersion_returns412() throws Exception {
        when(receiptService.update(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "收款单版本已变更，请刷新后重试"));

        mockMvc(true).perform(put("/v2.0/receipts/" + RECEIPT_ID)
                        .header("X-Resource-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(4120));
    }

    @Test
    void put_withMatchingVersion_returns200WithNewVersionAsString() throws Exception {
        when(receiptService.update(eq(RECEIPT_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/receipts/" + RECEIPT_ID)
                        .header("X-Resource-Version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("4"))
                .andExpect(jsonPath("$.id").value(String.valueOf(RECEIPT_ID)));

        verify(receiptService).update(eq(RECEIPT_ID), any(), eq(3L));
    }

    @Test
    void put_withIfMatchAlias_isAcceptedAsLegacyHeader() throws Exception {
        when(receiptService.update(eq(RECEIPT_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/receipts/" + RECEIPT_ID)
                        .header("If-Match", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("4"));
    }

    @Test
    void put_withoutVersionHeader_whenNotRequired_stillForwardsNullVersion() throws Exception {
        when(receiptService.update(eq(RECEIPT_ID), any(), isNull())).thenReturn(response("草稿", 1L));

        mockMvc(false).perform(put("/v2.0/receipts/" + RECEIPT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void detail_exposesVersionSoClientCanSendItBack() throws Exception {
        when(receiptService.detail(RECEIPT_ID)).thenReturn(response("草稿", 7L));

        mockMvc(true).perform(get("/v2.0/receipts/" + RECEIPT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("7"));
    }

    private MockMvc mockMvc(boolean requireResourceVersion) {
        ReceiptProperties properties = new ReceiptProperties();
        properties.setRequireResourceVersion(requireResourceVersion);
        return MockMvcBuilders
                .standaloneSetup(new V2ReceiptController(receiptService, properties))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /** 通过校验的最小整体替换请求。 */
    private String requestBody() {
        return """
                {"counterpartyType":"客户","counterpartyName":"客户A",
                 "accountId":"40","receiptDate":"2026-09-01","payType":"BANK_TRANSFER",
                 "amount":100.00,"status":"草稿","operatorName":"操作员","items":[]}
                """;
    }

    private ReceiptResponse response(String status, Long version) {
        return new ReceiptResponse(
                RECEIPT_ID, "RC001", "客户", 10L, "C001", "客户A", "CUSTOMER_STATEMENT_SETTLEMENT",
                10L, "C001", "客户A", 20L, "项目A", 1L, "主体A", 40L, null, null, null,
                LocalDate.of(2026, 9, 1), "银行转账", new BigDecimal("100.00"), status, false,
                "操作员", null, List.of(), version);
    }
}
