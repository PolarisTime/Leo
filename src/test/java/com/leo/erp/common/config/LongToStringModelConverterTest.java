package com.leo.erp.common.config;

import com.leo.erp.common.api.PageResponse;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.sales.contract.web.dto.SalesContractResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 Long/long 在 OpenAPI schema 中被标注为 string/int64，且不影响其它类型。
 */
class LongToStringModelConverterTest {

    private ModelConverters converters() {
        ModelConverters modelConverters = new ModelConverters();
        modelConverters.addConverter(new LongToStringModelConverter());
        return modelConverters;
    }

    private Schema<?> resolveSchema(Class<?> type) {
        return converters().readAllAsResolvedSchema(new AnnotatedType(type)).schema;
    }

    private Schema<?> resolveReferenced(Class<?> type, String name) {
        ResolvedSchema resolved = converters().readAllAsResolvedSchema(new AnnotatedType(type));
        return resolved.referencedSchemas.get(name);
    }

    private void assertStringInt64(Schema<?> schema) {
        assertThat(schema).isNotNull();
        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getFormat()).isEqualTo("int64");
    }

    @Test
    void quoteSheetResponseLongFieldsBecomeStrings() {
        Schema<?> schema = resolveSchema(QuoteSheetResponse.class);
        assertStringInt64(schema.getProperties().get("id"));
        assertStringInt64(schema.getProperties().get("projectId"));
        assertStringInt64(schema.getProperties().get("version"));

        Schema<?> brand = resolveReferenced(QuoteSheetResponse.class, "BrandResponse");
        assertStringInt64(brand.getProperties().get("id"));

        Schema<?> item = resolveReferenced(QuoteSheetResponse.class, "ItemResponse");
        assertStringInt64(item.getProperties().get("id"));

        Schema<?> price = resolveReferenced(QuoteSheetResponse.class, "ItemPriceResponse");
        assertStringInt64(price.getProperties().get("id"));
        assertStringInt64(price.getProperties().get("supplierId"));
    }

    @Test
    void salesContractResponseLongFieldsBecomeStrings() {
        Schema<?> schema = resolveSchema(SalesContractResponse.class);
        assertStringInt64(schema.getProperties().get("id"));
        assertStringInt64(schema.getProperties().get("customerId"));
        assertStringInt64(schema.getProperties().get("projectId"));
        assertStringInt64(schema.getProperties().get("version"));
    }

    @Test
    void pageResponseTotalElementsBecomesString() {
        Schema<?> schema = resolveSchema(PageResponse.class);
        assertStringInt64(schema.getProperties().get("totalElements"));
        assertThat(schema.getProperties().get("totalPages").getType()).isEqualTo("integer");
        assertThat(schema.getProperties().get("currentPage").getType()).isEqualTo("integer");
    }

    @Test
    void genericContainerElementsBecomeStrings() {
        Schema<?> schema = resolveSchema(LongCoverageSample.class);

        assertStringInt64(schema.getProperties().get("id"));
        assertStringInt64(schema.getProperties().get("primitiveId"));
        assertStringInt64(schema.getProperties().get("optionalId"));

        Schema<?> idList = schema.getProperties().get("idList");
        assertThat(idList.getType()).isEqualTo("array");
        assertStringInt64(idList.getItems());

        Schema<?> idArray = schema.getProperties().get("idArray");
        assertThat(idArray.getType()).isEqualTo("array");
        assertStringInt64(idArray.getItems());

        Schema<?> idMap = schema.getProperties().get("idMap");
        assertThat(idMap.getType()).isEqualTo("object");
        assertStringInt64((Schema<?>) idMap.getAdditionalProperties());
    }

    @Test
    void otherNumericAndTemporalTypesAreUnchanged() {
        Schema<?> schema = resolveSchema(LongCoverageSample.class);

        Schema<?> count = schema.getProperties().get("count");
        assertThat(count.getType()).isEqualTo("integer");
        assertThat(count.getFormat()).isEqualTo("int32");

        Schema<?> shortCount = schema.getProperties().get("shortCount");
        assertThat(shortCount.getType()).isEqualTo("integer");
        assertThat(shortCount.getFormat()).isNotEqualTo("int64");

        Schema<?> amount = schema.getProperties().get("amount");
        assertThat(amount.getType()).isEqualTo("number");

        Schema<?> date = schema.getProperties().get("date");
        assertThat(date.getType()).isEqualTo("string");
        assertThat(date.getFormat()).isEqualTo("date");

        Schema<?> dateTime = schema.getProperties().get("dateTime");
        assertThat(dateTime.getType()).isEqualTo("string");
        assertThat(dateTime.getFormat()).isEqualTo("date-time");

        Schema<?> flag = schema.getProperties().get("flag");
        assertThat(flag.getType()).isEqualTo("boolean");
    }

    @Test
    void converterIsIdempotent() {
        ModelConverters modelConverters = converters();
        ResolvedSchema first = modelConverters.readAllAsResolvedSchema(new AnnotatedType(SalesContractResponse.class));
        modelConverters.addConverter(new LongToStringModelConverter());
        ResolvedSchema second = modelConverters.readAllAsResolvedSchema(new AnnotatedType(SalesContractResponse.class));

        assertStringInt64((Schema<?>) first.schema.getProperties().get("id"));
        assertStringInt64((Schema<?>) second.schema.getProperties().get("id"));
    }

    record LongCoverageSample(
            Long id,
            long primitiveId,
            Integer count,
            Short shortCount,
            Byte byteCount,
            BigDecimal amount,
            LocalDate date,
            LocalDateTime dateTime,
            boolean flag,
            List<Long> idList,
            Long[] idArray,
            Map<String, Long> idMap,
            Optional<Long> optionalId
    ) {
    }
}
