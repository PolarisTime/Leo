package com.leo.erp.system.printtemplate.service;

import com.leo.erp.master.api.ProjectQuery;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PrintExportFilenameService {

    private static final String EMPTY_COMPONENT = "-";
    private static final int BUSINESS_NO_MAX_LENGTH = 64;
    // 按 UTF-8 字节计："2026年08月10日" 为 17 字节，取 32 留余量
    private static final int DATE_MAX_LENGTH = 32;
    private static final int PROJECT_MAX_LENGTH = 72;
    private static final int COMPANY_MAX_LENGTH = 72;
    private static final int DATE_PART_COUNT = 3;
    private static final int YEAR_INDEX = 0;
    private static final int MONTH_INDEX = 1;
    private static final int DAY_INDEX = 2;
    private static final int CONTROL_CHARACTER_LIMIT = 32;
    private static final DateTimeFormatter PDF_FILENAME_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.ROOT);

    private final ProjectQuery projectQuery;
    private final PrintRuntimeProperties runtimeProperties;

    public PrintExportFilenameService(
            ProjectQuery projectQuery,
            PrintRuntimeProperties runtimeProperties
    ) {
        this.projectQuery = projectQuery;
        this.runtimeProperties = runtimeProperties;
    }

    public String fromPrintData(Map<?, ?> data, String fallbackBusinessNo, String extension) {
        Map<?, ?> printData = data == null ? Map.of() : data;
        String businessNo = firstPresent(
                printData,
                runtimeProperties.childTextValues(runtimeProperties.topLevelFields().path("businessNoKeys"))
        );
        if (businessNo.isBlank()) {
            businessNo = normalize(fallbackBusinessNo);
        }
        String businessDate = firstPresent(
                printData,
                runtimeProperties.childTextValues(
                        runtimeProperties.topLevelFields().path("dateParts").path("sourceKeys")
                )
        );
        String projectName = firstPresent(printData, List.of("projectName"));
        String projectShortName = firstPresent(printData, List.of("projectShortName", "projectNameAbbr"));
        if (projectShortName.isBlank()) {
            projectShortName = resolveProjectShortName(longValue(printData.get("projectId")), projectName);
        }
        return build(
                businessNo,
                formatPdfDate(businessDate),
                projectShortName,
                firstPresent(printData, List.of("settlementCompanyName")),
                extension
        );
    }

    public String forOrder(
            String orderNo,
            LocalDate businessDate,
            Long projectId,
            String projectName,
            String settlementCompanyName,
            String extension
    ) {
        return build(
                orderNo,
                businessDate == null ? "" : businessDate.toString(),
                resolveProjectShortName(projectId, projectName),
                settlementCompanyName,
                extension
        );
    }

    private String build(
            String businessNo,
            String businessDate,
            String projectShortName,
            String settlementCompanyName,
            String extension
    ) {
        String filename = String.join(
                ".",
                safeComponent(businessNo, BUSINESS_NO_MAX_LENGTH),
                safeComponent(businessDate, DATE_MAX_LENGTH),
                safeComponent(projectShortName, PROJECT_MAX_LENGTH),
                safeComponent(settlementCompanyName, COMPANY_MAX_LENGTH)
        );
        String safeExtension = normalize(extension)
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);
        return safeExtension.isBlank() ? filename : filename + "." + safeExtension;
    }

    private String resolveProjectShortName(Long projectId, String projectName) {
        if (projectId == null) {
            return normalize(projectName);
        }
        return projectQuery.findActiveById(projectId)
                .map(ProjectQuery.ProjectSnapshot::abbreviatedName)
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .orElseGet(() -> normalize(projectName));
    }

    private String firstPresent(Map<?, ?> data, List<String> keys) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        for (String key : keys) {
            String value = normalize(data.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String formatPdfDate(String rawDate) {
        String value = normalize(rawDate);
        if (value.isBlank()) {
            return value;
        }
        String datePart = value.split("[T\\s]", 2)[0];
        String[] parts = datePart.split("[-/年/月日]");
        if (parts.length < DATE_PART_COUNT) {
            return datePart;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(parts[YEAR_INDEX]),
                    Integer.parseInt(parts[MONTH_INDEX]),
                    Integer.parseInt(parts[DAY_INDEX])
            ).format(PDF_FILENAME_DATE_FORMATTER);
        } catch (RuntimeException ignored) {
            return datePart;
        }
    }

    private String safeComponent(String rawValue, int maxLength) {
        String value = normalize(rawValue);
        // 按 UTF-8 字节数截断，兼容 Linux 文件系统 255 字节上限（中文每字 3 字节）
        StringBuilder result = new StringBuilder(Math.min(value.length(), maxLength));
        int usedBytes = 0;
        int index = 0;
        while (index < value.length() && usedBytes < maxLength) {
            int codePoint = value.codePointAt(index);
            int charCount = Character.charCount(codePoint);
            if (codePoint < CONTROL_CHARACTER_LIMIT
                    || "\\/:*?\"<>|".indexOf(value.charAt(index)) >= 0) {
                codePoint = '_';
                charCount = 1;
            }
            int charBytes = utf8ByteCount(codePoint);
            if (usedBytes + charBytes > maxLength) {
                break;
            }
            if (codePoint == '_') {
                result.append('_');
            } else {
                result.appendCodePoint(codePoint);
            }
            usedBytes += charBytes;
            index += charCount;
        }
        while (!result.isEmpty()
                && (result.charAt(result.length() - 1) == '.'
                || result.charAt(result.length() - 1) == ' ')) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.isEmpty() ? EMPTY_COMPONENT : result.toString();
    }

    private int utf8ByteCount(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    private Long longValue(Object value) {
        String text = normalize(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
