package com.leo.erp.common.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneMaskSerializerTest {

    private final PhoneMaskSerializer serializer = new PhoneMaskSerializer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldMaskNormalPhone() throws IOException {
        String result = serialize("13812345678");
        assertThat(result).isEqualTo("138****5678");
    }

    @Test
    void shouldMaskShortPhone() throws IOException {
        String result = serialize("12345");
        assertThat(result).isEqualTo("123****");
    }

    @Test
    void shouldReturnOriginalWhenLengthIsFourOrLess() throws IOException {
        String result = serialize("1234");
        assertThat(result).isEqualTo("1234");
    }

    @Test
    void shouldReturnOriginalWhenLengthIsThree() throws IOException {
        String result = serialize("123");
        assertThat(result).isEqualTo("123");
    }

    @Test
    void shouldHandleNullValue() throws IOException {
        String result = serialize(null);
        assertThat(result).isEqualTo("null");
    }

    @Test
    void shouldHandleEmptyString() throws IOException {
        String result = serialize("");
        assertThat(result).isEqualTo("");
    }

    @Test
    void shouldHandleBlankString() throws IOException {
        String result = serialize("   ");
        assertThat(result).isEqualTo("   ");
    }

    private String serialize(String value) throws IOException {
        StringWriter writer = new StringWriter();
        JsonGenerator gen = objectMapper.getFactory().createGenerator(writer);
        SerializerProvider provider = objectMapper.getSerializerProvider();
        serializer.serialize(value, gen, provider);
        gen.flush();
        return writer.toString().replace("\"", "");
    }
}
