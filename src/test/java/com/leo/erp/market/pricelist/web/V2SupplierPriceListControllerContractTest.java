package com.leo.erp.market.pricelist.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.market.pricelist.service.SupplierPriceListQueryService;
import com.leo.erp.market.pricelist.service.SupplierPriceListStore;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentRequest;
import com.leo.erp.market.pricelist.web.dto.PriceAdjustmentResponse;
import com.leo.erp.market.pricelist.web.dto.SupplierPriceListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 供应商价格表 RESTful 契约测试: 状态码语义、Location 可解引用、雪花 ID 一律字符串。
 */
@ExtendWith(MockitoExtension.class)
class V2SupplierPriceListControllerContractTest {

    private static final long LIST_ID = 9223372036854775807L;
    private static final long SUPPLIER_ID = 9007199254740993L;

    @Mock
    private SupplierPriceListStore store;

    @Mock
    private SupplierPriceListQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
                new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2SupplierPriceListController(store, queryService))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new com.leo.erp.common.web.PageQueryArgumentResolver(
                        () -> 20))
                .build();
    }

    private static SupplierPriceListResponse response(Long archivedListId) {
        return new SupplierPriceListResponse(LIST_ID, SUPPLIER_ID, "杭州中金钢铁", "安徽富鑫",
                LocalDateTime.of(2026, 9, 28, 14, 35), LocalDate.of(2026, 9, 28), null,
                "ACTIVE", "钢联新安库", "备注", 1, 3L,
                LocalDateTime.of(2026, 9, 28, 14, 35), LocalDateTime.of(2026, 9, 28, 14, 35),
                archivedListId,
                List.of(new SupplierPriceListResponse.ItemResponse(11L, "螺纹钢", "抗震钢E", 12, "9米",
                        new BigDecimal("3220.00"), "NORMAL", null, 0)));
    }

    /** 雪花 ID 必须在 JSON 中为十进制字符串, 否则前端 Number 会丢低位。 */
    @Test
    void detail_shouldSerializeSnowflakeIdsAsDecimalStrings() throws Exception {
        when(store.detail(LIST_ID)).thenReturn(response(null));

        mockMvc.perform(get("/v2.0/supplier-price-lists/{id}", LIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("9223372036854775807"))
                .andExpect(jsonPath("$.supplierId").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].id").value("11"))
                .andExpect(jsonPath("$.version").value("3"));
    }

    @Test
    void detail_shouldReturnNotFoundProblemDetail() throws Exception {
        when(store.detail(anyLong()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "价格表版本不存在"));

        mockMvc.perform(get("/v2.0/supplier-price-lists/{id}", 1L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void create_shouldReturnCreatedWithDereferenceableLocation() throws Exception {
        when(store.create(any())).thenReturn(response(null));

        mockMvc.perform(post("/v2.0/supplier-price-lists")
                        .contentType("application/json")
                        .content("""
                                {"supplierId":"9007199254740993","brandName":"安徽富鑫",
                                 "releasedAt":"2026-09-28T14:35:00","items":[]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/v2.0/supplier-price-lists/" + LIST_ID))
                .andExpect(jsonPath("$.id").value("9223372036854775807"));
    }

    @Test
    void create_shouldReturnUnprocessableWhenItemInvalid() throws Exception {
        when(store.create(any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "条目[0]规格必须大于 0"));

        mockMvc.perform(post("/v2.0/supplier-price-lists")
                        .contentType("application/json")
                        .content("""
                                {"supplierId":"9007199254740993","brandName":"安徽富鑫",
                                 "releasedAt":"2026-09-28T14:35:00",
                                 "items":[{"category":"螺纹钢","material":"抗震钢E","spec":0,"length":"9米"}]}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void update_shouldReturnOkWithVersionHeader() throws Exception {
        when(store.update(eq(LIST_ID), any(), isNull())).thenReturn(response(null));

        mockMvc.perform(put("/v2.0/supplier-price-lists/{id}", LIST_ID)
                        .contentType("application/json")
                        .content("""
                                {"supplierId":"9007199254740993","brandName":"安徽富鑫",
                                 "releasedAt":"2026-09-28T14:35:00","items":[]}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Resource-Version", "3"));
    }

    /** 同键重复建表 → 409(不再自动归档旧版)。 */
    @Test
    void create_shouldReturnConflictWhenKeyAlreadyExists() throws Exception {
        when(store.create(any()))
                .thenThrow(new BusinessException(ErrorCode.CONCURRENT_MODIFICATION,
                        "该供应商与品牌已存在价格表，请直接编辑原价格表或先删除后重建"));

        mockMvc.perform(post("/v2.0/supplier-price-lists")
                        .contentType("application/json")
                        .content("""
                                {"supplierId":"9007199254740993","brandName":"安徽富鑫","items":[]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()));
    }

    /** releasedAt 已取消版本语义: 请求省略也必须创建成功(releasedAt 缺省 = 当前时刻)。 */
    @Test
    void create_shouldAcceptRequestWithoutReleasedAt() throws Exception {
        when(store.create(any())).thenReturn(response(null));

        mockMvc.perform(post("/v2.0/supplier-price-lists")
                        .contentType("application/json")
                        .content("""
                                {"supplierId":"9007199254740993","brandName":"安徽富鑫"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/v2.0/supplier-price-lists/" + LIST_ID));
    }

    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(store).delete(LIST_ID);

        mockMvc.perform(delete("/v2.0/supplier-price-lists/{id}", LIST_ID))
                .andExpect(status().isNoContent());
        verify(store).delete(LIST_ID);
    }

    @Test
    void adjust_shouldReturnCreatedWithAdjustmentPayload() throws Exception {
        when(store.adjust(eq(LIST_ID), any(PriceAdjustmentRequest.class), anyLong(), any()))
                .thenReturn(new PriceAdjustmentResponse(7L, 1, 2, List.of(
                        new PriceAdjustmentResponse.AdjustedItem(11L, new BigDecimal("3270.00")))));

        mockMvc.perform(post("/v2.0/supplier-price-lists/{id}/price-adjustments", LIST_ID)
                        .contentType("application/json")
                        .content("""
                                {"mode":"ADD","amount":50.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.adjustmentId").value("7"))
                .andExpect(jsonPath("$.affectedCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(2))
                .andExpect(jsonPath("$.items[0].price").value(3270.00));

        ArgumentCaptor<PriceAdjustmentRequest> captor = ArgumentCaptor.forClass(PriceAdjustmentRequest.class);
        verify(store).adjust(eq(LIST_ID), captor.capture(), anyLong(), any());
        assertThat(captor.getValue().mode()).isEqualTo("ADD");
    }

    @Test
    void adjustments_shouldReturnHistory() throws Exception {
        when(store.adjustments(LIST_ID)).thenReturn(List.of());

        mockMvc.perform(get("/v2.0/supplier-price-lists/{id}/price-adjustments", LIST_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void specCatalog_shouldDelegateWithFilters() throws Exception {
        when(queryService.specCatalog("螺纹钢", "抗震钢E"))
                .thenReturn(List.of(new com.leo.erp.market.pricelist.web.dto.MaterialSpecResponse(
                        "螺纹钢", "抗震钢E", 12, "9米", 0)));

        mockMvc.perform(get("/v2.0/supplier-price-lists/spec-catalog")
                        .param("category", "螺纹钢")
                        .param("material", "抗震钢E"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].spec").value(12))
                .andExpect(jsonPath("$[0].length").value("9米"));
    }

    @Test
    void matrix_shouldBeReadOnlyProjection() throws Exception {
        when(queryService.matrix(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new com.leo.erp.market.pricelist.web.dto.SupplierPriceMatrixResponse(
                        LocalDateTime.of(2026, 9, 28, 23, 59, 59),
                        List.of(new com.leo.erp.market.pricelist.web.dto.SupplierPriceMatrixResponse.MatrixColumn(
                                SUPPLIER_ID, "杭州中金钢铁", "安徽富鑫", LIST_ID,
                                LocalDateTime.of(2026, 9, 28, 14, 35), "钢联新安库")),
                        List.of()));

        mockMvc.perform(get("/v2.0/supplier-price-lists/matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0].listId").value("9223372036854775807"))
                .andExpect(jsonPath("$.columns[0].supplierId").value("9007199254740993"));
    }

    /** 已取消版本语义的字段不再对外承诺可排序: 传旧 sortBy 按契约 422。 */
    @Test
    void page_shouldRejectRemovedVersionSortFields() throws Exception {
        mockMvc.perform(get("/v2.0/supplier-price-lists").param("sortBy", "releasedAt"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    /** 排序白名单只保留现表字段(updated_at 为默认), 版本语义字段必须已移除。 */
    @Test
    void pageSortFieldCatalog_dropsVersionFields() {
        assertThat(com.leo.erp.common.api.PageSortFieldCatalog.fields("supplier-price-list"))
                .contains("id", "supplierName", "brandName", "warehouse", "createdAt", "updatedAt")
                .doesNotContain("releasedAt", "effectiveFrom", "effectiveTo", "status");
    }

    /** 集合端点必须用既有分页契约, 不返回裸数组。 */
    @Test
    void page_shouldReturnPageResponseEnvelope() throws Exception {
        when(store.page(any(), isNull(), isNull()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/v2.0/supplier-price-lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
