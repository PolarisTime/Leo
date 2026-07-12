package com.leo.erp.master.supplier.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplierOptionResponseTest {

    private static final long LARGE_SNOWFLAKE_ID = 9007199254740993L;

    @Test
    void shouldDeclareStableIdentityAndDisplaySnapshotContract() {
        Map<String, Class<?>> components = Arrays.stream(SupplierOptionResponse.class.getRecordComponents())
                .collect(Collectors.toMap(
                        java.lang.reflect.RecordComponent::getName,
                        java.lang.reflect.RecordComponent::getType
                ));

        assertThat(components)
                .containsEntry("id", Long.class)
                .containsEntry("value", Long.class)
                .containsEntry("label", String.class)
                .containsEntry("supplierCode", String.class)
                .containsEntry("supplierName", String.class);
    }

    @Test
    void shouldSerializeSnowflakeIdAndValueAsExactStrings() throws Exception {
        SupplierOptionResponse response = new SupplierOptionResponse(
                LARGE_SNOWFLAKE_ID,
                LARGE_SNOWFLAKE_ID,
                "供应商甲",
                "S001",
                "供应商甲"
        );

        String json = objectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"id\":\"9007199254740993\"")
                .contains("\"value\":\"9007199254740993\"")
                .contains("\"supplierCode\":\"S001\"")
                .contains("\"supplierName\":\"供应商甲\"");
    }

    @Test
    void shouldBuildAuthoritativeTrimmedLabelFromCodeAndName() {
        SupplierOptionResponse response = new SupplierOptionResponse(
                1L,
                " S001 ",
                " 供应商甲 "
        );

        assertThat(response.label()).isEqualTo("S001 / 供应商甲");
        assertThat(response.supplierCode()).isEqualTo("S001");
        assertThat(response.supplierName()).isEqualTo("供应商甲");
    }

    @Test
    void shouldRejectMissingNonPositiveOrMismatchedStableIdentity() {
        assertThatThrownBy(() -> new SupplierOptionResponse(
                null, null, "供应商甲", "S001", "供应商甲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new SupplierOptionResponse(
                0L, 0L, "供应商甲", "S001", "供应商甲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new SupplierOptionResponse(
                1L, 2L, "供应商甲", "S001", "供应商甲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value必须与ID一致");
    }

    @Test
    void shouldRejectIncompleteDisplaySnapshot() {
        assertThatThrownBy(() -> new SupplierOptionResponse(
                1L, 1L, " ", "S001", "供应商甲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
        assertThatThrownBy(() -> new SupplierOptionResponse(
                1L, 1L, "供应商甲", " ", "供应商甲"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商编码");
        assertThatThrownBy(() -> new SupplierOptionResponse(
                1L, 1L, "供应商甲", "S001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("供应商名称");
    }

    @Test
    void shouldImplementEquals() {
        SupplierOptionResponse r1 = new SupplierOptionResponse(1L, "S001", "供应商甲");
        SupplierOptionResponse r2 = new SupplierOptionResponse(1L, "S001", "供应商甲");

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        SupplierOptionResponse r1 = new SupplierOptionResponse(1L, "S001", "供应商甲");
        SupplierOptionResponse r2 = new SupplierOptionResponse(1L, "S001", "供应商甲");

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    private ObjectMapper objectMapper() {
        JacksonConfig config = new JacksonConfig("Asia/Shanghai");
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

}
