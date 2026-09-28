package com.leo.erp.market.pricelist.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiFieldError;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.web.PageQueryArgumentResolver;
import com.leo.erp.market.pricelist.service.ValueAliasQuery;
import com.leo.erp.market.pricelist.service.ValueAliasStore;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.web.dto.ValueAliasResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
 * 值映射 RESTful 契约测试: 状态码语义(201/204/404/409/422)、Location 可解引用、
 * 排序白名单、雪花 ID 一律字符串、字段级错误明细。
 */
@ExtendWith(MockitoExtension.class)
class V2ValueAliasControllerContractTest {

    private static final long ALIAS_ID = 9223372036854775807L;

    @Mock
    private ValueAliasStore store;

    @Mock
    private ValueAliasQuery query;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder =
                new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        ObjectMapper mapper = builder.build();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2ValueAliasController(store, query))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .setCustomArgumentResolvers(new PageQueryArgumentResolver(() -> 20))
                .build();
    }

    private static ValueAliasResponse response() {
        return new ValueAliasResponse(ALIAS_ID, "BRAND", "富鑫", "安徽富鑫", "供应商价格表写法",
                "tester", LocalDateTime.of(2026, 9, 28, 10, 0),
                "tester", LocalDateTime.of(2026, 9, 28, 11, 0));
    }

    /** 分页查询 200, 雪花 ID 序列化为十进制字符串。 */
    @Test
    void page_shouldReturnPagedAliasesWithStringIds() throws Exception {
        when(store.page(any(), eq("BRAND"), eq("富")))
                .thenReturn(new PageImpl<>(List.of(response()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v2.0/value-aliases")
                        .param("dimension", "BRAND")
                        .param("keyword", "富")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("9223372036854775807"))
                .andExpect(jsonPath("$.content[0].dimension").value("BRAND"))
                .andExpect(jsonPath("$.content[0].sourceValue").value("富鑫"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.currentPage").value(0));
    }

    /** 排序白名单只留 id/dimension/sourceValue/targetValue/createdAt/updatedAt: 其它字段 422。 */
    @Test
    void page_shouldRejectSortFieldOutsideWhitelist() throws Exception {
        mockMvc.perform(get("/v2.0/value-aliases").param("sortBy", "remark"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        when(store.page(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/v2.0/value-aliases").param("sortBy", "updatedAt"))
                .andExpect(status().isOk());
    }

    /** 创建 201 + 可解引用 Location。 */
    @Test
    void create_shouldReturnCreatedWithDereferenceableLocation() throws Exception {
        when(store.create(any())).thenReturn(response());

        mockMvc.perform(post("/v2.0/value-aliases")
                        .contentType("application/json")
                        .content("""
                                {"dimension":"BRAND","sourceValue":"富鑫","targetValue":"安徽富鑫",
                                 "remark":"供应商价格表写法"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/v2.0/value-aliases/" + ALIAS_ID))
                .andExpect(jsonPath("$.id").value("9223372036854775807"))
                .andExpect(jsonPath("$.targetValue").value("安徽富鑫"));
    }

    /** 重复 (维度, 源值) → 409。 */
    @Test
    void create_shouldReturnConflictOnDuplicate() throws Exception {
        when(store.create(any())).thenThrow(
                new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "该维度下源值已存在映射: BRAND / 富鑫"));

        mockMvc.perform(post("/v2.0/value-aliases")
                        .contentType("application/json")
                        .content("""
                                {"dimension":"BRAND","sourceValue":"富鑫","targetValue":"安徽富鑫"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()));
    }

    /** 非法维度 → 422(不是 400)。 */
    @Test
    void create_shouldReturnUnprocessableOnInvalidDimension() throws Exception {
        when(store.create(any())).thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                "维度只能是 CATEGORY/MATERIAL/LENGTH/BRAND 之一: COLOR"));

        mockMvc.perform(post("/v2.0/value-aliases")
                        .contentType("application/json")
                        .content("""
                                {"dimension":"COLOR","sourceValue":"红","targetValue":"红色"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(
                        "维度只能是 CATEGORY/MATERIAL/LENGTH/BRAND 之一: COLOR"));
    }

    /** 空值由 Bean Validation 拦成 422。 */
    @Test
    void create_shouldReturnUnprocessableOnBlankSourceValue() throws Exception {
        mockMvc.perform(post("/v2.0/value-aliases")
                        .contentType("application/json")
                        .content("""
                                {"dimension":"CATEGORY","sourceValue":"  ","targetValue":"螺纹钢"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.errors[0].field").value("sourceValue"));
    }

    /** 自映射 → 422 且带字段级错误明细。 */
    @Test
    void create_shouldReturnUnprocessableOnSelfMapping() throws Exception {
        when(store.create(any())).thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                "源值与目标值不能相同(自映射无意义)",
                List.of(new ApiFieldError("targetValue", "SelfMapping", "源值与目标值不能相同(自映射无意义)"))));

        mockMvc.perform(post("/v2.0/value-aliases")
                        .contentType("application/json")
                        .content("""
                                {"dimension":"CATEGORY","sourceValue":"螺纹钢","targetValue":"螺纹钢"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("targetValue"))
                .andExpect(jsonPath("$.errors[0].code").value("SelfMapping"));
    }

    /** 编辑 200; 资源不存在 404。 */
    @Test
    void update_shouldReturnOkAndNotFound() throws Exception {
        when(store.update(eq(ALIAS_ID), any())).thenReturn(response());

        mockMvc.perform(put("/v2.0/value-aliases/{id}", ALIAS_ID)
                        .contentType("application/json")
                        .content("""
                                {"dimension":"BRAND","sourceValue":"富鑫","targetValue":"安徽富鑫"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("9223372036854775807"));

        when(store.update(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "值映射不存在"));
        mockMvc.perform(put("/v2.0/value-aliases/{id}", 1L)
                        .contentType("application/json")
                        .content("""
                                {"dimension":"BRAND","sourceValue":"富鑫","targetValue":"安徽富鑫"}
                                """))
                .andExpect(status().isNotFound());
    }

    /** 删除 204(软删), 无响应体。 */
    @Test
    void delete_shouldReturnNoContent() throws Exception {
        doNothing().when(store).delete(ALIAS_ID);

        mockMvc.perform(delete("/v2.0/value-aliases/{id}", ALIAS_ID))
                .andExpect(status().isNoContent());
        verify(store).delete(ALIAS_ID);
    }

    /** 删除不存在的资源 → 404。 */
    @Test
    void delete_shouldReturnNotFound() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.NOT_FOUND, "值映射不存在"))
                .when(store).delete(anyLong());

        mockMvc.perform(delete("/v2.0/value-aliases/{id}", 7L))
                .andExpect(status().isNotFound());
    }

    /** 归一化预览 200: 命中映射时 matched=true 并回显查表键。 */
    @Test
    void resolve_shouldReturnPreview() throws Exception {
        when(query.resolveByText("CATEGORY", "直条")).thenReturn(
                new ValueAliasQuery.Resolution(ValueAliasDimension.CATEGORY, "直条", "直条", "螺纹钢", true));

        mockMvc.perform(get("/v2.0/value-aliases/resolutions")
                        .param("dimension", "CATEGORY")
                        .param("value", "直条"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimension").value("CATEGORY"))
                .andExpect(jsonPath("$.lookupKey").value("直条"))
                .andExpect(jsonPath("$.targetValue").value("螺纹钢"))
                .andExpect(jsonPath("$.matched").value(true));
    }

    /** 预览: 非法维度/空值 → 422。 */
    @Test
    void resolve_shouldReturnUnprocessableOnInvalidInput() throws Exception {
        when(query.resolveByText(eq("COLOR"), any())).thenThrow(
                new BusinessException(ErrorCode.VALIDATION_ERROR, "维度只能是 CATEGORY/MATERIAL/LENGTH/BRAND 之一: COLOR"));

        mockMvc.perform(get("/v2.0/value-aliases/resolutions")
                        .param("dimension", "COLOR")
                        .param("value", "红"))
                .andExpect(status().isUnprocessableEntity());
    }
}
