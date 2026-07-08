package com.leo.erp.system.printtemplate.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record PrintRecordData(
        Map<String, String> data,
        Map<String, List<Map<String, String>>> sections
) {
    static final String ITEMS_SECTION = "items";
    static final String CHARGE_ITEMS_SECTION = "chargeItems";

    PrintRecordData {
        sections = normalizeSections(sections);
    }

    PrintRecordData(Map<String, String> data, List<Map<String, String>> items) {
        this(data, Map.of(ITEMS_SECTION, items == null ? List.of() : items));
    }

    List<Map<String, String>> items() {
        return section(ITEMS_SECTION);
    }

    List<Map<String, String>> chargeItems() {
        return section(CHARGE_ITEMS_SECTION);
    }

    List<Map<String, String>> section(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        return sections.getOrDefault(key, List.of());
    }

    private static Map<String, List<Map<String, String>>> normalizeSections(
            Map<String, List<Map<String, String>>> rawSections
    ) {
        Map<String, List<Map<String, String>>> normalized = new LinkedHashMap<>();
        if (rawSections != null) {
            rawSections.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    normalized.put(key, List.copyOf(value));
                }
            });
        }
        normalized.putIfAbsent(ITEMS_SECTION, List.of());
        normalized.putIfAbsent(CHARGE_ITEMS_SECTION, List.of());
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }
}
