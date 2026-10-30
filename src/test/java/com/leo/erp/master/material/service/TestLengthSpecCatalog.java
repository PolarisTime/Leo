package com.leo.erp.master.material.service;

import com.leo.erp.common.api.LengthSpecCatalog;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定尺目录测试替身：{@link #empty()} 模拟「定尺主数据尚未维护」（校验整体放行），
 * {@link #of} 模拟已维护的定尺名称，{@link #withSuffixes} 额外带打印后缀。
 */
final class TestLengthSpecCatalog implements LengthSpecCatalog {

    private final Set<String> names;
    private final Map<String, String> suffixes;

    private TestLengthSpecCatalog(List<String> names, Map<String, String> suffixes) {
        this.names = new LinkedHashSet<>(names);
        this.suffixes = new LinkedHashMap<>(suffixes);
    }

    static TestLengthSpecCatalog empty() {
        return new TestLengthSpecCatalog(List.of(), Map.of());
    }

    static TestLengthSpecCatalog of(String... names) {
        return new TestLengthSpecCatalog(Arrays.asList(names), Map.of());
    }

    static TestLengthSpecCatalog withSuffixes(String... lengthToSuffixPairs) {
        Map<String, String> suffixes = new LinkedHashMap<>();
        for (int index = 0; index + 1 < lengthToSuffixPairs.length; index += 2) {
            suffixes.put(lengthToSuffixPairs[index], lengthToSuffixPairs[index + 1]);
        }
        return new TestLengthSpecCatalog(List.copyOf(suffixes.keySet()), suffixes);
    }

    @Override
    public Set<String> activeLengthNames() {
        return names;
    }

    @Override
    public Map<String, String> activeSpecSuffixMap() {
        return suffixes;
    }
}
