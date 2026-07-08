package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record PrintPdfFormPayload(
        JsonNode root,
        Map<String, String> data,
        List<Map<String, String>> items,
        List<Map<String, String>> chargeItems,
        Map<String, List<Map<String, String>>> sections
) {
    PrintPdfFormPayload {
        sections = normalizeSections(sections, items, chargeItems);
        items = sections.get(PrintRecordData.ITEMS_SECTION);
        chargeItems = sections.get(PrintRecordData.CHARGE_ITEMS_SECTION);
    }

    PrintPdfFormPayload(JsonNode root, Map<String, String> data, List<Map<String, String>> items) {
        this(root, data, items, List.of(), Map.of(PrintRecordData.ITEMS_SECTION, items == null ? List.of() : items));
    }

    List<Map<String, String>> section(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        return sections.getOrDefault(key, List.of());
    }

    private static Map<String, List<Map<String, String>>> normalizeSections(
            Map<String, List<Map<String, String>>> rawSections,
            List<Map<String, String>> items,
            List<Map<String, String>> chargeItems
    ) {
        Map<String, List<Map<String, String>>> normalized = new LinkedHashMap<>();
        if (rawSections != null) {
            rawSections.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    normalized.put(key, List.copyOf(value));
                }
            });
        }
        normalized.put(PrintRecordData.ITEMS_SECTION, items == null ? List.of() : List.copyOf(items));
        normalized.put(PrintRecordData.CHARGE_ITEMS_SECTION, chargeItems == null ? List.of() : List.copyOf(chargeItems));
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }
}
