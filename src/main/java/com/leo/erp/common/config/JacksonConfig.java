package com.leo.erp.common.config;

import com.leo.erp.common.json.ScaledBigDecimalSerializer;
import com.leo.erp.common.support.DateTimeFormatSupport;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(BigDecimal.class, new ScaledBigDecimalSerializer())
                .serializerByType(LocalDateTime.class, new EpochMillisLocalDateTimeSerializer())
                .deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer())
                .deserializerByType(LocalDate.class, new FlexibleLocalDateDeserializer());
    }

    /** Serialize LocalDateTime → epoch milliseconds (Asia/Shanghai). */
    static class EpochMillisLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            long epoch = value.atZone(ZONE).toInstant().toEpochMilli();
            gen.writeNumber(epoch);
        }
    }

    /** Deserialize LocalDateTime from epoch millis (number) or "yyyy-MM-dd HH:mm:ss" (string). */
    static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FMT = DateTimeFormatSupport.DATE_TIME_FORMATTER;

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                long epoch = p.getLongValue();
                return Instant.ofEpochMilli(epoch).atZone(ZONE).toLocalDateTime();
            }
            String text = p.getText().trim();
            try {
                return LocalDateTime.parse(text, FMT);
            } catch (DateTimeParseException e) {
                long epoch = Long.parseLong(text);
                return Instant.ofEpochMilli(epoch).atZone(ZONE).toLocalDateTime();
            }
        }
    }

    /** Deserialize LocalDate from epoch millis or "yyyy-MM-dd" or "yyyy-MM-dd HH:mm:ss". */
    static class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                long epoch = p.getLongValue();
                return Instant.ofEpochMilli(epoch).atZone(ZONE).toLocalDate();
            }
            String text = p.getText().trim();
            if (text.length() > 10) {
                try {
                    return LocalDate.parse(text, DATE_TIME);
                } catch (DateTimeParseException e) {
                    // fall through
                }
            }
            try {
                return LocalDate.parse(text, DATE_ONLY);
            } catch (DateTimeParseException e) {
                long epoch = Long.parseLong(text);
                return Instant.ofEpochMilli(epoch).atZone(ZONE).toLocalDate();
            }
        }
    }
}
