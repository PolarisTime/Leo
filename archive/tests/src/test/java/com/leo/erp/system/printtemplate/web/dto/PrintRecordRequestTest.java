package com.leo.erp.system.printtemplate.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRecordRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeAndNormalizePrintOptions() throws Exception {
        PrintRecordRequest request = objectMapper.readValue("""
                {
                  "moduleKey": "sales-order",
                  "templateId": "template-1",
                  "recordId": "1",
                  "printOptions": {
                    "hideUnitPrice": true,
                    "hideRemark": true,
                    "brandOverrides": {"抚顺新钢": " 抚新 "},
                    "brandOverridesByItemId": {"11": " 沙钢 "},
                    "itemOrder": ["12", "11", "11", ""]
                  }
                }
                """, PrintRecordRequest.class);

        assertThat(request.moduleKey()).isEqualTo("sales-order");
        assertThat(request.templateId()).isEqualTo("template-1");
        assertThat(request.recordId()).isEqualTo(1L);
        assertThat(request.resolvedPrintOptions().hideUnitPrice()).isTrue();
        assertThat(request.resolvedPrintOptions().hideRemark()).isTrue();
        assertThat(request.resolvedPrintOptions().brandOverrides()).isEqualTo(Map.of("抚顺新钢", "抚新"));
        assertThat(request.resolvedPrintOptions().brandOverridesByItemId()).isEqualTo(Map.of("11", "沙钢"));
        assertThat(request.resolvedPrintOptions().itemOrder()).isEqualTo(List.of("12", "11"));
    }

    @Test
    void shouldUseDefaultOptionsWhenMissing() throws Exception {
        PrintRecordRequest request = objectMapper.readValue("""
                {"moduleKey":"sales-order","templateId":"template-1","recordId":1}
                """, PrintRecordRequest.class);

        assertThat(request.resolvedPrintOptions().hideUnitPrice()).isFalse();
        assertThat(request.resolvedPrintOptions().brandOverridesByItemId()).isEmpty();
        assertThat(request.resolvedPrintOptions().itemOrder()).isEmpty();
    }
}
