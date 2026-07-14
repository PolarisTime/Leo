package com.leo.erp.sales.order.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderPrintXlsxRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeAndNormalizePrintOptions() throws Exception {
        SalesOrderPrintXlsxRequest request = objectMapper.readValue("""
                {
                  "printOptions": {
                    "hideUnitPrice": true,
                    "hideRemark": true,
                    "brandOverridesByItemId": {"11": " 抚新 "},
                    "itemOrder": ["12", "11", "11", ""]
                  }
                }
                """, SalesOrderPrintXlsxRequest.class);

        assertThat(request.resolvedPrintOptions().hideUnitPrice()).isTrue();
        assertThat(request.resolvedPrintOptions().hideRemark()).isTrue();
        assertThat(request.resolvedPrintOptions().brandOverridesByItemId()).isEqualTo(Map.of("11", "抚新"));
        assertThat(request.resolvedPrintOptions().itemOrder()).isEqualTo(List.of("12", "11"));
    }

    @Test
    void shouldUseDefaultOptionsWhenMissing() throws Exception {
        SalesOrderPrintXlsxRequest request = objectMapper.readValue("{}", SalesOrderPrintXlsxRequest.class);

        assertThat(request.resolvedPrintOptions().hideUnitPrice()).isFalse();
        assertThat(request.resolvedPrintOptions().brandOverridesByItemId()).isEmpty();
        assertThat(request.resolvedPrintOptions().itemOrder()).isEmpty();
    }
}
