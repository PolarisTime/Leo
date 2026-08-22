package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
class MaterialCsvFileReader {

    private static final CSVFormat MATERIAL_CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setRecordSeparator("\r\n")
            .build();

    CsvTable read(byte[] raw) throws IOException {
        String content = decodeAndStripBom(raw, StandardCharsets.UTF_8);
        List<List<String>> rows = parse(content);
        if (!rows.isEmpty() && !hasKnownHeaders(rows.getFirst())) {
            content = decodeAndStripBom(raw, Charset.forName("GBK"));
            rows = parse(content);
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入文件不能为空");
        }
        return new CsvTable(rows, buildHeaderIndexes(rows.getFirst()));
    }

    private Map<String, Integer> buildHeaderIndexes(List<String> headerRow) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            indexes.put(normalizeHeader(headerRow.get(index)), index);
        }
        requireHeader(indexes, "materialCode", "商品编码");
        requireHeader(indexes, "brand", "品牌");
        requireHeader(indexes, "material", "材质");
        requireHeader(indexes, "category", "类别");
        requireHeader(indexes, "spec", "规格");
        requireHeader(indexes, "unit", "单位");
        requireHeader(indexes, "pieceWeightTon", "件重(吨)");
        requireHeader(indexes, "piecesPerBundle", "每件支数");
        requireHeader(indexes, "unitPrice", "单价");
        return indexes;
    }

    private void requireHeader(Map<String, Integer> indexes, String key, String label) {
        if (!indexes.containsKey(key)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入模板缺少列：" + label);
        }
    }

    private String normalizeHeader(String header) {
        String raw = header == null ? "" : header;
        if (!raw.isEmpty() && raw.charAt(0) == '﻿') {
            raw = raw.substring(1);
        }
        String value = raw.replace(" ", "").trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "商品编码", "materialcode" -> "materialCode";
            case "品牌", "brand" -> "brand";
            case "材质", "material" -> "material";
            case "类别", "category" -> "category";
            case "规格", "spec" -> "spec";
            case "长度", "length" -> "length";
            case "单位", "unit" -> "unit";
            case "数量单位", "quantityunit" -> "quantityUnit";
            case "件重(吨)", "件重", "pieceweightton" -> "pieceWeightTon";
            case "每件支数", "piecesperbundle" -> "piecesPerBundle";
            case "单价", "unitprice" -> "unitPrice";
            case "备注", "remark" -> "remark";
            case "商品类型", "materialtype" -> "materialType";
            default -> value;
        };
    }

    private String decodeAndStripBom(byte[] raw, Charset charset) {
        String content = new String(raw, charset);
        if (!content.isEmpty() && content.charAt(0) == '﻿') {
            return content.substring(1);
        }
        return content;
    }

    private boolean hasKnownHeaders(List<String> headerRow) {
        for (String header : headerRow) {
            String normalized = normalizeHeader(header);
            String plain = header.replace(" ", "").trim().toLowerCase(Locale.ROOT);
            if (!normalized.equals(plain)) {
                return true;
            }
        }
        return false;
    }

    private List<List<String>> parse(String content) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(content, MATERIAL_CSV_FORMAT)) {
            for (CSVRecord record : parser) {
                rows.add(List.copyOf(Arrays.asList(record.values())));
            }
        }
        return rows;
    }

    record CsvTable(List<List<String>> rows, Map<String, Integer> headerIndexes) {
    }
}
