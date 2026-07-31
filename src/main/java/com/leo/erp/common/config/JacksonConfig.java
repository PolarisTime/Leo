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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    private final ZoneId zone;

    public JacksonConfig(@org.springframework.beans.factory.annotation.Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        try {
            this.zone = ZoneId.of(timezone);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid timezone configured: " + timezone, e);
        }
    }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(BigDecimal.class, new ScaledBigDecimalSerializer())
                .serializerByType(LocalDateTime.class, new IsoLocalDateTimeSerializer())
                .deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer())
                .serializerByType(LocalDate.class, new IsoLocalDateSerializer())
                .deserializerByType(LocalDate.class, new FlexibleLocalDateDeserializer());
    }

    /** API 日期统一输出 ISO-8601。 */
    class IsoLocalDateSerializer extends JsonSerializer<LocalDate> {
        @Override
        public void serialize(LocalDate value, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            gen.writeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
    }

    /** API 日期时间统一输出带时区偏移的 ISO-8601。 */
    class IsoLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen,
                              SerializerProvider provider) throws IOException {
            gen.writeString(value.atZone(zone).toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    /** Deserialize LocalDateTime from epoch millis (number) or "yyyy-MM-dd HH:mm:ss" (string). */
    class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FMT = DateTimeFormatSupport.DATE_TIME_FORMATTER;

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                long epoch = p.getLongValue();
                return Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime();
            }
            String text = p.getText().trim();
            try {
                return java.time.OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .atZoneSameInstant(zone)
                        .toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // Continue with local and legacy representations.
            }
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                // Continue with the legacy representation.
            }
            try {
                return LocalDateTime.parse(text, FMT);
            } catch (DateTimeParseException e) {
                long epoch = Long.parseLong(text);
                return Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime();
            }
        }
    }

    /** Deserialize LocalDate from epoch millis or "yyyy-MM-dd" or "yyyy-MM-dd HH:mm:ss". */
    class FlexibleLocalDateDeserializer extends JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.VALUE_NUMBER_INT) {
                long epoch = p.getLongValue();
                return Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate();
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
                return Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate();
            }
        }
    }

}
