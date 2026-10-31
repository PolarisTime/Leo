package com.leo.erp.purchase.order.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.web.PageQueryArgumentResolver;
import com.leo.erp.purchase.order.PurchaseOrderProperties;
import com.leo.erp.purchase.order.service.PurchaseOrderForceCloseService;
import com.leo.erp.purchase.order.service.PurchaseOrderOptionService;
import com.leo.erp.purchase.order.service.PurchaseOrderPickupListService;
import com.leo.erp.purchase.order.service.PurchaseOrderService;
import com.leo.erp.purchase.order.service.PurchaseOrderWarehouseRecommendationService;
import com.leo.erp.purchase.order.web.dto.PurchaseOrderResponse;
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
import java.time.LocalDateTime;
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
 * 采购订单的「资源版本前置条件」HTTP 契约（质量报告 P1-2）。
 *
 * <p>与销售订单同一套契约（同一个 {@code ResourceVersionPrecondition} 解析器、同一 properties 模式）：</p>
 * <ol>
 *   <li>缺 {@code X-Resource-Version} → <b>428</b>，且不触达 service；</li>
 *   <li>版本与 {@code @Version} 不一致 → <b>412</b>；</li>
 *   <li>版本一致 → <b>200</b> + 更新后的订单表示（{@code version} 字符串形态）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class V2PurchaseOrderVersionContractTest {

    private static final long ORDER_ID = 9223372036854775807L;

    @Mock
    private PurchaseOrderService purchaseOrderService;

    @Mock
    private PurchaseOrderPickupListService pickupListService;

    @Mock
    private PurchaseOrderWarehouseRecommendationService warehouseRecommendationService;

    @Mock
    private PurchaseOrderOptionService purchaseOrderOptionService;

    @Mock
    private PurchaseOrderForceCloseService forceCloseService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    @Test
    void put_withoutVersionHeader_returns428AndNeverCallsService() throws Exception {
        mockMvc(true).perform(put("/v2.0/purchase-orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));

        verify(purchaseOrderService, never()).update(anyLong(), any(), any());
    }

    @Test
    void put_withStaleVersion_returns412() throws Exception {
        when(purchaseOrderService.update(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "采购订单版本已变更，请刷新后重试"));

        mockMvc(true).perform(put("/v2.0/purchase-orders/" + ORDER_ID)
                        .header("X-Resource-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(4120));
    }

    @Test
    void put_withMatchingVersion_returns200WithNewVersionAsString() throws Exception {
        when(purchaseOrderService.update(eq(ORDER_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/purchase-orders/" + ORDER_ID)
                        .header("X-Resource-Version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("4"))
                .andExpect(jsonPath("$.id").value(String.valueOf(ORDER_ID)));
    }

    @Test
    void put_withIfMatchAlias_isAcceptedAsLegacyHeader() throws Exception {
        when(purchaseOrderService.update(eq(ORDER_ID), any(), eq(3L)))
                .thenReturn(response("草稿", 4L));

        mockMvc(true).perform(put("/v2.0/purchase-orders/" + ORDER_ID)
                        .header("If-Match", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void put_withoutVersionHeader_whenNotRequired_stillForwardsNullVersion() throws Exception {
        when(purchaseOrderService.update(eq(ORDER_ID), any(), isNull())).thenReturn(response("草稿", 1L));

        mockMvc(false).perform(put("/v2.0/purchase-orders/" + ORDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void detail_exposesVersionSoClientCanSendItBack() throws Exception {
        when(purchaseOrderService.detail(ORDER_ID)).thenReturn(response("草稿", 7L));

        mockMvc(true).perform(get("/v2.0/purchase-orders/" + ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("7"));
    }

    private MockMvc mockMvc(boolean requireResourceVersion) {
        PurchaseOrderProperties properties = new PurchaseOrderProperties();
        properties.setRequireResourceVersion(requireResourceVersion);
        return MockMvcBuilders
                .standaloneSetup(new V2PurchaseOrderController(
                        purchaseOrderService,
                        pickupListService,
                        warehouseRecommendationService,
                        purchaseOrderOptionService,
                        forceCloseService,
                        properties))
                .setCustomArgumentResolvers(new PageQueryArgumentResolver(() -> 20))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    /** 通过校验的最小整体替换请求（明细必填字段齐备）。 */
    private String requestBody() {
        return """
                {"orderNo":"PO001","supplierId":"1","supplierCode":"SUP001","supplierName":"供应商A",
                 "orderDate":"2026-08-01T00:00:00","buyerName":"采购员A","settlementCompanyId":"2",
                 "items":[{"materialCode":"M001","brand":"品牌","category":"类别","material":"材质",
                           "spec":"规格","unit":"吨","batchNo":"B001","quantity":5,
                           "pieceWeightTon":0.5,"piecesPerBundle":1,"unitPrice":100.00}]}
                """;
    }

    private PurchaseOrderResponse response(String status, Long version) {
        return new PurchaseOrderResponse(
                ORDER_ID, "PO001", 1L, "SUP001", "供应商A", LocalDateTime.of(2026, 8, 1, 0, 0),
                "采购员A", 2L, "结算主体A", BigDecimal.ONE, new BigDecimal("100.00"), status, false, null,
                List.of(), List.of(), 0, null, null, null, null, version);
    }
}
