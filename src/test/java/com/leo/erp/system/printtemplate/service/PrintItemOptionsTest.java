package com.leo.erp.system.printtemplate.service;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PrintItemOptionsTest {

    @Test
    void shouldReturnDefaultsForUnsupportedRawOptions() {
        assertThat(PrintItemOptions.from(null)).isEqualTo(PrintItemOptions.defaults());
        assertThat(PrintItemOptions.from("unsupported")).isEqualTo(PrintItemOptions.defaults());
    }

    @Test
    void shouldNormalizeRawMapsAndLists() {
        Map<Object, Object> brandOverrides = new LinkedHashMap<>();
        brandOverrides.put(" 抚顺新钢 ", " 抚新 ");
        brandOverrides.put("ignored", 1);
        Map<Object, Object> itemOverrides = new LinkedHashMap<>();
        itemOverrides.put(" 11 ", " 沙钢 ");
        itemOverrides.put(12, "ignored");
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("brandOverride", " 默认 ");
        raw.put("brandOverrides", brandOverrides);
        raw.put("brandOverridesByItemId", itemOverrides);
        raw.put("itemOrder", java.util.Arrays.asList(" 12 ", null, "11", "12", " "));

        PrintItemOptions options = PrintItemOptions.from(raw);

        assertThat(options.brandOverride()).isEqualTo("默认");
        assertThat(options.brandOverrides()).containsExactly(Map.entry("抚顺新钢", "抚新"));
        assertThat(options.brandOverridesByItemId()).containsExactly(Map.entry("11", "沙钢"));
        assertThat(options.itemOrder()).containsExactly("12", "11");
    }

    @Test
    void shouldKeepFirstRawMapValueWhenDuplicateKeysAreEncountered() {
        PrintItemOptions options = PrintItemOptions.from(Map.of(
                "brandOverrides",
                duplicateEntryMap(
                        entry("brand", "left"),
                        entry("brand", "right")
                )
        ));

        assertThat(options.brandOverrides()).containsExactly(Map.entry("brand", "left"));
    }

    @Test
    void shouldDropBlankNormalizedMapEntriesAndHandleNullList() {
        Map<String, String> brandOverrides = new LinkedHashMap<>();
        brandOverrides.put(" ", "value");
        brandOverrides.put("key", " ");
        brandOverrides.put(" a ", " b ");
        brandOverrides.put("a", "later");
        PrintItemOptions options = new PrintItemOptions(
                null,
                brandOverrides,
                null,
                null
        );

        assertThat(options.brandOverride()).isEmpty();
        assertThat(options.brandOverrides()).containsExactly(Map.entry("a", "b"));
        assertThat(options.brandOverridesByItemId()).isEmpty();
        assertThat(options.itemOrder()).isEmpty();
    }

    @Test
    void shouldUseEmptyCollectionsWhenRawMapAndRawListAreUnsupported() {
        PrintItemOptions options = PrintItemOptions.from(Map.of(
                "brandOverride", 42,
                "brandOverrides", List.of("ignored"),
                "brandOverridesByItemId", "ignored",
                "itemOrder", "ignored"
        ));

        assertThat(options.brandOverride()).isEmpty();
        assertThat(options.brandOverrides()).isEmpty();
        assertThat(options.brandOverridesByItemId()).isEmpty();
        assertThat(options.itemOrder()).isEmpty();
    }

    @SafeVarargs
    private static Map<Object, Object> duplicateEntryMap(Map.Entry<Object, Object>... entries) {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<Object, Object>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public Iterator<Entry<Object, Object>> iterator() {
                        return List.of(entries).iterator();
                    }

                    @Override
                    public int size() {
                        return entries.length;
                    }
                };
            }
        };
    }

    private static Map.Entry<Object, Object> entry(Object key, Object value) {
        return Map.entry(key, value);
    }
}
