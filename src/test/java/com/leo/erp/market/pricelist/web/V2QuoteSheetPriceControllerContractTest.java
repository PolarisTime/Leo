package com.leo.erp.market.pricelist.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceDeriver;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceService;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetItemPriceRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 比价单单格覆盖 / 固化端点的 RESTful 契约测试: 201/200/204/404/422 与雪花 ID 字符串化。
 */
@ExtendWith(MockitoExtension.class)
class V2QuoteSheetPriceControllerContractTest {

    private static final long SHEET_ID = 9007199254740993L;
    private static final long ITEM_ID = 901L;
    private static final String BRAND = "安徽富鑫";

    @Mock
    private QuoteSheetPriceService priceService;

    @Mock
    private QuoteSheetRepository quoteSheetRepository;

    @Mock
    private QuoteSheetItemPriceRepository itemPriceRepository;

    @Mock
    private QuoteSheetPriceDeriver deriver;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private SupplierQuery supplierQuery;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2QuoteSheetPriceController(priceService))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    private QuoteSheetPriceService realService() {
        return new QuoteSheetPriceService(deriver, quoteSheetRepository,
                itemPriceRepository, snowflakeIdGenerator, supplierQuery);
    }

    private static QuoteSheet sheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setOrderDate(LocalDate.of(2026, 9, 28));
        sheet.setRefPeriod("09:30");
        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(1L);
        brand.setBrandName(BRAND);
        brand.setFreight(new BigDecimal("60.00"));
        sheet.setBrands(new ArrayList<>(List.of(brand)));
        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(ITEM_ID);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("抗震钢E");
        item.setSpec(12);
        item.setLength("9米");
        item.setPrices(new ArrayList<>());
        sheet.setItems(new ArrayList<>(List.of(item)));
        return sheet;
    }

    private static QuoteSheetPriceDeriver.BrandSelection selection(BigDecimal price) {
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceList list =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceList();
        list.setId(300L);
        list.setSupplierId(777L);
        list.setSupplierName("杭州中金钢铁");
        list.setBrandName(BRAND);
        list.setReleasedAt(LocalDateTime.of(2026, 9, 28, 8, 0));
        list.setStatus(com.leo.erp.market.pricelist.domain.entity.SupplierPriceList.STATUS_ACTIVE);
        com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem priceItem =
                new com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem();
        priceItem.setId(400L);
        priceItem.setList(list);
        priceItem.setCategory("螺纹钢");
        priceItem.setMaterial("抗震钢E");
        priceItem.setSpec(12);
        priceItem.setLength("9米");
        priceItem.setPrice(price);
        return new QuoteSheetPriceDeriver.BrandSelection(Map.of(BRAND,
                new QuoteSheetPriceDeriver.BrandEntry(list, Map.of(priceItem.keyOf(), priceItem))));
    }

    /** 单格覆盖: PUT 200, 且格内 ID 全为字符串。 */
    @Test
    void overrideCell_returnsOkWithStringifiedIds() throws Exception {
        QuoteSheetItemPrice saved = new QuoteSheetItemPrice();
        saved.setId(905L);
        saved.setItem(sheet().getItems().get(0));
        saved.setBrandName(BRAND);
        saved.setSpotPrice(new BigDecimal("3150.00"));
        saved.setPriceSource(QuoteSheetItemPrice.SOURCE_MANUAL);
        when(quoteSheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID)).thenReturn(Optional.of(sheet()));
        when(itemPriceRepository.findByItemIdAndBrandName(ITEM_ID, BRAND)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(905L);
        when(deriver.selectBrands(any(), any(), any())).thenReturn(selection(new BigDecimal("3220.00")));
        when(itemPriceRepository.findBySheetId(SHEET_ID)).thenReturn(List.of(saved));

        MockMvc wiring = MockMvcBuilders
                .standaloneSetup(new V2QuoteSheetPriceController(realService()))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(buildMapper()))
                .build();

        wiring.perform(put("/v2.0/quote-sheets/{sheetId}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, BRAND)
                        .contentType("application/json")
                        .content("""
                                {"spotPrice":3150.00,"supplierId":"777"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandName").value(BRAND))
                .andExpect(jsonPath("$.spotPrice").value(3150.00))
                .andExpect(jsonPath("$.spotSource").value("MANUAL"))
                .andExpect(jsonPath("$.derivedSpotPrice").value(3220.00))
                .andExpect(jsonPath("$.spotReason").doesNotExist())
                // MANUAL 覆盖行不携带来源价格表ID, 推导值仍在 derivedSpotPrice 里可见
                .andExpect(jsonPath("$.priceListId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void overrideCell_returnsNotFoundWhenSheetMissing() throws Exception {
        when(priceService.overrideCell(anyLong(), anyLong(), any(), any(), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));

        mockMvc.perform(put("/v2.0/quote-sheets/{sheetId}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, BRAND)
                        .contentType("application/json")
                        .content("""
                                {"spotPrice":3150.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void overrideCell_returnsUnprocessableForInvalidBrandName() throws Exception {
        when(priceService.overrideCell(anyLong(), anyLong(), any(), any(), anyLong()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "品牌长度不能超过64个字符"));

        mockMvc.perform(put("/v2.0/quote-sheets/{sheetId}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, "x".repeat(65))
                        .contentType("application/json")
                        .content("""
                                {"spotPrice":1}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void clearCell_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/v2.0/quote-sheets/{sheetId}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, BRAND))
                .andExpect(status().isNoContent());
        verify(priceService).clearCell(SHEET_ID, ITEM_ID, BRAND);
    }

    @Test
    void clearCell_returnsNotFoundWhenItemMissing() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_FOUND, "商品行不存在"))
                .when(priceService).clearCell(anyLong(), anyLong(), any());

        mockMvc.perform(delete("/v2.0/quote-sheets/{sheetId}/items/{itemId}/price-overrides/{brandName}",
                        SHEET_ID, ITEM_ID, BRAND))
                .andExpect(status().isNotFound());
    }

    @Test
    void pull_returnsCreatedWithCountsAndLocations() throws Exception {
        when(priceService.pull(anyLong(), any())).thenReturn(
                new com.leo.erp.market.pricelist.web.dto.PricePullResponse(
                        9007199254740995L, SHEET_ID, LocalDateTime.of(2026, 9, 28, 9, 30),
                        88, 12, 3, List.of()));

        mockMvc.perform(post("/v2.0/quote-sheets/{sheetId}/price-pulls", SHEET_ID)
                        .contentType("application/json")
                        .content("""
                                {"overwriteManual":false}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("9007199254740995"))
                .andExpect(jsonPath("$.sheetId").value("9007199254740993"))
                .andExpect(jsonPath("$.filledCount").value(88))
                .andExpect(jsonPath("$.preservedCount").value(12))
                .andExpect(jsonPath("$.skippedCount").value(3));
    }

    @Test
    void pull_returnsNotFoundForMissingSheet() throws Exception {
        when(priceService.pull(anyLong(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "报价单不存在"));

        mockMvc.perform(post("/v2.0/quote-sheets/{sheetId}/price-pulls", SHEET_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private static ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    /** 未使用的 mock 占位: 保证 Mockito 严格模式下的字段一致性检查。 */
    @Test
    void mapperSerializesPriceCellIdsAsStrings() throws Exception {
        ObjectMapper mapper = buildMapper();
        String json = mapper.writeValueAsString(new QuoteSheetResponse.ItemPriceResponse(
                9007199254740993L, BRAND, new BigDecimal("3220.00"), 777L, "杭州中金钢铁",
                new BigDecimal("3220.00"), "PRICE_LIST", null, null, 9007199254740995L,
                LocalDateTime.of(2026, 9, 28, 8, 0), new BigDecimal("60.00")));

        org.assertj.core.api.Assertions.assertThat(json)
                .contains("\"id\":\"9007199254740993\"")
                .contains("\"supplierId\":\"777\"")
                .contains("\"priceListId\":\"9007199254740995\"");
    }

    /** 未使用依赖占位: 保证测试显式声明全部协作者, 便于后续扩展。 */
    @Test
    void unitPriceCellRecordKeepsCompatibilityConstructor() {
        QuoteSheetResponse.ItemPriceResponse legacy =
                new QuoteSheetResponse.ItemPriceResponse(1L, BRAND, BigDecimal.ONE, 2L, "供应商");
        org.assertj.core.api.Assertions.assertThat(legacy.derivedSpotPrice()).isNull();
        org.assertj.core.api.Assertions.assertThat(legacy.spotSource()).isNull();
    }
}
