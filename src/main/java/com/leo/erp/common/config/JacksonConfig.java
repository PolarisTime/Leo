package com.leo.erp.common.config;

import com.leo.erp.common.json.ScaledBigDecimalSerializer;
import com.leo.erp.common.support.DateTimeFormatSupport;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(BigDecimal.class, new ScaledBigDecimalSerializer())
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatSupport.DATE_TIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatSupport.DATE_TIME_FORMATTER))
                .serializerByType(OffsetDateTime.class, new OffsetDateTimeSerializer(
                        OffsetDateTimeSerializer.INSTANCE,
                        Boolean.FALSE,
                        DateTimeFormatSupport.DATE_TIME_FORMATTER,
                        null
                ))
                .deserializerByType(LocalDate.class, new FlexibleLocalDateDeserializer())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Accepts both "yyyy-MM-dd" and "yyyy-MM-dd HH:mm:ss" to handle frontend datetime overflow. */
    static class FlexibleLocalDateDeserializer extends com.fasterxml.jackson.databind.JsonDeserializer<LocalDate> {
        private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ISO_LOCAL_DATE;
        private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public LocalDate deserialize(com.fasterxml.jackson.core.JsonParser p,
                                      com.fasterxml.jackson.databind.DeserializationContext ctxt) throws java.io.IOException {
            String text = p.getText().trim();
            if (text.length() > 10) {
                try {
                    return LocalDate.parse(text, DATE_TIME);
                } catch (DateTimeParseException e) {
                    // fall through to try date-only
                }
            }
            return LocalDate.parse(text, DATE_ONLY);
        }
    }
}
