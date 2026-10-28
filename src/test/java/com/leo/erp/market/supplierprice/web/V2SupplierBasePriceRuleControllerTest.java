package com.leo.erp.market.supplierprice.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.market.supplierprice.SupplierPriceProperties;
import com.leo.erp.market.supplierprice.service.SupplierBasePriceRuleService;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleRequest;
import com.leo.erp.market.supplierprice.web.dto.SupplierBasePriceRuleResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 供应商品牌基价加价规则的后端接口契约冻结测试。
 *
 * <p>用生产 {@link JacksonConfig} 装配真实 JSON 转换器, 锁定: 路径与状态码、响应字段名与类型
 * (supplierId 为字符串、version 为 number)、{@code X-Resource-Version} 前置条件的 412/428 语义、
 * 雪花 ID 只接受十进制字符串、以及「复用 supplier-price-lists 权限 + 类级幂等」这两项约定。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2SupplierBasePriceRuleControllerTest {

    /** 2^53 + 1: 超过 JavaScript 安全整数的真实雪花 ID。 */
    private static final long SUPPLIER_ID = 9007199254740993L;
    private static final String PATH = "/v2.0/supplier-base-price-rules";
    private static final String VERSION_HEADER = "X-Resource-Version";

    private static final List<String> RESPONSE_FIELDS =
            List.of("supplierId", "brandName", "base", "items", "version");
    private static final List<String> PRODUCT_KEY_FIELDS = List.of("material", "spec", "length");
    private static final List<String> ITEM_FIELDS = List.of("material", "spec", "length", "delta");

    @Mock
    private SupplierBasePriceRuleService service;

    private final ObjectMapper objectMapper = objectMapper();

    @Test
    /** 响应字段名/类型冻结: supplierId 字符串、version number、base 定位键三字段、items.delta 为数字。 */
    void get_locksFieldNamesAndTypes() throws Exception {
        when(service.find(SUPPLIER_ID, "中天")).thenReturn(response(2L));

        MvcResult result = mockMvc(true).perform(get(PATH)
                        .param("supplierId", String.valueOf(SUPPLIER_ID))
                        .param("brandName", "中天"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(body)).containsExactlyInAnyOrderElementsOf(RESPONSE_FIELDS);
        assertThat(body.get("supplierId").isTextual()).isTrue();
        assertThat(body.get("supplierId").asText()).isEqualTo(String.valueOf(SUPPLIER_ID));
        assertThat(body.get("brandName").asText()).isEqualTo("中天");
        assertThat(body.get("version").isNumber()).isTrue();
        assertThat(body.get("version").asLong()).isEqualTo(2L);
        assertThat(fieldNames(body.get("base"))).containsExactlyInAnyOrderElementsOf(PRODUCT_KEY_FIELDS);
        assertThat(body.get("items").size()).isEqualTo(2);
        assertThat(fieldNames(body.get("items").get(0)))
                .containsExactlyInAnyOrderElementsOf(ITEM_FIELDS);
        assertThat(body.get("items").get(1).get("delta").isNumber()).isTrue();
        assertThat(body.get("items").get(1).get("delta").decimalValue())
                .isEqualByComparingTo("-30.50");
    }

    @Test
    /** 未配置时 base=null、items=[]、version=0, 前端不需要处理 404。 */
    void get_emptyRuleSet_returnsNullBaseZeroVersion() throws Exception {
        when(service.find(SUPPLIER_ID, "中天")).thenReturn(
                new SupplierBasePriceRuleResponse(SUPPLIER_ID, "中天", null, List.of(), 0L));

        mockMvc(true).perform(get(PATH)
                        .param("supplierId", String.valueOf(SUPPLIER_ID))
                        .param("brandName", "中天"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base").doesNotExist())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    /** 缺 brandName 是请求参数错误(400), 不是 422 也不是 500。 */
    void get_missingBrandName_returns400() throws Exception {
        mockMvc(true).perform(get(PATH).param("supplierId", String.valueOf(SUPPLIER_ID)))
                .andExpect(status().isBadRequest());
        verify(service, never()).find(any(), any());
    }

    @Test
    /** 匹配版本: 200 + 响应头回传新版本; service 收到解析后的前置条件版本。 */
    void put_withMatchingVersion_returns200AndVersionHeader() throws Exception {
        when(service.save(any(), eq(2L))).thenReturn(response(3L));

        MvcResult result = mockMvc(true).perform(put(PATH)
                        .header(VERSION_HEADER, "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string(VERSION_HEADER, "3"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("version").isNumber()).isTrue();
        assertThat(body.get("version").asLong()).isEqualTo(3L);
        assertThat(body.get("supplierId").asText()).isEqualTo(String.valueOf(SUPPLIER_ID));

        ArgumentCaptor<SupplierBasePriceRuleRequest> captor =
                ArgumentCaptor.forClass(SupplierBasePriceRuleRequest.class);
        verify(service).save(captor.capture(), eq(2L));
        assertThat(captor.getValue().supplierId()).isEqualTo(SUPPLIER_ID);
        assertThat(captor.getValue().brandName()).isEqualTo("中天");
        assertThat(captor.getValue().items()).hasSize(2);
    }

    @Test
    /** If-Match 是兼容别名: 只给别名时同样解析为前置条件版本。 */
    void put_withIfMatchAlias_returns200() throws Exception {
        when(service.save(any(), eq(2L))).thenReturn(response(3L));

        mockMvc(true).perform(put(PATH)
                        .header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string(VERSION_HEADER, "3"));
    }

    @Test
    /** 强制前置条件但缺头: 428(PRECONDITION_REQUIRED), 且不进入业务层。 */
    void put_withoutVersion_whenRequired_returns428ProblemDetail() throws Exception {
        mockMvc(true).perform(put(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));
        verify(service, never()).save(any(), any());
    }

    @Test
    /** 版本不匹配: 412(PRECONDITION_FAILED), 不再用 409 表达版本冲突。 */
    void put_withStaleVersion_returns412ProblemDetail() throws Exception {
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "规则版本已变更"));

        mockMvc(true).perform(put(PATH)
                        .header(VERSION_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(4120));
    }

    @Test
    /** 雪花 ID 只接受十进制字符串: JSON number 直接 400, 避免 JavaScript 丢低位后写到别的供应商。 */
    void put_withNumericSupplierId_returns400() throws Exception {
        mockMvc(true).perform(put(PATH)
                        .header(VERSION_HEADER, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":9007199254740993,"brandName":"中天",
                                 "items":[{"material":"HRB400E","spec":12,"length":"9米","delta":0}]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    /** 清空规则是合法请求: items=[] 返回 200, base=null、items=[]、version 仍 +1 并回写响应头。 */
    void put_withEmptyItems_clearsRuleSet() throws Exception {
        when(service.save(any(), eq(3L))).thenReturn(new SupplierBasePriceRuleResponse(
                SUPPLIER_ID, "中天", null, List.of(), 4L));

        MvcResult result = mockMvc(true).perform(put(PATH)
                        .header(VERSION_HEADER, "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"9007199254740993\",\"brandName\":\"中天\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(VERSION_HEADER, "4"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("base").isNull()).isTrue();
        assertThat(body.get("items").size()).isZero();
        assertThat(body.get("version").isNumber()).isTrue();
        assertThat(body.get("version").asLong()).isEqualTo(4L);
        verify(service).save(any(), eq(3L));
    }

    @Test
    /** 单字段校验失败(规格必须大于 0)是 422, 由 bean validation 统一返回 ProblemDetail。 */
    void put_withZeroSpec_returns422ProblemDetail() throws Exception {
        mockMvc(true).perform(put(PATH)
                        .header(VERSION_HEADER, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"supplierId":"9007199254740993","brandName":"中天",
                                 "items":[{"material":"HRB400E","spec":0,"length":"9米","delta":0}]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(4000));
        verify(service, never()).save(any(), any());
    }

    @Test
    /** 跨字段校验失败(重复键/基价不在 items)由 service 抛 VALIDATION_ERROR, 映射 422。 */
    void put_withCrossFieldViolation_returns422ProblemDetail() throws Exception {
        when(service.save(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "规则项重复: HRB400E 12 9米"));

        mockMvc(true).perform(put(PATH)
                        .header(VERSION_HEADER, "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(4000));
    }

    @Test
    /** 契约冻结: 类级幂等注解 + 复用 supplier-price-lists 的读写权限码(不新增权限资源)。 */
    void contract_reusesSupplierPricePermissionAndRequiresIdempotency() throws Exception {
        assertThat(V2SupplierBasePriceRuleController.class
                .isAnnotationPresent(IdempotencyRequired.class)).isTrue();

        Method find = V2SupplierBasePriceRuleController.class
                .getDeclaredMethod("find", Long.class, String.class);
        assertThat(find.getAnnotation(RequirePermission.class).value())
                .containsExactly(PermissionCodes.SUPPLIER_PRICE_LISTS_READ);

        Method save = V2SupplierBasePriceRuleController.class
                .getDeclaredMethod("save", String.class, String.class, SupplierBasePriceRuleRequest.class);
        assertThat(save.getAnnotation(RequirePermission.class).value())
                .containsExactly(PermissionCodes.SUPPLIER_PRICE_LISTS_UPDATE);
    }

    private MockMvc mockMvc(boolean requireResourceVersion) {
        SupplierPriceProperties properties = new SupplierPriceProperties();
        properties.setRequireResourceVersion(requireResourceVersion);
        return MockMvcBuilders
                .standaloneSetup(new V2SupplierBasePriceRuleController(service, properties))
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
                {"supplierId":"9007199254740993","brandName":"中天",
                 "base":{"material":"HRB400E","spec":12,"length":"9米"},
                 "items":[{"material":"HRB400E","spec":12,"length":"9米","delta":0.00},
                          {"material":"HRB400E","spec":14,"length":"9米","delta":-30.50}]}
                """;
    }

    private SupplierBasePriceRuleResponse response(long version) {
        return new SupplierBasePriceRuleResponse(
                SUPPLIER_ID,
                "中天",
                new SupplierBasePriceRuleResponse.ProductKey("HRB400E", 12, "9米"),
                List.of(
                        new SupplierBasePriceRuleResponse.Item(
                                "HRB400E", 12, "9米", new BigDecimal("0.00")),
                        new SupplierBasePriceRuleResponse.Item(
                                "HRB400E", 14, "9米", new BigDecimal("-30.50"))),
                version);
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
