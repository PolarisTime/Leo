package com.leo.erp.common.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScaledBigDecimalSerializerTest {

    @Test
    void serialize_nullValue_writesNull() throws IOException {
        ScaledBigDecimalSerializer serializer = new ScaledBigDecimalSerializer();
        StringWriter writer = new StringWriter();
        JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
        SerializerProvider provider = new ObjectMapper().getSerializerProvider();

        serializer.serialize(null, gen, provider);
        gen.flush();

        assertThat(writer.toString()).isEqualTo("null");
    }

    @Test
    void serialize_valueWithoutScale_writesPlainString() throws IOException {
        ScaledBigDecimalSerializer serializer = new ScaledBigDecimalSerializer();
        StringWriter writer = new StringWriter();
        JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
        SerializerProvider provider = new ObjectMapper().getSerializerProvider();

        serializer.serialize(new BigDecimal("123.456"), gen, provider);
        gen.flush();

        assertThat(writer.toString()).isEqualTo("123.456");
    }

    @Test
    void createContextual_withNullProperty_returnsSelf() throws JsonMappingException {
        ScaledBigDecimalSerializer serializer = new ScaledBigDecimalSerializer();
        SerializerProvider provider = new ObjectMapper().getSerializerProvider();

        JsonSerializer<?> result = serializer.createContextual(provider, null);
        assertThat(result).isSameAs(serializer);
    }

    @Test
    void weightField_usesWeightScale() throws Exception {
        ObjectMapper mapper = buildMapperWithSerializer();

        String json = mapper.writeValueAsString(new WeightHolder(new BigDecimal("1.23456")));
        assertThat(json).contains("1.235");
    }

    @Test
    void amountField_usesScale2() throws Exception {
        ObjectMapper mapper = buildMapperWithSerializer();

        String json = mapper.writeValueAsString(new AmountHolder(new BigDecimal("123.456")));
        assertThat(json).contains("123.46");
    }

    @Test
    void priceField_usesScale2() throws Exception {
        ObjectMapper mapper = buildMapperWithSerializer();

        String json = mapper.writeValueAsString(new PriceHolder(new BigDecimal("99.999")));
        assertThat(json).contains("100.00");
    }

    @Test
    void freightField_usesScale2() throws Exception {
        ObjectMapper mapper = buildMapperWithSerializer();

        String json = mapper.writeValueAsString(new FreightHolder(new BigDecimal("55.555")));
        assertThat(json).contains("55.56");
    }

    @Test
    void unknownField_usesNoScale() throws Exception {
        ObjectMapper mapper = buildMapperWithSerializer();

        String json = mapper.writeValueAsString(new QuantityHolder(new BigDecimal("10.12345")));
        assertThat(json).contains("10.12345");
    }

    @Test
    void serialize_zero_writesZero() throws IOException {
        ScaledBigDecimalSerializer serializer = new ScaledBigDecimalSerializer();
        StringWriter writer = new StringWriter();
        JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
        SerializerProvider provider = new ObjectMapper().getSerializerProvider();

        serializer.serialize(BigDecimal.ZERO, gen, provider);
        gen.flush();

        assertThat(writer.toString()).isEqualTo("0");
    }

    @Test
    void serialize_negativeValue_writesNegative() throws IOException {
        ScaledBigDecimalSerializer serializer = new ScaledBigDecimalSerializer();
        StringWriter writer = new StringWriter();
        JsonGenerator gen = new ObjectMapper().getFactory().createGenerator(writer);
        SerializerProvider provider = new ObjectMapper().getSerializerProvider();

        serializer.serialize(new BigDecimal("-99.99"), gen, provider);
        gen.flush();

        assertThat(writer.toString()).isEqualTo("-99.99");
    }

    private ObjectMapper buildMapperWithSerializer() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(BigDecimal.class, new ScaledBigDecimalSerializer());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
    }

    static class WeightHolder {
        @JsonSerialize(using = ScaledBigDecimalSerializer.class)
        BigDecimal netWeight;

        WeightHolder(BigDecimal netWeight) { this.netWeight = netWeight; }
        public BigDecimal getNetWeight() { return netWeight; }
    }

    static class AmountHolder {
        @JsonSerialize(using = ScaledBigDecimalSerializer.class)
        BigDecimal totalAmount;

        AmountHolder(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }

    static class PriceHolder {
        @JsonSerialize(using = ScaledBigDecimalSerializer.class)
        BigDecimal unitPrice;

        PriceHolder(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getUnitPrice() { return unitPrice; }
    }

    static class FreightHolder {
        @JsonSerialize(using = ScaledBigDecimalSerializer.class)
        BigDecimal freight;

        FreightHolder(BigDecimal freight) { this.freight = freight; }
        public BigDecimal getFreight() { return freight; }
    }

    static class QuantityHolder {
        @JsonSerialize(using = ScaledBigDecimalSerializer.class)
        BigDecimal quantity;

        QuantityHolder(BigDecimal quantity) { this.quantity = quantity; }
        public BigDecimal getQuantity() { return quantity; }
    }
}
