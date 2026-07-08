package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrintOutput(
        Kind kind,
        String templateName,
        String templateType,
        String contentType,
        String fileName,
        String pdfBase64,
        String businessNo,
        Long recordId,
        String moduleKey,
        String templateHtml,
        Map<String, String> data,
        List<Map<String, String>> items,
        List<Map<String, String>> chargeItems,
        Map<String, List<Map<String, String>>> sections
) {

    public enum Kind {
        PDF,
        LODOP_SCRIPT
    }

    public PrintOutput(
            Kind kind,
            String templateName,
            String templateType,
            String contentType,
            String fileName,
            String pdfBase64,
            String businessNo,
            Long recordId,
            String moduleKey,
            String templateHtml,
            Map<String, String> data,
            List<Map<String, String>> items
    ) {
        this(kind, templateName, templateType, contentType, fileName, pdfBase64, businessNo, recordId, moduleKey,
                templateHtml, data, items, null, null);
    }

    public static PrintOutput fromPayload(Map<String, Object> payload) {
        List<Map<String, String>> items = stringMapList(payload.get("items"));
        List<Map<String, String>> chargeItems = stringMapList(payload.get("chargeItems"));
        Map<String, List<Map<String, String>>> sections = sections(payload.get("sections"));
        sections = mergeSections(sections, items, chargeItems);
        return new PrintOutput(
                Kind.LODOP_SCRIPT,
                stringValue(payload.get("templateName")),
                stringValue(payload.getOrDefault("templateType", "COORD")),
                null,
                null,
                null,
                stringValue(payload.get("businessNo")),
                longValue(payload.get("recordId")),
                stringValue(payload.get("moduleKey")),
                stringValue(payload.get("templateHtml")),
                stringMap(payload.get("data")),
                items,
                chargeItems,
                sections
        );
    }

    public static PrintOutput pdf(Map<String, Object> payload, String pdfBase64, String contentType, String fileName) {
        List<Map<String, String>> items = stringMapList(payload.get("items"));
        List<Map<String, String>> chargeItems = stringMapList(payload.get("chargeItems"));
        Map<String, List<Map<String, String>>> sections = mergeSections(
                sections(payload.get("sections")),
                items,
                chargeItems
        );
        return new PrintOutput(
                Kind.PDF,
                stringValue(payload.get("templateName")),
                "PDF_FORM",
                contentType,
                fileName,
                pdfBase64,
                stringValue(payload.get("businessNo")),
                longValue(payload.get("recordId")),
                stringValue(payload.get("moduleKey")),
                null,
                stringMap(payload.get("data")),
                items,
                chargeItems,
                sections
        );
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key
                    && entry.getValue() instanceof String text) {
                result.put(key, text);
            }
        }
        return result;
    }

    private static List<Map<String, String>> stringMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(PrintOutput::stringMap)
                .toList();
    }

    private static Map<String, List<Map<String, String>>> sections(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, List<Map<String, String>>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                List<Map<String, String>> rows = stringMapList(entry.getValue());
                if (rows != null) {
                    result.put(key, rows);
                }
            }
        }
        return result;
    }

    private static Map<String, List<Map<String, String>>> mergeSections(
            Map<String, List<Map<String, String>>> sections,
            List<Map<String, String>> items,
            List<Map<String, String>> chargeItems
    ) {
        Map<String, List<Map<String, String>>> result = sections == null ? new LinkedHashMap<>() : new LinkedHashMap<>(sections);
        if (items != null) {
            result.put(PrintRecordData.ITEMS_SECTION, items);
        }
        if (chargeItems != null) {
            result.put(PrintRecordData.CHARGE_ITEMS_SECTION, chargeItems);
        }
        return result.isEmpty() ? null : result;
    }
}
