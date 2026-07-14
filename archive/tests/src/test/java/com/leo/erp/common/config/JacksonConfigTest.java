package com.leo.erp.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonConfigTest {

    @Test
    void defaultTimezone_isShanghai() {
        JacksonConfig config = new JacksonConfig("Asia/Shanghai");
        assertThat(config).isNotNull();
    }

    @Test
    void invalidTimezone_throwsException() {
        assertThatThrownBy(() -> new JacksonConfig("Invalid/Zone"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid timezone");
    }

    @Test
    void customizer_longSerializedAsString() throws Exception {
        ObjectMapper mapper = buildMapper();

        String json = mapper.writeValueAsString(12345L);
        assertThat(json).isEqualTo("\"12345\"");
    }

    @Test
    void customizer_longPrimitiveSerializedAsString() throws Exception {
        ObjectMapper mapper = buildMapper();

        record Holder(long value) {}
        String json = mapper.writeValueAsString(new Holder(99L));
        assertThat(json).contains("\"99\"");
    }

    @Test
    void customizer_localDateTimeSerializedAsEpoch() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDateTime ldt = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        String json = mapper.writeValueAsString(ldt);
        long expected = ldt.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertThat(json).isEqualTo(String.valueOf(expected));
    }

    @Test
    void customizer_localDateSerializedAsEpoch() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDate ld = LocalDate.of(2024, 1, 1);
        String json = mapper.writeValueAsString(ld);
        long expected = ld.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        assertThat(json).isEqualTo(String.valueOf(expected));
    }

    @Test
    void customizer_deserializesEpochMillisToLocalDateTime() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDateTime original = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        long epoch = original.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        LocalDateTime result = mapper.readValue(String.valueOf(epoch), LocalDateTime.class);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void customizer_deserializesFormattedStringToLocalDateTime() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDateTime result = mapper.readValue("\"2024-06-15 10:30:00\"", LocalDateTime.class);
        assertThat(result).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
    }

    @Test
    void customizer_deserializesEpochStringToLocalDateTime() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDateTime original = LocalDateTime.of(2024, 6, 15, 10, 30, 0);
        long epoch = original.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        LocalDateTime result = mapper.readValue("\"" + epoch + "\"", LocalDateTime.class);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void customizer_deserializesEpochMillisToLocalDate() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDate original = LocalDate.of(2024, 6, 15);
        long epoch = original.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        LocalDate result = mapper.readValue(String.valueOf(epoch), LocalDate.class);
        assertThat(result).isEqualTo(original);
    }

    @Test
    void customizer_deserializesFormattedStringToLocalDate() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDate result = mapper.readValue("\"2024-06-15\"", LocalDate.class);
        assertThat(result).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    @Test
    void customizer_deserializesDateTimeStringToLocalDate() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDate result = mapper.readValue("\"2024-06-15 10:30:00\"", LocalDate.class);
        assertThat(result).isEqualTo(LocalDate.of(2024, 6, 15));
    }

    @Test
    void customizer_deserializesEpochStringToLocalDate() throws Exception {
        ObjectMapper mapper = buildMapper();

        LocalDate original = LocalDate.of(2024, 6, 15);
        long epoch = original.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

        LocalDate result = mapper.readValue("\"" + epoch + "\"", LocalDate.class);
        assertThat(result).isEqualTo(original);
    }

    private ObjectMapper buildMapper() {
        JacksonConfig config = new JacksonConfig("Asia/Shanghai");
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        config.jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }
}
