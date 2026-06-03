package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.excel.annotation.ImportColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelTemplateServiceTest {

    private final ExcelTemplateService service = new ExcelTemplateService();

    @Test
    void shouldGenerateTemplateBytes() {
        byte[] template = service.generateTemplate(TestTemplateDto.class);

        assertThat(template).isNotEmpty();
    }

    @Test
    void shouldThrow_whenDtoHasNoAnnotations() {
        assertThatThrownBy(() -> service.generateTemplate(NoAnnotationDto.class))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少 @ImportColumn 注解");
    }

    @Test
    void shouldResolveFields() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(TestTemplateDto.class);

        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).header()).isEqualTo("名称");
        assertThat(fields.get(1).header()).isEqualTo("数量");
    }

    @Test
    void shouldResolveFieldsWithEnumValues() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(EnumTemplateDto.class);

        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).enumValues()).containsExactly("A", "B", "C");
    }

    @Test
    void shouldBuildFormatDescription() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(RequiredRegexDto.class);

        assertThat(fields.get(0).required()).isTrue();
        assertThat(fields.get(0).regex()).isNotBlank();
    }

    @Test
    void shouldHandleEmptyFields() {
        List<ExcelTemplateService.TemplateField> fields = service.resolveFields(NoAnnotationDto.class);

        assertThat(fields).isEmpty();
    }

    record TestTemplateDto(
            @ImportColumn(header = "名称", required = true, example = "商品名称", order = 1)
            String name,
            @ImportColumn(header = "数量", example = "100", order = 2)
            Integer quantity
    ) {}

    record EnumTemplateDto(
            @ImportColumn(header = "类型", enumValues = {"A", "B", "C"}, order = 1)
            String type
    ) {}

    record RequiredRegexDto(
            @ImportColumn(header = "编码", required = true, regex = "^[A-Z]+$", order = 1)
            String code
    ) {}

    record NoAnnotationDto(String name) {}
}
