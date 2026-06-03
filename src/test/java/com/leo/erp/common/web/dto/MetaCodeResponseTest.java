package com.leo.erp.common.web.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaCodeResponseTest {

    @Test
    void recordAccessors() {
        MetaErrorCodeResponse errorCode = new MetaErrorCodeResponse("ERR", 500, "error");
        Map<String, String> resourceLabels = Map.of("user", "用户");
        Map<String, String> actionLabels = Map.of("create", "创建");

        MetaCodeResponse response = new MetaCodeResponse(
                List.of(errorCode), resourceLabels, actionLabels
        );

        assertThat(response.errorCodes()).hasSize(1);
        assertThat(response.errorCodes().get(0)).isEqualTo(errorCode);
        assertThat(response.resourceLabels()).containsEntry("user", "用户");
        assertThat(response.actionLabels()).containsEntry("create", "创建");
    }

    @Test
    void recordEquality() {
        MetaErrorCodeResponse errorCode = new MetaErrorCodeResponse("ERR", 500, "error");
        MetaCodeResponse a = new MetaCodeResponse(List.of(errorCode), Map.of(), Map.of());
        MetaCodeResponse b = new MetaCodeResponse(List.of(errorCode), Map.of(), Map.of());
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void emptyLists() {
        MetaCodeResponse response = new MetaCodeResponse(List.of(), Map.of(), Map.of());
        assertThat(response.errorCodes()).isEmpty();
        assertThat(response.resourceLabels()).isEmpty();
        assertThat(response.actionLabels()).isEmpty();
    }
}
