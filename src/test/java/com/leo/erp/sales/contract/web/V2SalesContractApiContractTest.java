package com.leo.erp.sales.contract.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.web.PageQueryArgumentResolver;
import com.leo.erp.sales.contract.SalesContractProperties;
import com.leo.erp.sales.contract.service.SalesContractService;
import com.leo.erp.sales.contract.service.SalesOrderContractCheckService;
import com.leo.erp.sales.contract.web.dto.SalesContractCheckResponse;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 销售合同后端接口级契约冻结测试。
 *
 * <p>使用生产 {@link JacksonConfig} 装配真实 JSON 转换器, 锁定:
 * 路径与状态码、响应字段名与类型、雪花 ID 与 version 字符串、金额两位定标、
 * 以及非法流转/资源版本前置条件的 RFC 9457 错误码。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2SalesContractApiContractTest {

    private static final long CONTRACT_ID = 9223372036854775807L;
    private static final long CUSTOMER_ID = 9007199254740993L;
    private static final long PROJECT_ID = 9007199254740995L;

    private static final List<String> CONTRACT_FIELDS = List.of(
            "id", "contractNo", "name", "customerId", "customerName", "projectId", "projectName",
            "signDate", "startDate", "endDate", "totalAmount", "totalTonnage", "status", "remark",
            "createdAt", "updatedAt", "version");

    private static final List<String> CHECK_FIELDS = List.of(
            "hasContract", "contractAmount", "usedAmount", "remainingAmount", "contractTonnage",
            "usedTonnage", "remainingTonnage", "exceededAmount", "exceededTonnage", "message");

    @Mock
    private SalesContractService service;

    @Mock
    private SalesOrderContractCheckService checkService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        objectMapper = builder.build();
    }

    private MockMvc mockMvc(boolean requireResourceVersion) {
        SalesContractProperties properties = new SalesContractProperties();
        properties.setRequireResourceVersion(requireResourceVersion);
        return MockMvcBuilders
                .standaloneSetup(
                        new V2SalesContractController(service, properties),
                        new V2SalesOrderContractCheckController(checkService))
                .setCustomArgumentResolvers(new PageQueryArgumentResolver(() -> 20))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void post_returns201WithLocationAndStringIdsAndScaledAmount() throws Exception {
        when(service.create(any())).thenReturn(response("草稿", new BigDecimal("123.456"), 0L));

        mockMvc(false).perform(post("/v2.0/sales-contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"年度合同","customerId":"1","projectId":"2",
                                 "signDate":"2026-09-17","totalAmount":1000000.00,"totalTonnage":3000.5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.endsWith("/v2.0/sales-contracts/" + CONTRACT_ID)))
                .andExpect(jsonPath("$.id").value(String.valueOf(CONTRACT_ID)))
                .andExpect(jsonPath("$.version").value("0"))
                .andExpect(jsonPath("$.totalAmount").value(123.46));
    }

    @Test
    void getDetail_locksResponseFieldNamesAndTypes() throws Exception {
        when(service.detail(CONTRACT_ID)).thenReturn(response("审核", new BigDecimal("100.00"), 3L));

        MvcResult result = mockMvc(false)
                .perform(get("/v2.0/sales-contracts/" + CONTRACT_ID))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(body)).containsExactlyInAnyOrderElementsOf(CONTRACT_FIELDS);
        assertThat(body.get("id").isTextual()).isTrue();
        assertThat(body.get("customerId").isTextual()).isTrue();
        assertThat(body.get("projectId").isTextual()).isTrue();
        assertThat(body.get("version").isTextual()).isTrue();
        assertThat(body.get("totalAmount").isNumber()).isTrue();
        assertThat(body.get("id").asText()).isEqualTo(String.valueOf(CONTRACT_ID));
        assertThat(body.get("customerId").asText()).isEqualTo(String.valueOf(CUSTOMER_ID));
        assertThat(body.get("projectId").asText()).isEqualTo(String.valueOf(PROJECT_ID));
        assertThat(body.get("version").asText()).isEqualTo("3");
        assertThat(body.get("totalAmount").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(body.get("status").asText()).isEqualTo("审核");
    }

    @Test
    void getList_locksPageEnvelopeAndItemIdString() throws Exception {
        when(service.page(any(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(new PageImpl<>(List.of(response("草稿", new BigDecimal("1.00"), 0L)),
                        PageRequest.of(0, 20), 1L));

        MvcResult result = mockMvc(false)
                .perform(get("/v2.0/sales-contracts"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(body)).containsExactlyInAnyOrderElementsOf(
                List.of("content", "totalElements", "totalPages", "currentPage", "pageSize", "hasMore"));
        assertThat(body.get("content").get(0).get("id").isTextual()).isTrue();
        assertThat(body.get("content").get(0).get("id").asText()).isEqualTo(String.valueOf(CONTRACT_ID));
    }

    @Test
    void put_withMatchingVersion_returns200AndVersionHeader() throws Exception {
        when(service.update(eq(CONTRACT_ID), any(), eq(3L)))
                .thenReturn(response("草稿", new BigDecimal("100.00"), 4L));

        mockMvc(true).perform(put("/v2.0/sales-contracts/" + CONTRACT_ID)
                        .header("X-Resource-Version", "3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Resource-Version", "4"))
                .andExpect(jsonPath("$.id").value(String.valueOf(CONTRACT_ID)));
    }

    @Test
    void put_withoutVersion_whenOptional_forwardsNullVersion() throws Exception {
        when(service.update(eq(CONTRACT_ID), any(), isNull()))
                .thenReturn(response("草稿", new BigDecimal("100.00"), 1L));

        mockMvc(false).perform(put("/v2.0/sales-contracts/" + CONTRACT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isOk());
    }

    @Test
    void put_withStaleVersion_returns412ProblemDetail() throws Exception {
        when(service.update(anyLong(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.PRECONDITION_FAILED, "版本已变更"));

        mockMvc(true).perform(put("/v2.0/sales-contracts/" + CONTRACT_ID)
                        .header("X-Resource-Version", "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value(4120));
    }

    @Test
    void put_withoutVersion_whenRequired_returns428ProblemDetail() throws Exception {
        mockMvc(true).perform(put("/v2.0/sales-contracts/" + CONTRACT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody()))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value(4280));
    }

    @Test
    void patchStatus_illegalTransition_returns422ProblemDetail() throws Exception {
        when(service.updateStatus(anyLong(), anyString()))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "当前单据状态不能从「签发」变更为「作废」"));

        mockMvc(false).perform(patch("/v2.0/sales-contracts/" + CONTRACT_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"作废\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(4220));
    }

    @Test
    void delete_returns204NoContent() throws Exception {
        doNothing().when(service).delete(CONTRACT_ID);

        mockMvc(false).perform(delete("/v2.0/sales-contracts/" + CONTRACT_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void contractCheck_locksFieldNamesAndZerosWhenNoContract() throws Exception {
        when(checkService.check(eq(7L), any(), any(), any())).thenReturn(zeroCheck());

        MvcResult result = mockMvc(false)
                .perform(get("/v2.0/sales-orders/contract-checks")
                        .param("projectId", "7")
                        .param("amount", "100.00")
                        .param("tonnage", "1.00000000"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(fieldNames(body)).containsExactlyInAnyOrderElementsOf(CHECK_FIELDS);
        assertThat(body.get("hasContract").asBoolean()).isFalse();
        assertThat(body.get("contractAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("usedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("remainingAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("exceededAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(body.get("exceededTonnage").decimalValue()).isEqualByComparingTo("0");
    }

    private String requestBody() {
        return """
                {"name":"年度合同","customerId":"1","projectId":"2",
                 "signDate":"2026-09-17","totalAmount":1000000.00,"totalTonnage":3000.5}
                """;
    }

    private SalesContractResponse response(String status, BigDecimal amount, Long version) {
        return new SalesContractResponse(
                CONTRACT_ID, "HT-2026-001", "年度合同",
                CUSTOMER_ID, "客户甲", PROJECT_ID, "项目乙",
                LocalDate.of(2026, 9, 17), LocalDate.of(2026, 9, 18), LocalDate.of(2026, 12, 31),
                amount, new BigDecimal("3000.50000000"), status, "备注",
                LocalDateTime.of(2026, 9, 17, 10, 30), LocalDateTime.of(2026, 9, 17, 10, 30), version);
    }

    private SalesContractCheckResponse zeroCheck() {
        BigDecimal amountZero = new BigDecimal("0.00");
        BigDecimal tonnageZero = new BigDecimal("0.00000000");
        return new SalesContractCheckResponse(
                false, amountZero, amountZero, amountZero, tonnageZero, tonnageZero, tonnageZero,
                amountZero, tonnageZero, "该项目未关联有效销售合同");
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
