package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.annotation.JsonInclude;

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
        java.util.List<Map<String, String>> items
) {

    public enum Kind {
        PDF,
        LODOP_SCRIPT
    }

    public static PrintOutput fromPayload(Map<String, Object> payload) {
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
                stringMapList(payload.get("items"))
        );
    }

    public static PrintOutput pdf(Map<String, Object> payload, String pdfBase64, String contentType, String fileName) {
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
                null,
                null
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

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Map<String, String>> stringMapList(Object value) {
        return value instanceof java.util.List<?> list ? (java.util.List<Map<String, String>>) list : null;
    }
}
