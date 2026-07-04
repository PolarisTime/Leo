package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.ClientIpResolver;
import com.leo.erp.common.support.ModuleCatalog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
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
    void shouldReturnNull_whenApiResponseMessageIsNull() {
        var apiResponse = new ApiResponse<>(0, null, "data", null);

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
    void shouldTrimStringAttributesAndFallbackWhenBusinessNoAttributeBlank() {
        var metadata = new OperationLogMetadata("销售订单", "", "打印", new String[]{"businessNo"}, "id", "moduleKey", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResultCollector.BUSINESS_NO_ATTRIBUTE)).thenReturn("   ");
        var apiResponse = new ApiResponse<>(0, "成功", Map.of("businessNo", "SO-002"), null);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("SO-002");
    }

    @Test
    void shouldResolveRecordIdFromStringAttribute() {
        var metadata = new OperationLogMetadata("销售订单", "", "查看", new String[0], "id", "", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResultCollector.RECORD_ID_ATTRIBUTE)).thenReturn(" 42 ");

        Long result = collector.resolveRecordId(request, null, metadata);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    void shouldIgnoreInvalidRecordIdAttributeAndResolvedValue() {
        var metadata = new OperationLogMetadata("销售订单", "", "查看", new String[0], "id", "", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResultCollector.RECORD_ID_ATTRIBUTE)).thenReturn("not-number");
        var apiResponse = new ApiResponse<>(0, "成功", Map.of("id", "abc"), null);

        Long result = collector.resolveRecordId(request, apiResponse, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullRecordId_whenAttributeBlankAndFieldNull() {
        var metadata = new OperationLogMetadata("销售订单", "", "查看", new String[0], null, "", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(OperationLogResultCollector.RECORD_ID_ATTRIBUTE)).thenReturn("   ");

        Long result = collector.resolveRecordId(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldResolveRecordIdFromUriVariablesWhenResponseAndRequestMissing() {
        var metadata = new OperationLogMetadata("销售订单", "", "查看", new String[0], "id", "", false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(Map.of("id", "77"));

        Long result = collector.resolveRecordId(request, null, metadata);

        assertThat(result).isEqualTo(77L);
    }

    @Test
    void shouldResolveModuleKeyFromResponseRequestAndUriVariables() {
        var metadata = new OperationLogMetadata("销售订单", "", "查看", new String[0], "", "moduleKey", false);
        HttpServletRequest responseRequest = mock(HttpServletRequest.class);
        var apiResponse = new ApiResponse<>(0, "成功", Map.of("moduleKey", "sales-order"), null);

        assertThat(collector.resolveModuleKey(responseRequest, apiResponse, metadata)).isEqualTo("sales-order");

        ContentCachingRequestWrapper bodyRequest = mock(ContentCachingRequestWrapper.class);
        when(bodyRequest.getContentAsByteArray())
                .thenReturn("{\"moduleKey\":\"purchase-order\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(collector.resolveModuleKey(bodyRequest, null, metadata)).isEqualTo("purchase-order");

        HttpServletRequest uriRequest = mock(HttpServletRequest.class);
        when(uriRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(Map.of("moduleKey", "inventory-report"));

        assertThat(collector.resolveModuleKey(uriRequest, null, metadata)).isEqualTo("inventory-report");
    }

    @Test
    void shouldReturnNullModuleKey_whenUriVariablesAbsentOrMissingValue() {
        var metadata = new OperationLogMetadata("销售订单", "", "查看", new String[0], "", "moduleKey", false);
        HttpServletRequest absentUriRequest = mock(HttpServletRequest.class);

        assertThat(collector.resolveModuleKey(absentUriRequest, null, metadata)).isNull();

        HttpServletRequest missingValueRequest = mock(HttpServletRequest.class);
        when(missingValueRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE))
                .thenReturn(new HashMap<>());

        assertThat(collector.resolveModuleKey(missingValueRequest, null, metadata)).isNull();
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
    void shouldResolveModuleNameFallbacks() {
        assertThat(collector.resolveModuleName(mock(HttpServletRequest.class), null)).isNull();

        var nullFieldMetadata = new OperationLogMetadata("空字段模块", null, "查看", new String[0], "", "", false);
        assertThat(collector.resolveModuleName(mock(HttpServletRequest.class), nullFieldMetadata)).isEqualTo("空字段模块");

        var fixedMetadata = new OperationLogMetadata("固定模块", " ", "查看", new String[0], "", "", false);
        assertThat(collector.resolveModuleName(mock(HttpServletRequest.class), fixedMetadata)).isEqualTo("固定模块");

        var missingBodyMetadata = new OperationLogMetadata("默认模块", "moduleKey", "查看", new String[0], "", "", false);
        ContentCachingRequestWrapper blankRequest = mock(ContentCachingRequestWrapper.class);
        when(blankRequest.getContentAsByteArray()).thenReturn("   ".getBytes(StandardCharsets.UTF_8));
        assertThat(collector.resolveModuleName(blankRequest, missingBodyMetadata)).isEqualTo("默认模块");

        ContentCachingRequestWrapper trimmedBlankRequest = mock(ContentCachingRequestWrapper.class);
        String body = "{\"moduleKey\":\"   \"}";
        when(trimmedBlankRequest.getContentAsByteArray()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        assertThat(collector.resolveModuleName(trimmedBlankRequest, missingBodyMetadata)).isEqualTo("默认模块");

        ContentCachingRequestWrapper nullModuleKeyRequest = mock(ContentCachingRequestWrapper.class);
        when(nullModuleKeyRequest.getContentAsByteArray()).thenReturn("{\"moduleKey\":null}".getBytes(StandardCharsets.UTF_8));
        assertThat(collector.resolveModuleName(nullModuleKeyRequest, missingBodyMetadata)).isEqualTo("默认模块");

        OperationLogResultCollector collectorWithoutCatalog = new OperationLogResultCollector(
                objectMapper,
                clientIpResolver,
                null
        );
        ContentCachingRequestWrapper request = mock(ContentCachingRequestWrapper.class);
        when(request.getContentAsByteArray())
                .thenReturn("{\"moduleKey\":\"raw-module\"}".getBytes(StandardCharsets.UTF_8));
        assertThat(collectorWithoutCatalog.resolveModuleName(request, missingBodyMetadata)).isEqualTo("raw-module");
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
    void shouldReturnNullBusinessNo_whenUriIdMissing() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "查看", new String[0], "", "", false);
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(new HashMap<>());

        String result = collector.resolveBusinessNo(request, null, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldIgnoreNullAndBlankBusinessNoFields() {
        var request = mock(HttpServletRequest.class);
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{null, " "}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", Map.of("orderNo", "ORD001"), null);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

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
    void shouldIgnoreJsonNodeNullBlankAndMissingTextValues() throws Exception {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"orderNo"}, "", "", false);
        var request = mock(HttpServletRequest.class);
        var nullValueResponse = new ApiResponse<>(0, "成功", objectMapper.readTree("{\"orderNo\":null}"), null);
        var blankValueResponse = new ApiResponse<>(0, "成功", objectMapper.readTree("{\"orderNo\":\"   \"}"), null);
        var missingTextNode = objectMapper.createObjectNode()
                .set("orderNo", MissingNode.getInstance());
        var missingTextResponse = new ApiResponse<>(0, "成功", missingTextNode, null);

        assertThat(collector.resolveBusinessNo(request, nullValueResponse, metadata)).isNull();
        assertThat(collector.resolveBusinessNo(request, blankValueResponse, metadata)).isNull();
        assertThat(collector.resolveBusinessNo(request, missingTextResponse, metadata)).isNull();
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
    void shouldIgnoreNullValueFromMap() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"key"}, "", "", false);
        Map<String, Object> data = new HashMap<>();
        data.put("key", null);
        var apiResponse = new ApiResponse<>(0, "成功", data, null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReadValueFromNoArgMethodNamedAsField() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"ticketNo"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new MethodOnlyData(), null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("METHOD-001");
    }

    @Test
    void shouldReadValueFromInheritedFieldWhenNoAccessorExists() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"inheritedNo"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new ChildOrderData(), null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isEqualTo("BASE-001");
    }

    @Test
    void shouldReturnNullWhenNoPojoAccessorOrFieldExists() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"missingNo"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new NoReadableData(), null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenPojoFieldMissingThroughSuperclassChain() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"missingNo"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new ChildOrderData(), null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenPojoValueTrimsToEmpty() {
        var metadata = new OperationLogMetadata("测试模块", "", "创建", new String[]{"blankNo"}, "", "", false);
        var apiResponse = new ApiResponse<>(0, "成功", new BlankFieldData(), null);
        var request = mock(HttpServletRequest.class);

        String result = collector.resolveBusinessNo(request, apiResponse, metadata);

        assertThat(result).isNull();
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

    static class MethodOnlyData {
        public String ticketNo() {
            return "METHOD-001";
        }
    }

    static class BaseOrderData {
        private final String inheritedNo = "BASE-001";
    }

    static class ChildOrderData extends BaseOrderData {
    }

    static class NoReadableData {
    }

    static class BlankFieldData {
        private final String blankNo = "   ";
    }
}
