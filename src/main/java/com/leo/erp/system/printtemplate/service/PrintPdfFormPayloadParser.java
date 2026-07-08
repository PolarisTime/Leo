package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PrintPdfFormPayloadParser {

    private final PrintPdfFormTemplateValidator templateValidator;

    public PrintPdfFormPayloadParser(PrintPdfFormTemplateValidator templateValidator) {
        this.templateValidator = templateValidator;
    }

    PrintPdfFormPayload parse(Map<String, Object> payload) {
        String templateType = String.valueOf(payload.getOrDefault("templateType", ""));
        if (!"PDF_FORM".equals(templateType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前模板不是 PDF_FORM 类型");
        }
        Map<String, List<Map<String, String>>> sections = sections(payload.get("sections"));
        List<Map<String, String>> items = payload.containsKey("items")
                ? items(payload.getOrDefault("items", Collections.emptyList()))
                : sections.getOrDefault(PrintRecordData.ITEMS_SECTION, List.of());
        List<Map<String, String>> chargeItems = payload.containsKey("chargeItems")
                ? items(payload.getOrDefault("chargeItems", Collections.emptyList()))
                : sections.getOrDefault(PrintRecordData.CHARGE_ITEMS_SECTION, List.of());
        sections = new LinkedHashMap<>(sections);
        sections.put(PrintRecordData.ITEMS_SECTION, items);
        sections.put(PrintRecordData.CHARGE_ITEMS_SECTION, chargeItems);
        return new PrintPdfFormPayload(
                templateValidator.validate(String.valueOf(payload.getOrDefault("templateHtml", ""))),
                data(payload.getOrDefault("data", Collections.emptyMap())),
                items,
                chargeItems,
                sections
        );
    }

    private Map<String, String> data(Object rawData) {
        if (!(rawData instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                result.put(key, value);
            }
        }
        return result;
    }

    private List<Map<String, String>> items(Object rawItems) {
        if (!(rawItems instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(this::data)
                .toList();
    }

    private Map<String, List<Map<String, String>>> sections(Object rawSections) {
        if (!(rawSections instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof List<?>) {
                result.put(key, items(entry.getValue()));
            }
        }
        return result;
    }
}
