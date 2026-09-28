package com.leo.erp.market.pricelist.web;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.market.quotation.QuotationProperties;
import com.leo.erp.market.quotation.service.QuoteSheetService;
import com.leo.erp.market.quotation.web.V2QuoteSheetController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 现货价写入口已彻底删除(契约修订: 现货价只由供应商价格表推导)。
 *
 * <p>断言 {@code /quote-sheets/{id}} 资源族里不再存在
 * {@code PUT}/{@code DELETE .../price-overrides/{brandName}} 与 {@code POST .../price-pulls},
 * 旧前端调用一律 404。同时用 {@code GET /quote-sheets/{id}}(已映射)证明 MockMvc 里确实注册了
 * 该资源族的控制器, 因此 404 是"路由不存在"而不是"没有控制器"。</p>
 */
@ExtendWith(MockitoExtension.class)
class QuoteSheetPriceEndpointsRemovedTest {

    private static final long SHEET_ID = 9007199254740993L;
    private static final long ITEM_ID = 500L;
    private static final String BRAND = "安徽富鑫";

    @Mock
    private QuoteSheetService quoteSheetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2QuoteSheetController(quoteSheetService, new QuotationProperties()))
                // 真实应用里未映射路径由资源处理器兜底成 404(NoResourceFoundException);
                // standalone 默认会把"无处理器"抛成异常, 这里按真实 DispatcherServlet 语义关掉。
                .addDispatcherServletCustomizer(servlet -> servlet.setThrowExceptionIfNoHandlerFound(false))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();
    }

    /** 旧的单格手填覆盖写入端点已删除。 */
    @Test
    void putPriceOverride_shouldReturnNotFound() throws Exception {
        mockMvc.perform(put("/v2.0/quote-sheets/{id}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, BRAND)
                        .contentType("application/json")
                        .content("{\"spotPrice\":3150.00}"))
                .andExpect(status().isNotFound());
    }

    /** 旧的单格覆盖清除端点已删除。 */
    @Test
    void deletePriceOverride_shouldReturnNotFound() throws Exception {
        mockMvc.perform(delete("/v2.0/quote-sheets/{id}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, BRAND))
                .andExpect(status().isNotFound());
    }

    /** 价格固化(写 mk_quote_item_price)端点已删除: 该表不再被读/写。 */
    @Test
    void postPricePulls_shouldReturnNotFound() throws Exception {
        mockMvc.perform(post("/v2.0/quote-sheets/{id}/price-pulls", SHEET_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    /** 对照: 同一控制器族的读取端点仍被映射(证明上面的 404 来自路由缺失)。 */
    @Test
    void getQuoteSheet_stillMapped() throws Exception {
        when(quoteSheetService.detail(SHEET_ID)).thenReturn(null);

        mockMvc.perform(get("/v2.0/quote-sheets/{id}", SHEET_ID))
                .andExpect(status().isOk());
    }
}
