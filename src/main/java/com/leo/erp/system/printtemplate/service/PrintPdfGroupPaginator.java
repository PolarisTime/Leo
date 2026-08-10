package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PrintPdfGroupPaginator {

    private static final String GROUP_HEADER_SOURCE = "source";
    private static final String GROUP_HEADER_PROJECT = "project";
    private static final String GROUP_HEADER_SUBTOTAL = "subtotal";
    private static final String GROUP_HEADER_KEY = "isGroupHeader";
    private static final String DETAIL_HEADER_AFTER_PROJECT_HEADER = "afterProjectHeader";

    private final PrintPdfDrawingSupport drawing;

    public PrintPdfGroupPaginator(PrintPdfDrawingSupport drawing) {
        this.drawing = drawing;
    }

    boolean isGroupHeader(Map<String, String> item) {
        return isSourceGroupHeader(item)
                || GROUP_HEADER_PROJECT.equals(item.get(GROUP_HEADER_KEY));
    }

    boolean isBlankRow(Map<String, String> item) {
        return "true".equals(item.get("isBlankRow"));
    }

    /** 组内明细末尾的小计汇总行。 */
    boolean isSubtotalRow(Map<String, String> item) {
        return GROUP_HEADER_SUBTOTAL.equals(item.get(GROUP_HEADER_KEY));
    }

    /** 小计行高度，取 groupHeader.subtotal.height，缺省回落 groupHeader.height。 */
    float subtotalHeight(JsonNode tableConfig) {
        return drawing.number(
                tableConfig.path("groupHeader").path(GROUP_HEADER_SUBTOTAL),
                "height",
                drawing.number(tableConfig.path("groupHeader"), "height", 18f)
        );
    }

    boolean shouldStartGroupOnNextPage(
            List<Map<String, String>> items,
            int itemIndex,
            float rowTop,
            float tableBottom,
            float continuationTableTop,
            JsonNode tableConfig,
            float headerHeight,
            float rowHeight,
            boolean repeatHeaderPerSourceGroup
    ) {
        Map<String, String> item = items.get(itemIndex);
        if (!isGroupHeader(item)) {
            return false;
        }
        if (rowTop <= continuationTableTop) {
            return false;
        }
        float minimumGroupHeight = minimumGroupHeight(
                items, itemIndex, tableConfig, headerHeight, rowHeight, repeatHeaderPerSourceGroup
        );
        float continuationContextHeight = continuationContextHeight(
                items, itemIndex, tableConfig, repeatHeaderPerSourceGroup
        );
        float fullPageHeight = tableBottom - continuationTableTop;
        return minimumGroupHeight + continuationContextHeight <= fullPageHeight
                && rowTop + minimumGroupHeight > tableBottom;
    }

    boolean isGroupContinuation(List<Map<String, String>> items, int itemIndex) {
        return itemIndex > 0
                && itemIndex < items.size()
                && !isBlankRow(items.get(itemIndex))
                && !isSourceGroupHeader(items.get(itemIndex))
                && !groupContinuationHeaders(items, itemIndex).isEmpty();
    }

    List<Map<String, String>> groupContinuationHeaders(
            List<Map<String, String>> items,
            int itemIndex
    ) {
        Map<String, String> sourceHeader = null;
        Map<String, String> projectHeader = null;
        boolean startsProjectGroup = GROUP_HEADER_PROJECT.equals(items.get(itemIndex).get(GROUP_HEADER_KEY));
        for (int index = itemIndex - 1; index >= 0; index--) {
            Map<String, String> item = items.get(index);
            String groupType = item.get(GROUP_HEADER_KEY);
            if (GROUP_HEADER_SOURCE.equals(groupType)) {
                sourceHeader = item;
                break;
            }
            if (!startsProjectGroup && GROUP_HEADER_PROJECT.equals(groupType) && projectHeader == null) {
                projectHeader = item;
            }
        }
        if (sourceHeader == null) {
            return List.of();
        }
        List<Map<String, String>> headers = new ArrayList<>();
        headers.add(sourceHeader);
        if (projectHeader != null) {
            headers.add(projectHeader);
        }
        return headers;
    }

    boolean isSourceGroupHeader(Map<String, String> item) {
        return GROUP_HEADER_SOURCE.equals(item.get(GROUP_HEADER_KEY));
    }

    boolean isProjectGroupHeader(Map<String, String> item) {
        return GROUP_HEADER_PROJECT.equals(item.get(GROUP_HEADER_KEY));
    }

    float groupHeaderHeight(JsonNode tableConfig, Map<String, String> item) {
        JsonNode groupHeader = tableConfig.path("groupHeader");
        String groupType = item.get(GROUP_HEADER_KEY);
        String heightKey = GROUP_HEADER_SOURCE.equals(groupType) ? "sourceHeight" : "projectHeight";
        return drawing.number(
                groupHeader.path(groupType),
                "height",
                drawing.number(groupHeader, heightKey, drawing.number(groupHeader, "height", 18f))
        );
    }

    boolean isDetailHeaderAfterProject(JsonNode tableConfig, boolean repeatHeaderPerSourceGroup) {
        return repeatHeaderPerSourceGroup
                && DETAIL_HEADER_AFTER_PROJECT_HEADER.equals(tableConfig.path("detailHeaderPlacement").asText());
    }

    private float minimumGroupHeight(
            List<Map<String, String>> items,
            int groupHeaderIndex,
            JsonNode tableConfig,
            float headerHeight,
            float rowHeight,
            boolean repeatHeaderPerSourceGroup
    ) {
        String groupType = items.get(groupHeaderIndex).get(GROUP_HEADER_KEY);
        boolean detailHeaderAfterProject = isDetailHeaderAfterProject(tableConfig, repeatHeaderPerSourceGroup);
        float height = repeatHeaderPerSourceGroup
                && !detailHeaderAfterProject
                && GROUP_HEADER_SOURCE.equals(groupType) ? headerHeight : 0f;
        for (int index = groupHeaderIndex; index < items.size(); index++) {
            Map<String, String> item = items.get(index);
            if (index > groupHeaderIndex && (isBlankRow(item) || startsNextGroup(item, groupType))) {
                break;
            }
            if (isGroupHeader(item)) {
                height += groupHeaderHeight(tableConfig, item);
                if (detailHeaderAfterProject && isProjectGroupHeader(item)) {
                    height += headerHeight;
                }
                continue;
            }
            if (isSubtotalRow(item)) {
                height += subtotalHeight(tableConfig);
                return height;
            }
            height += rowHeight;
            // 明细行后若紧跟小计行，一并计入，避免小计与明细分页分离
            if (index + 1 < items.size() && isSubtotalRow(items.get(index + 1))) {
                height += subtotalHeight(tableConfig);
            }
            return height;
        }
        return height;
    }

    private float continuationContextHeight(
            List<Map<String, String>> items,
            int groupHeaderIndex,
            JsonNode tableConfig,
            boolean repeatHeaderPerSourceGroup
    ) {
        if (!repeatHeaderPerSourceGroup
                || !drawing.bool(tableConfig, "repeatGroupContextOnContinuation", true)
                || !isProjectGroupHeader(items.get(groupHeaderIndex))) {
            return 0f;
        }
        float height = 0f;
        for (Map<String, String> header : groupContinuationHeaders(items, groupHeaderIndex)) {
            height += groupHeaderHeight(tableConfig, header);
        }
        return height;
    }

    private boolean startsNextGroup(Map<String, String> item, String currentGroupType) {
        String nextGroupType = item.get(GROUP_HEADER_KEY);
        return GROUP_HEADER_SOURCE.equals(nextGroupType)
                || (GROUP_HEADER_PROJECT.equals(currentGroupType)
                && GROUP_HEADER_PROJECT.equals(nextGroupType));
    }
}
