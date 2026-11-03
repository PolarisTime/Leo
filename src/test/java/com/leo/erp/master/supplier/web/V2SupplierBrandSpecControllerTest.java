package com.leo.erp.master.supplier.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.master.supplier.service.SupplierBrandSpecService;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecRequest;
import com.leo.erp.master.supplier.web.dto.SupplierBrandSpecResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 供应商品牌规格配置接口的契约冻结测试。
 *
 * <p>用生产 {@link JacksonConfig} 装配真实 JSON 转换器, 锁定: 路径形态
 * {@code /api/v2.0/suppliers/{id}/brand-specs}(MockMvc 里省去 {@code /api} 的 context-path)、
 * 请求体是<b>数组本身</b>、元素字段名与类型、空数组 = 清空的语义、未配置 = 200 + 空数组(不是 404)、
 * 以及「复用 suppliers 权限 + 类级幂等 + 路径 id 雪花校验」这三项约定。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2SupplierBrandSpecControllerTest {

    /** 2^53 + 1: 超过 JavaScript 安全整数的真实雪花 ID, 出参与路径都必须是字符串。 */
    private static final long SUPPLIER_ID = 9007199254740993L;
    private static final String PATH = "/v2.0/suppliers/" + SUPPLIER_ID + "/brand-specs";

    private static final List<String> ELEMENT_FIELDS =
            List.of("brandName", "category", "material", "spec", "length");

    @Mock
    private SupplierBrandSpecService service;

    private final ObjectMapper objectMapper = objectMapper();

    @Test
    /** 响应是数组, 每个元素恰好 5 个字段; spec 是数字, 不是字符串。 */
    void get_returnsArrayWithFrozenFields() throws Exception {
        when(service.list(SUPPLIER_ID)).thenReturn(configured());

        MvcResult result = mockMvc().perform(get(PATH))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSize(2);
        assertThat(fieldNames(body.get(0))).containsExactlyInAnyOrderElementsOf(ELEMENT_FIELDS);
        assertThat(body.get(0).get("brandName").asText()).isEqualTo("永钢");
        assertThat(body.get(0).get("category").asText()).isEqualTo("HRB400E");
        assertThat(body.get(0).get("material").asText()).isEqualTo("螺纹钢");
        assertThat(body.get(0).get("spec").isNumber()).isTrue();
        assertThat(body.get(0).get("spec").asInt()).isEqualTo(12);
        assertThat(body.get(0).get("length").asText()).isEqualTo("9米");
    }

    @Test
    /** 未配置时 200 + 空数组: 前端不需要处理 404, 也不会把"没有配置"误判成"供应商不存在"。 */
    void get_whenNotConfigured_returns200WithEmptyArray() throws Exception {
        when(service.list(SUPPLIER_ID)).thenReturn(List.of());

        mockMvc().perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    /** 供应商不存在 → 404 ProblemDetail(不再是 200 + 空数组)。 */
    void get_unknownSupplier_returns404ProblemDetail() throws Exception {
        when(service.list(SUPPLIER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在"));

        mockMvc().perform(get(PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(4040));
    }

    @Test
    /** PUT 成功: 200 + 保存后的数组; 请求体数组被原样解析成元素列表(字段名以 camelCase 契约冻结)。 */
    @SuppressWarnings("unchecked")
    void put_returns200AndSavedConfiguration() throws Exception {
        when(service.replace(eq(SUPPLIER_ID), any())).thenReturn(configured());

        MvcResult result = mockMvc().perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();
        assertThat(body).hasSize(2);
        assertThat(fieldNames(body.get(1))).containsExactlyInAnyOrderElementsOf(ELEMENT_FIELDS);

        ArgumentCaptor<List<SupplierBrandSpecRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).replace(eq(SUPPLIER_ID), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        SupplierBrandSpecRequest first = captor.getValue().get(0);
        assertThat(first.brandName()).isEqualTo("永钢");
        assertThat(first.category()).isEqualTo("HRB400E");
        assertThat(first.material()).isEqualTo("螺纹钢");
        assertThat(first.spec()).isEqualTo(12);
        assertThat(first.length()).isEqualTo("9米");
    }

    @Test
    /** 空数组是合法请求(清空 = 回落默认), 不是 400 也不是 422。 */
    @SuppressWarnings("unchecked")
    void put_withEmptyArray_returns200AndEmptyArray() throws Exception {
        when(service.replace(eq(SUPPLIER_ID), any())).thenReturn(List.of());

        mockMvc().perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        ArgumentCaptor<List<SupplierBrandSpecRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).replace(eq(SUPPLIER_ID), captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    /** 品牌不在经营品牌内由 service 抛 VALIDATION_ERROR → 422 + code 4000(ProblemDetail)。 */
    void put_brandOutsideBusinessBrands_returns422ProblemDetail() throws Exception {
        when(service.replace(eq(SUPPLIER_ID), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "第1条规格配置的品牌不在该供应商的经营品牌内: 中天"));

        mockMvc().perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(4000))
                .andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.containsString("不在该供应商的经营品牌内")));
    }

    @Test
    /** 请求体必须是数组: 传对象是请求体格式错误 400, 不是 500, 也不进业务层。 */
    void put_whenBodyIsObject_returns400() throws Exception {
        mockMvc().perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000));

        verify(service, never()).replace(any(), any());
    }

    @Test
    /** 契约冻结: 路径、方法、权限码、类级幂等、路径 id 的雪花校验与 OpenAPI 摘要。 */
    void contract_freezesPathPermissionsIdempotencyAndDocs() throws Exception {
        Class<V2SupplierBrandSpecController> controller = V2SupplierBrandSpecController.class;

        assertThat(controller.getAnnotation(RequestMapping.class).value())
                .containsExactly(ApiVersion.V2_PREFIX + "/suppliers/{id}/brand-specs");
        assertThat(controller.isAnnotationPresent(IdempotencyRequired.class)).isTrue();
        assertThat(controller.isAnnotationPresent(Validated.class)).isTrue();

        Method list = controller.getDeclaredMethod("list", Long.class);
        assertThat(list.getAnnotation(GetMapping.class)).isNotNull();
        assertThat(list.getAnnotation(RequirePermission.class).value())
                .containsExactly(PermissionCodes.SUPPLIERS_READ);
        assertThat(list.getAnnotation(Operation.class).summary()).isNotEmpty();
        assertThat(list.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
        assertThat(list.getParameters()[0].getAnnotation(SnowflakeId.class)).isNotNull();

        Method replace = controller.getDeclaredMethod("replace", Long.class, List.class);
        assertThat(replace.getAnnotation(PutMapping.class)).isNotNull();
        assertThat(replace.getAnnotation(RequirePermission.class).value())
                .containsExactly(PermissionCodes.SUPPLIERS_UPDATE);
        assertThat(replace.getAnnotation(Operation.class).summary()).isNotEmpty();
        assertThat(replace.getParameters()[0].getAnnotation(PathVariable.class)).isNotNull();
        assertThat(replace.getParameters()[0].getAnnotation(SnowflakeId.class)).isNotNull();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new V2SupplierBrandSpecController(service))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    private String requestBody() {
        return """
                [{"brandName":"永钢","category":"HRB400E","material":"螺纹钢","spec":12,"length":"9米"},
                 {"brandName":"沙钢","category":"HRB400E","material":"螺纹钢","spec":14,"length":"9米"}]
                """;
    }

    private List<SupplierBrandSpecResponse> configured() {
        return List.of(
                new SupplierBrandSpecResponse("永钢", "HRB400E", "螺纹钢", 12, "9米"),
                new SupplierBrandSpecResponse("沙钢", "HRB400E", "螺纹钢", 14, "9米"));
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
