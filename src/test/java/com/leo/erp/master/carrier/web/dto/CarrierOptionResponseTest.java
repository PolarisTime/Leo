package com.leo.erp.master.carrier.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarrierOptionResponseTest {

    private static final long LARGE_SNOWFLAKE_ID = 9007199254740993L;

    @Test
    void shouldDeclareStableCarrierAndVehicleOptionContracts() {
        Map<String, Class<?>> carrierComponents = Arrays.stream(CarrierOptionResponse.class.getRecordComponents())
                .collect(Collectors.toMap(
                        java.lang.reflect.RecordComponent::getName,
                        java.lang.reflect.RecordComponent::getType
                ));

        assertThat(carrierComponents)
                .containsEntry("id", Long.class)
                .containsEntry("value", Long.class)
                .containsEntry("label", String.class)
                .containsEntry("carrierCode", String.class)
                .containsEntry("carrierName", String.class)
                .containsEntry("defaultSettlementCompanyId", Long.class)
                .containsEntry("defaultSettlementCompanyName", String.class)
                .containsEntry("vehicles", List.class);
        Map<String, Class<?>> vehicleComponents = Arrays.stream(VehicleOptionResponse.class.getRecordComponents())
                .collect(Collectors.toMap(
                        java.lang.reflect.RecordComponent::getName,
                        java.lang.reflect.RecordComponent::getType
                ));
        assertThat(vehicleComponents)
                .containsEntry("id", Long.class)
                .containsEntry("value", Long.class)
                .containsEntry("label", String.class)
                .containsEntry("plate", String.class);
    }

    @Test
    void shouldSerializeSnowflakeIdAndValueAsExactStrings() throws Exception {
        CarrierOptionResponse response = new CarrierOptionResponse(
                LARGE_SNOWFLAKE_ID,
                LARGE_SNOWFLAKE_ID,
                "承运商A",
                "CR-001",
                "承运商A",
                null,
                null,
                List.of(
                        new VehicleOptionResponse(101L, "沪A12345"),
                        new VehicleOptionResponse(102L, "京B67890")
                )
        );

        String json = objectMapper().writeValueAsString(response);

        assertThat(json)
                .contains("\"id\":\"9007199254740993\"")
                .contains("\"value\":\"9007199254740993\"")
                .contains("\"carrierCode\":\"CR-001\"")
                .contains("\"carrierName\":\"承运商A\"")
                .contains("\"vehicles\":[")
                .contains("\"id\":\"101\"")
                .contains("\"vehiclePlates\":[\"沪A12345\",\"京B67890\"]");
    }

    @Test
    void shouldRejectMissingNonPositiveOrMismatchedCarrierIdentity() {
        assertThatThrownBy(() -> new CarrierOptionResponse(
                null, null, "物流商甲", "CR001", "物流商甲", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new CarrierOptionResponse(
                0L, 0L, "物流商甲", "CR001", "物流商甲", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new CarrierOptionResponse(
                1L, 2L, "物流商甲", "CR001", "物流商甲", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value必须与ID一致");
    }

    @Test
    void shouldRejectIncompleteCarrierOrSettlementSnapshot() {
        assertThatThrownBy(() -> new CarrierOptionResponse(
                1L, 1L, "物流商甲", " ", "物流商甲", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流商编码");
        assertThatThrownBy(() -> new CarrierOptionResponse(
                1L, 1L, "物流商甲", "CR001", "物流商甲", 9L, " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结算主体名称");
        assertThatThrownBy(() -> new CarrierOptionResponse(
                1L, 1L, "物流商甲", "CR001", "物流商甲", null, "上海结算主体", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("结算主体ID");
    }

    @Test
    void shouldRejectMissingNonPositiveOrMismatchedVehicleIdentityAndPlate() {
        assertThatThrownBy(() -> new VehicleOptionResponse(null, null, "苏A12345", "苏A12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("车辆选项")
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new VehicleOptionResponse(0L, 0L, "苏A12345", "苏A12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("车辆选项")
                .hasMessageContaining("稳定ID");
        assertThatThrownBy(() -> new VehicleOptionResponse(1L, 2L, "苏A12345", "苏A12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value必须与ID一致");
        assertThatThrownBy(() -> new VehicleOptionResponse(1L, 1L, "苏A12345", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("车牌");
    }

    @Test
    void shouldSupportEmptyVehiclePlates() {
        CarrierOptionResponse response = new CarrierOptionResponse(
                1L, " CR001 ", " 承运商A ", null, null, List.of()
        );

        assertThat(response.label()).isEqualTo("CR001 / 承运商A");
        assertThat(response.carrierCode()).isEqualTo("CR001");
        assertThat(response.carrierName()).isEqualTo("承运商A");
        assertThat(response.vehicles()).isEmpty();
        assertThat(response.vehiclePlates()).isEmpty();
    }

    @Test
    void shouldImplementEquals() {
        CarrierOptionResponse r1 = new CarrierOptionResponse(
                1L, "CR001", "承运商A", null, null,
                List.of(new VehicleOptionResponse(101L, "沪A12345"))
        );
        CarrierOptionResponse r2 = new CarrierOptionResponse(
                1L, "CR001", "承运商A", null, null,
                List.of(new VehicleOptionResponse(101L, "沪A12345"))
        );

        assertThat(r1).isEqualTo(r2);
    }

    @Test
    void shouldImplementHashCode() {
        CarrierOptionResponse r1 = new CarrierOptionResponse(
                1L, "CR001", "承运商A", null, null,
                List.of(new VehicleOptionResponse(101L, "沪A12345"))
        );
        CarrierOptionResponse r2 = new CarrierOptionResponse(
                1L, "CR001", "承运商A", null, null,
                List.of(new VehicleOptionResponse(101L, "沪A12345"))
        );

        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    private ObjectMapper objectMapper() {
        JacksonConfig config = new JacksonConfig("Asia/Shanghai");
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

}
