package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.common.support.ModuleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationLogResultCollectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final ModuleCatalog moduleCatalog = mock(ModuleCatalog.class);
    private final OperationLogResultCollector collector = new OperationLogResultCollector(objectMapper, clientIpResolver, moduleCatalog);

    @Test
    void shouldReturnSuccess_whenApiResponseCodeIsZero() {
        var apiResponse = ApiResponse.success("data");

        String result = collector.resolveResultStatus(apiResponse, null, null);

        assertThat(result).isEqualTo("成功");
    }

    @Test
    void shouldReturnFail_whenApiResponseCodeIsNotZero() {
        var apiResponse = new ApiResponse<>(1, "error", null, null);

        String result = collector.resolveResultStatus(apiResponse, null, null);

        assertThat(result).isEqualTo("失败");
    }

    @Test
    void shouldReturnFail_whenExceptionPresent() {
        var response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        String result = collector.resolveResultStatus(null, response, new RuntimeException());

        assertThat(result).isEqualTo("失败");
    }

    @Test
    void shouldReturnSuccess_whenNoExceptionAndStatusOk() {
        var response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(200);

        String result = collector.resolveResultStatus(null, response, null);

        assertThat(result).isEqualTo("成功");
    }

    @Test
    void shouldReturnFail_whenResponseStatusIs400() {
        var response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(400);

        String result = collector.resolveResultStatus(null, response, null);

        assertThat(result).isEqualTo("失败");
    }

    @Test
    void shouldReturnFail_whenResponseStatusIs500() {
        var response = mock(HttpServletResponse.class);
        when(response.getStatus()).thenReturn(500);

        String result = collector.resolveResultStatus(null, response, null);

        assertThat(result).isEqualTo("失败");
    }

    @Test
    void shouldReturnMessage_whenApiResponseHasMessage() {
        var apiResponse = ApiResponse.success("操作成功", "data");

        String result = collector.resolveRemark(apiResponse, null);

        assertThat(result).isEqualTo("操作成功");
    }

    @Test
    void shouldReturnNull_whenApiResponseMessageIsBlank() {
        var apiResponse = new ApiResponse<>(0, "  ", "data", null);

        String result = collector.resolveRemark(apiResponse, null);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnFailMessage_whenExceptionPresentAndNoApiResponse() {
        String result = collector.resolveRemark(null, new RuntimeException());

        assertThat(result).isEqualTo("请求处理失败");
    }

    @Test
    void shouldReturnNull_whenNoApiResponseAndNoException() {
        String result = collector.resolveRemark(null, null);

        assertThat(result).isNull();
    }

    @Test
    void shouldResolveServletPath_whenAvailable() {
        var request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("/api/test");

        String result = collector.resolveRequestPath(request);

        assertThat(result).isEqualTo("/api/test");
    }

    @Test
    void shouldResolveRequestUri_whenServletPathBlank() {
        var request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn("");
        when(request.getRequestURI()).thenReturn("/context/api/test");

        String result = collector.resolveRequestPath(request);

        assertThat(result).isEqualTo("/context/api/test");
    }

    @Test
    void shouldResolveRequestUri_whenServletPathNull() {
        var request = mock(HttpServletRequest.class);
        when(request.getServletPath()).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/context/api/test");

        String result = collector.resolveRequestPath(request);

        assertThat(result).isEqualTo("/context/api/test");
    }

    @Test
    void shouldResolveIp_fromClientIpResolver() {
        var request = mock(HttpServletRequest.class);
        when(clientIpResolver.resolveClientIpOrUnknown(request)).thenReturn("127.0.0.1");

        String result = collector.resolveIp(request);

        assertThat(result).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldExtractApiResponse_whenAttributePresent() {
        var request = mock(HttpServletRequest.class);
        var apiResponse = ApiResponse.success("data");
        when(request.getAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE)).thenReturn(apiResponse);

        ApiResponse<?> result = collector.extractApiResponse(request);

        assertThat(result).isEqualTo(apiResponse);
    }

    @Test
    void shouldReturnNull_whenAttributeNotApiResponse() {
        var request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE)).thenReturn("notApiResponse");

        ApiResponse<?> result = collector.extractApiResponse(request);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNull_whenAttributeIsNull() {
        var request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResponseBodyAdvice.RESPONSE_BODY_ATTRIBUTE)).thenReturn(null);

        ApiResponse<?> result = collector.extractApiResponse(request);

        assertThat(result).isNull();
    }

    @Test
    void shouldResolveBusinessNo_fromResponseData() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new TestOrderData("ORD001"), null);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("ORD001");
    }

    @Test
    void shouldResolveBusinessNo_fromMultipleFields() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo", "id"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new TestOrderData("ORD001"), null);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("ORD001/1");
    }

    @Test
    void shouldResolveBusinessNo_fromRequestBody() throws Exception {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);

        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        String body = "{\"orderNo\":\"REQ001\"}";
        when(request.getContentAsByteArray()).thenReturn(body.getBytes(StandardCharsets.UTF_8));

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isEqualTo("REQ001");
    }

    @Test
    void shouldFallbackBusinessNoToRecordIdField() {
        var metadata = new OperationLogMetadata("打印", "moduleKey", "打印", new String[]{"businessNo"}, "recordId", "moduleKey", false);
        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        String body = "{\"moduleKey\":\"sales-order\",\"recordId\":\"1\"}";
        when(request.getContentAsByteArray()).thenReturn(body.getBytes(StandardCharsets.UTF_8));

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isEqualTo("1");
    }

    @Test
    void shouldResolveLogContextFromAttributes() {
        var metadata = new OperationLogMetadata("销售订单", "", "打印", new String[]{"id"}, "id", "moduleKey", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResultCollector.BUSINESS_NO_ATTRIBUTE)).thenReturn("SO-001");
        when(request.getAttribute(OperationLogResultCollector.RECORD_ID_ATTRIBUTE)).thenReturn(1L);
        when(request.getAttribute(OperationLogResultCollector.MODULE_KEY_ATTRIBUTE)).thenReturn("sales-order");

        assertThat(collector.resolveBusinessNo(request, null, metadata)).isEqualTo("SO-001");
        assertThat(collector.resolveRecordId(request, null, metadata)).isEqualTo(1L);
        assertThat(collector.resolveModuleKey(request, null, metadata)).isEqualTo("sales-order");
    }

    @Test
    void shouldResolveModuleName_fromRequestBodyModuleKey() {
        var metadata = new OperationLogMetadata("打印", "moduleKey", "打印", new String[]{"recordId"}, "", "", false);
        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        String body = "{\"moduleKey\":\"sales-order\",\"recordId\":\"1\"}";
        when(request.getContentAsByteArray()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(moduleCatalog.resolveModuleName("sales-order")).thenReturn("销售订单");

        String result = collector.resolveModuleName(request, metadata);

        assertThat(result).isEqualTo("销售订单");
    }

    @Test
    void shouldResolveBusinessNo_fromUriVariables() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "查看", new String[]{"id"}, "", "", false);
        Map<String, String> uriVars = new HashMap<>();
        uriVars.put("id", "123");
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(uriVars);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isEqualTo("123");
    }

    @Test
    void shouldReturnNullBusinessNo_whenNoSource() {
        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        var metadata = new OperationLogMetadata("测试模块", "", "查看", new String[]{"id"}, "", "", false);
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(null);
        when(request.getContentAsByteArray()).thenReturn(new byte[0]);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullBusinessNo_whenNoFields() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "查看", new String[]{}, "", "", false);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullBusinessNo_whenFieldsNull() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "查看", null, "", "", false);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullBusinessNo_whenUriIdBlank() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "查看", new String[]{"id"}, "", "", false);
        Map<String, String> uriVars = new HashMap<>();
        uriVars.put("id", "  ");
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(uriVars);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReadValue_fromJsonNode() throws Exception {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);
        var jsonNode = objectMapper.readTree("{\"orderNo\":\"JSON001\"}");
        var apiResponse = new ApiResponse<>(0, "成功", jsonNode, null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("JSON001");
    }

    @Test
    void shouldReadValue_fromMap() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"key"}, "", "", false);
        Map<String, Object> data = Map.of("key", "val123");
        var apiResponse = new ApiResponse<>(0, "成功", data, null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("val123");
    }

    @Test
    void shouldReturnNull_whenRequestBodyEmpty() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);
        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        when(request.getContentAsByteArray()).thenReturn(new byte[0]);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNull_whenRequestBodyInvalidJson() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);
        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        when(request.getContentAsByteArray()).thenReturn("not json".getBytes(StandardCharsets.UTF_8));

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNull_whenNotContentCachingRequestWrapper() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);
        HttpServletRequest request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    static class TestOrderData {
        private String orderNo;
        private Long id = 1L;

        TestOrderData(String orderNo) {
            this.orderNo = orderNo;
        }

        public String getOrderNo() {
            return orderNo;
        }

        public Long getId() {
            return id;
        }
    }
}
