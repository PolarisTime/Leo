package com.leo.erp.system.printtemplate.service;

import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PrintExportFilenameService {

    private static final String EMPTY_COMPONENT = "-";
    private static final int BUSINESS_NO_MAX_LENGTH = 64;
    private static final int DATE_MAX_LENGTH = 16;
    private static final int PROJECT_MAX_LENGTH = 72;
    private static final int COMPANY_MAX_LENGTH = 72;
    private static final int DATE_PART_COUNT = 3;
    private static final int YEAR_INDEX = 0;
    private static final int MONTH_INDEX = 1;
    private static final int DAY_INDEX = 2;
    private static final int CONTROL_CHARACTER_LIMIT = 32;

    private final ProjectRepository projectRepository;
    private final PrintRuntimeProperties runtimeProperties;

    public PrintExportFilenameService(
            ProjectRepository projectRepository,
            PrintRuntimeProperties runtimeProperties
    ) {
        this.projectRepository = projectRepository;
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
                businessDate,
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
                safeComponent(normalizeDate(businessDate), DATE_MAX_LENGTH),
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
        return projectRepository.findByIdAndDeletedFlagFalse(projectId)
                .map(Project::getProjectNameAbbr)
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

    private String normalizeDate(String rawDate) {
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
            ).toString();
        } catch (RuntimeException ignored) {
            return datePart;
        }
    }

    private String safeComponent(String rawValue, int maxLength) {
        String value = normalize(rawValue);
        StringBuilder result = new StringBuilder(Math.min(value.length(), maxLength));
        for (int index = 0; index < value.length() && result.length() < maxLength; index += 1) {
            char ch = value.charAt(index);
            if (ch < CONTROL_CHARACTER_LIMIT || "\\/:*?\"<>|".indexOf(ch) >= 0) {
                result.append('_');
            } else {
                result.append(ch);
            }
        }
        while (!result.isEmpty()
                && (result.charAt(result.length() - 1) == '.'
                || result.charAt(result.length() - 1) == ' ')) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.isEmpty() ? EMPTY_COMPONENT : result.toString();
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
