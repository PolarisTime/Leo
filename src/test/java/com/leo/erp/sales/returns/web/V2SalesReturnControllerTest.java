package com.leo.erp.sales.returns.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.JacksonConfig;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.web.dto.StatusUpdateRequest;
import com.leo.erp.sales.returns.service.SalesReturnCandidateService;
import com.leo.erp.sales.returns.service.SalesReturnService;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateItemResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnItemResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2SalesReturnController REST 契约测试：状态码与雪花 ID 字符串。
 */
@ExtendWith(MockitoExtension.class)
class V2SalesReturnControllerTest {

    @Mock
    private SalesReturnService service;

    @Mock
    private SalesReturnCandidateService candidateService;

    @InjectMocks
    private V2SalesReturnController controller;

    @BeforeEach
    void setUpServletContext() {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private SalesReturnRequest request() {
        return new SalesReturnRequest(
                "SR001", "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 9, 1), "草稿", null, List.of(), false);
    }

    @Test
    void page_shouldDelegateWithFilter() {
        when(service.page(any(PageQuery.class), any(PageFilter.class)))
                .thenReturn(mock(org.springframework.data.domain.Page.class));

        var result = controller.page(mock(PageQuery.class), "kw", 10L, "客户A", 20L, "项目A",
                "草稿", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result).isNotNull();
        verify(service).page(any(PageQuery.class), any(PageFilter.class));
    }

    @Test
    void detail_shouldDelegate() {
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(service.detail(5L)).thenReturn(response);

        assertThat(controller.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreatedWithLocation() {
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(response.id()).thenReturn(5L);
        when(service.create(any())).thenReturn(response);

        ResponseEntity<SalesReturnResponse> result = controller.create(request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isSameAs(response);
        assertThat(result.getHeaders().getLocation()).isNotNull();
        assertThat(result.getHeaders().getLocation().getPath()).endsWith("/v2.0/sales-returns/5");
    }

    @Test
    void update_shouldDelegate() {
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(service.update(anyLong(), any())).thenReturn(response);

        assertThat(controller.update(5L, request())).isSameAs(response);
    }

    @Test
    void createAudit_shouldAuditExistingWhenBodyAbsent() {
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(response.id()).thenReturn(5L);
        when(service.audit(5L)).thenReturn(response);

        ResponseEntity<SalesReturnResponse> result = controller.createAudit(5L, null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(service).audit(5L);
        verify(service, never()).updateAndAudit(anyLong(), any());
    }

    @Test
    void createAudit_shouldSaveAndAuditWhenBodyPresent() {
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(response.id()).thenReturn(5L);
        when(service.updateAndAudit(anyLong(), any())).thenReturn(response);

        ResponseEntity<SalesReturnResponse> result = controller.createAudit(5L, request());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(service).updateAndAudit(5L, request());
        verify(service, never()).audit(anyLong());
    }

    @Test
    void updateStatus_shouldPassStatusFromRequest() {
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(service.updateStatus(anyLong(), anyString())).thenReturn(response);

        assertThat(controller.updateStatus(5L, new StatusUpdateRequest("已审核"))).isSameAs(response);
        verify(service).updateStatus(5L, "已审核");
    }

    @Test
    void delete_shouldReturnNoContent() {
        ResponseEntity<Void> result = controller.delete(5L);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(5L);
    }

    @Test
    void candidates_shouldDelegateToCandidateService() {
        SalesReturnCandidateResponse response = mock(SalesReturnCandidateResponse.class);
        when(candidateService.candidates(7L)).thenReturn(response);

        assertThat(controller.candidates(7L)).isSameAs(response);
        verify(candidateService).candidates(7L);
    }

    @Test
    void candidates_shouldReturn400WithValidationErrorWhenSalesOutboundIdMissing() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();

        mockMvc.perform(get("/v2.0/sales-returns/candidates"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4000));
    }

    @Test
    void candidateResponse_shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        SalesReturnCandidateResponse response = new SalesReturnCandidateResponse(
                9007199254740993L, "OB001", "SO001", 9007199254740995L, "客户A",
                9007199254740997L, "项目A", 9007199254740999L, "库房A",
                9007199254741001L, "主体A",
                List.of(new SalesReturnCandidateItemResponse(
                        9007199254741003L, 9007199254741005L, 9007199254741007L, "M001", "品牌", "型钢",
                        "螺纹钢", "HRB400", "12m", "吨", 9007199254741009L, "库房A", "B001", "件",
                        null, 100, 10, 4, 6, null)));

        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"salesOutboundId\":\"9007199254740993\"");
        assertThat(json).contains("\"sourceSalesOutboundItemId\":\"9007199254741003\"");
        assertThat(json).doesNotContain("9007199254740993,");
    }

    @Test
    void response_shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        Jackson2ObjectMapperBuilderCustomizer customizer =
                new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer();
        customizer.customize(builder);
        ObjectMapper mapper = builder.build();

        SalesReturnResponse response = new SalesReturnResponse(
                9223372036854775807L, "SR001", "SO001", 9007199254740993L, "客户A",
                9007199254740995L, "项目A", 9007199254740997L, "库房A", null, null,
                LocalDate.of(2026, 9, 1), null, null, "草稿", false, null,
                List.of(new SalesReturnItemResponse(
                        9007199254740999L, 1, 9007199254741001L, "OB001", 9007199254741003L, "SO001",
                        null, null, null, null, null, "M001", "品牌", "型钢", "螺纹钢", "HRB400", "12m",
                        "吨", null, "库房A", null, null, 5, "件", null, null, null, null, null)));

        String json = mapper.writeValueAsString(response);

        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"sourceSalesOutboundItemId\":\"9007199254741001\"");
        assertThat(json).doesNotContain("9223372036854775807,");
    }
}
