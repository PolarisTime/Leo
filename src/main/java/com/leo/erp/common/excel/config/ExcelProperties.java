package com.leo.erp.common.excel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "leo.excel")
public class ExcelProperties {

    private int maxExportRows = 10_000;

    /**
     * Excel 日期列的展示格式码。
     *
     * <p>中文年/月/日是字面量，POI 数字格式码必须用双引号转义，否则 {@code y}/{@code m}/{@code d}
     * 会被当成日期占位符导致格式无效。</p>
     */
    private String defaultDateFormat = "yyyy\"年\"MM\"月\"dd\"日\"";

    private long maxFileSize = 10 * 1024 * 1024;

    private Set<String> allowedExtensions = Set.of("xlsx", "xls", "csv");

    private int importBatchSize = 500;

    public int getMaxExportRows() {
        return maxExportRows;
    }

    public void setMaxExportRows(int maxExportRows) {
        this.maxExportRows = maxExportRows;
    }

    public String getDefaultDateFormat() {
        return defaultDateFormat;
    }

    public void setDefaultDateFormat(String defaultDateFormat) {
        this.defaultDateFormat = defaultDateFormat;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Set<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public int getImportBatchSize() {
        return importBatchSize;
    }

    public void setImportBatchSize(int importBatchSize) {
        this.importBatchSize = importBatchSize;
    }
}
