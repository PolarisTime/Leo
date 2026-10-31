package com.leo.erp.sales.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.export.ExportConcurrencyProperties;
import com.leo.erp.common.web.PageQueryArgumentResolver;
import com.leo.erp.sales.order.SalesOrderProperties;
import com.leo.erp.sales.order.service.SalesOrderDocumentFlowService;
import com.leo.erp.sales.order.service.SalesOrderPrintExportService;
import com.leo.erp.sales.order.service.SalesOrderService;
import com.leo.erp.sales.order.service.SalesOrderSourceCandidateService;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
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
 * 销售订单的「资源版本前置条件」HTTP 契约（质量报告 P1-2）。
 *
 * <p>锁定三态：</p>
 * <ol>
 *   <li>缺 {@code X-Resource-Version}（且未配置关闭）→ <b>428</b>，且不触达 service；</li>
 *   <li>版本与单据当前 {@code @Version} 不一致 → <b>412</b>；</li>
 *   <li>版本一致 → <b>200</b>，响应体带更新后的 {@code version}（全局 Long→字符串，输出 {@code "4"}）。</li>
 * </ol>
 *
 * <p>用生产 {@link JacksonConfig} 装配 JSON 转换器，因此 {@code version} 的字符串形态与线上一致；
 * 这也是「列表/详情/更新响应都带 version」这条跨端契约的可执行证据。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2SalesOrderVersionContractTest {

    private static final long ORDER_ID = 9223372036854775807L;

    @Mock
    private SalesOrderService service;

    @Mock
    private SalesOrderPrintExportService printExportService;

    @Mock
    private SalesOrderSourceCandidateService sourceCandidateService;

    @Mock
    private SalesOrderDocumentFlowService documentFlowService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void put_withoutVersionHeader_returns428AndNeverCallsService() throws Exception {
        mockMvc(true).perform(put("/v2.0/sales-orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));

        verify(service, never()).update(anyLong(), any(), any());
    }

    @Test
    void put_withStaleVersion_returns412() throws Exception {
        when(service.update(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "销售订单版本已变更，请刷新后重试"));

        mockMvc(true).perform(put("/v2.0/sales-orders/" + ORDER_ID)
                        .header("X-Resource-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(4120));
    }

    @Test
    void put_withMatchingVersion_returns200WithNewVersionAsString() throws Exception {
        when(service.update(eq(ORDER_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/sales-orders/" + ORDER_ID)
                        .header("X-Resource-Version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("4"))
                .andExpect(jsonPath("$.id").value(String.valueOf(ORDER_ID)));

        verify(service).update(eq(ORDER_ID), any(), eq(3L));
    }

    @Test
    void put_withIfMatchAlias_isAcceptedAsLegacyHeader() throws Exception {
        when(service.update(eq(ORDER_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/sales-orders/" + ORDER_ID)
                        .header("If-Match", "W/\"3\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("4"));
    }

    @Test
    void put_withoutVersionHeader_whenNotRequired_stillForwardsNullVersion() throws Exception {
        when(service.update(eq(ORDER_ID), any(), isNull())).thenReturn(response("草稿", 1L));

        mockMvc(false).perform(put("/v2.0/sales-orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void detail_exposesVersionSoClientCanSendItBack() throws Exception {
        when(service.detail(ORDER_ID)).thenReturn(response("草稿", 7L));

        mockMvc(true).perform(get("/v2.0/sales-orders/" + ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("7"));
    }

    private MockMvc mockMvc(boolean requireResourceVersion) {
        SalesOrderProperties properties = new SalesOrderProperties();
        properties.setRequireResourceVersion(requireResourceVersion);
        return MockMvcBuilders
                .standaloneSetup(new V2SalesOrderController(
                        service,
                        printExportService,
                        sourceCandidateService,
                        documentFlowService,
                        new ExportConcurrencyGuard(new ExportConcurrencyProperties()),
                        properties))
                .setCustomArgumentResolvers(new PageQueryArgumentResolver(() -> 20))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /** 通过校验的最小整体替换请求（明细必填字段齐备）。 */
    private String requestBody() {
        return """
                {"orderNo":"SO001","customerId":"10","customerName":"客户A","projectId":"20",
                 "projectName":"项目A","settlementCompanyId":"30","deliveryDate":"2026-08-01",
                 "salesName":"销售员A","status":"草稿",
                 "items":[{"materialCode":"M001","brand":"品牌","category":"品类","material":"材质",
                           "spec":"规格","unit":"吨","warehouseName":"仓库A","quantity":1,
                           "pieceWeightTon":0.5,"piecesPerBundle":1,"unitPrice":100.00}]}
                """;
    }

    private SalesOrderResponse response(String status, Long version) {
        return new SalesOrderResponse(
                ORDER_ID, "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", 30L, "结算主体A",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), "销售员A",
                BigDecimal.ONE, new BigDecimal("100.00"), status, false, null,
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), 0, 0, 0, null, version);
    }
}
