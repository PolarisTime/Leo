package com.leo.erp.system.printtemplate.service;

import com.leo.erp.attachment.api.AttachmentRecordAccess;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrintScriptService {

    private static final String SALES_ORDER_MODULE = "sales-order";
    private static final String FREIGHT_STATEMENT_MODULE = "freight-statement";
    private static final String PDF_FORM_TEMPLATE_TYPE = "PDF_FORM";
    private static final String GROUP_HEADER_SOURCE = "source";
    private static final String GROUP_HEADER_PROJECT = "project";
    private static final String GROUP_HEADER_SUBTOTAL = "subtotal";

    private final PrintTemplateRepository templateRepository;
    private final PrintRecordDataProvider dataProvider;
    private final PrintRecordEnricher recordEnricher;
    private final PrintRecordLayoutPreparer layoutPreparer;
    private final PrintLayoutLodopRenderer layoutLodopRenderer;
    private final AttachmentRecordAccess recordAccessService;
    private final PrintRuntimeProperties runtimeProperties;

    public PrintScriptService(
            PrintTemplateRepository templateRepository,
            PrintRecordDataProvider dataProvider,
            PrintRecordEnricher recordEnricher,
            PrintRecordLayoutPreparer layoutPreparer,
            PrintLayoutLodopRenderer layoutLodopRenderer,
            AttachmentRecordAccess recordAccessService,
            PrintRuntimeProperties runtimeProperties
    ) {
        this.templateRepository = templateRepository;
        this.dataProvider = dataProvider;
        this.recordEnricher = recordEnricher;
        this.layoutPreparer = layoutPreparer;
        this.layoutLodopRenderer = layoutLodopRenderer;
        this.recordAccessService = recordAccessService;
        this.runtimeProperties = runtimeProperties;
    }

    /** Load record + items from DB, return raw template + data for frontend rendering. */
    public Map<String, Object> generateFromRecord(String templateId, String moduleKey, Long recordId) {
        return generateFromRecord(templateId, moduleKey, recordId, PrintRenderOptions.defaults());
    }

    public Map<String, Object> generateFromRecord(String templateId, String moduleKey, Long recordId, PrintRenderOptions options) {
        PrintTemplate template = templateRepository.findByIdAndDeletedFlagFalse(Long.parseLong(templateId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "打印模板不存在"));
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "打印模板已禁用");
        }

        if (!moduleKey.equals(template.getBillType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "打印模板与当前模块不匹配");
        }
        dataProvider.requireSupported(moduleKey);
        recordAccessService.assertRecordExists(moduleKey, recordId);
        PrintRecordData recordData = dataProvider.loadRecord(moduleKey, recordId);
        Map<String, String> data = recordData.data();
        List<Map<String, String>> items = recordData.items();

        recordEnricher.enrich(moduleKey, data, items);
        assertTemplateMatchesSettlementCompany(template, data);
        items = applyItemSelection(items, options);
        items = applyItemOrder(items, options);
        applyPrintOptions(data, items, options);
        if (shouldMergeEquivalentItems(moduleKey, options)) {
            items = PrintRecordItemMerger.mergeEquivalentItems(items);
        }
        items = layoutPreparer.prepare(moduleKey, template.getTemplateName(), template.getTemplateHtml(), data, items);
        if (FREIGHT_STATEMENT_MODULE.equals(moduleKey)
                && PDF_FORM_TEMPLATE_TYPE.equals(template.getTemplateType())) {
            items = groupItemsForPdf(items);
        }
        if ("COORD".equals(template.getTemplateType())) {
            items = appendLengthToSpec(items);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("templateName", template.getTemplateName());
        result.put("templateHtml", renderTemplateHtml(template, data, items));
        result.put("templateType", template.getTemplateType() != null ? template.getTemplateType() : "COORD");
        result.put("businessNo", resolvePrintBusinessNo(data));
        result.put("recordId", recordId);
        result.put("moduleKey", moduleKey);
        result.put("data", data);
        result.put("items", items);
        return result;
    }

    private boolean shouldMergeEquivalentItems(
            String moduleKey,
            PrintRenderOptions options
    ) {
        return SALES_ORDER_MODULE.equals(moduleKey)
                && (options == null || options.mergeEquivalentItems());
    }

    List<Map<String, String>> groupItemsForPdf(List<Map<String, String>> items) {
        Map<String, List<Map<String, String>>> sourceGroups = new LinkedHashMap<>();
        for (Map<String, String> item : items) {
            String sourceId = normalizeText(item.get("sourceFreightBillId"));
            String sourceNo = normalizeText(item.get("sourceNo"));
            String key = !sourceId.isBlank()
                    ? "source-id:" + sourceId
                    : !sourceNo.isBlank() ? "source-no:" + sourceNo : "unassigned";
            sourceGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        List<Map<String, String>> rows = new ArrayList<>();
        boolean firstSourceGroup = true;
        for (List<Map<String, String>> groupItems : sourceGroups.values()) {
            int lineIndex = 0;
            if (!firstSourceGroup) {
                rows.add(blankGroupRow());
            }
            firstSourceGroup = false;
            rows.add(sourceGroupHeader(groupItems));
            Map<String, List<Map<String, String>>> projectGroups = new LinkedHashMap<>();
            for (Map<String, String> item : groupItems) {
                String projectName = normalizeText(item.get("projectName"));
                String key = !projectName.isBlank() ? "project:" + projectName : "unassigned";
                projectGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
            }
            for (List<Map<String, String>> projectItems : projectGroups.values()) {
                rows.add(projectGroupHeader(projectItems));
                for (Map<String, String> item : projectItems) {
                    item.put("index", String.valueOf(++lineIndex));
                    rows.add(item);
                }
                rows.add(projectSubtotal(projectItems));
            }
        }
        return rows;
    }

    private Map<String, String> blankGroupRow() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("isBlankRow", "true");
        return row;
    }

    private Map<String, String> sourceGroupHeader(List<Map<String, String>> groupItems) {
        BigDecimal totalWeight = sumDecimal(groupItems, "weightTon");
        BigDecimal totalFreight = firstPositiveDecimal(groupItems, "amount");
        BigDecimal unitPrice = totalWeight.signum() > 0
                ? totalFreight.divide(totalWeight, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        Map<String, String> header = new LinkedHashMap<>();
        header.put("isGroupHeader", GROUP_HEADER_SOURCE);
        header.put("sourceNo", distinctText(groupItems, "sourceNo"));
        header.put("billTime", distinctText(groupItems, "billTime"));
        header.put("vehiclePlate", distinctText(groupItems, "vehiclePlate"));
        header.put("totalQuantity", String.valueOf(sumInteger(groupItems, "quantity")));
        header.put("totalWeightTon", plainNumber(totalWeight));
        header.put("totalFreight", plainAmount(totalFreight));
        header.put("unitPrice", plainAmount(unitPrice));
        return header;
    }

    private Map<String, String> projectGroupHeader(List<Map<String, String>> projectItems) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("isGroupHeader", GROUP_HEADER_PROJECT);
        header.put("customerName", distinctText(projectItems, "customerName"));
        header.put("projectName", distinctText(projectItems, "projectName"));
        header.put("totalQuantity", String.valueOf(sumInteger(projectItems, "quantity")));
        header.put("totalWeightTon", plainNumber(sumDecimal(projectItems, "weightTon")));
        return header;
    }

    /** 项目组明细末尾的小计行：承载数量、重量汇总，渲染为独立的浅色小计行。 */
    private Map<String, String> projectSubtotal(List<Map<String, String>> projectItems) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("isGroupHeader", GROUP_HEADER_SUBTOTAL);
        row.put("totalQuantity", String.valueOf(sumInteger(projectItems, "quantity")));
        row.put("totalWeightTon", plainNumber(sumDecimal(projectItems, "weightTon")));
        return row;
    }

    private String distinctText(List<Map<String, String>> items, String key) {
        return items.stream()
                .map(item -> normalizeText(item.get(key)))
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining("、"));
    }

    private BigDecimal sumDecimal(List<Map<String, String>> items, String key) {
        return items.stream()
                .map(item -> decimalOf(item.get(key)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int sumInteger(List<Map<String, String>> items, String key) {
        return items.stream()
                .mapToInt(item -> decimalOf(item.get(key)).intValue())
                .sum();
    }

    private BigDecimal firstPositiveDecimal(List<Map<String, String>> items, String key) {
        for (Map<String, String> item : items) {
            BigDecimal value = decimalOf(item.get(key));
            if (value.signum() > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal decimalOf(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String plainAmount(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String plainNumber(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private List<Map<String, String>> appendLengthToSpec(List<Map<String, String>> items) {
        return items.stream().map(item -> {
            Map<String, String> printableItem = new HashMap<>(item);
            String spec = normalizeText(printableItem.get("spec"));
            String length = normalizeText(printableItem.get("length"));
            if (!spec.isBlank() && "12米".equals(length) && !spec.endsWith("*12")) {
                printableItem.put("spec", spec + "*12");
            }
            return printableItem;
        }).toList();
    }

    private void assertTemplateMatchesSettlementCompany(PrintTemplate template, Map<String, String> data) {
        if (matchesSettlementCompany(template, data)) {
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "打印模板与当前结算主体不匹配");
    }

    private boolean matchesSettlementCompany(PrintTemplate template, Map<String, String> data) {
        String recordCompanyId = normalizeText(data.get("settlementCompanyId"));
        String recordCompanyName = normalizeText(data.get("settlementCompanyName"));
        String templateCompanyId = template.getSettlementCompanyId() == null
                ? ""
                : String.valueOf(template.getSettlementCompanyId());
        String templateCompanyName = normalizeText(template.getSettlementCompanyName());

        if (!templateCompanyId.isBlank()) {
            if (!recordCompanyId.isBlank()) {
                return templateCompanyId.equals(recordCompanyId);
            }
            return !templateCompanyName.isBlank() && templateCompanyName.equals(recordCompanyName);
        }
        if (!templateCompanyName.isBlank()) {
            return templateCompanyName.equals(recordCompanyName);
        }
        // 模板未绑定结算主体时为通用模板，适用于任何主体的单据（兜底）。
        return true;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public List<PrintRecordItem> listPrintItems(String moduleKey, List<Long> recordIds) {
        dataProvider.requireSupported(moduleKey);
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        for (Long recordId : recordIds) {
            recordAccessService.assertRecordExists(moduleKey, recordId);
        }
        return dataProvider.listPrintItems(moduleKey, recordIds);
    }

    private void applyPrintOptions(Map<String, String> data, List<Map<String, String>> items, PrintRenderOptions options) {
        if (options == null) {
            return;
        }
        if (options.hideUnitPrice()) {
            data.put("unitPrice", "");
            for (Map<String, String> item : items) {
                item.put("unitPrice", "");
            }
        }
        if (options.hideRemark()) {
            data.put("remark", "");
        }
        if (options.brandOverride() != null && !options.brandOverride().isBlank()) {
            String brandOverride = options.brandOverride().trim();
            for (Map<String, String> item : items) {
                item.put("brand", brandOverride);
            }
        }
        if (options.brandOverridesByItemId() != null && !options.brandOverridesByItemId().isEmpty()) {
            for (Map<String, String> item : items) {
                String itemId = item.get("id");
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                String override = options.brandOverridesByItemId().get(itemId);
                if (override != null && !override.isBlank()) {
                    item.put("brand", override.trim());
                }
            }
        }
        if (options.brandOverrides() == null || options.brandOverrides().isEmpty()) {
            return;
        }
        for (Map<String, String> item : items) {
            String originalBrand = item.get("brand");
            if (originalBrand == null || originalBrand.isBlank()) {
                continue;
            }
            String override = options.brandOverrides().get(originalBrand);
            if (override != null && !override.isBlank()) {
                item.put("brand", override.trim());
            }
        }
    }

    private List<Map<String, String>> applyItemOrder(List<Map<String, String>> items, PrintRenderOptions options) {
        if (options == null || options.itemOrder() == null || options.itemOrder().isEmpty() || items.isEmpty()) {
            return items;
        }
        Map<String, Map<String, String>> itemsById = new LinkedHashMap<>();
        for (Map<String, String> item : items) {
            String itemId = item.get("id");
            if (itemId != null && !itemId.isBlank()) {
                itemsById.putIfAbsent(itemId, item);
            }
        }
        if (itemsById.isEmpty()) {
            return items;
        }

        Set<String> selectedIds = new HashSet<>();
        List<Map<String, String>> orderedItems = new ArrayList<>();
        for (String itemId : options.itemOrder()) {
            Map<String, String> item = itemsById.get(itemId);
            if (item != null && selectedIds.add(itemId)) {
                orderedItems.add(item);
            }
        }
        for (Map<String, String> item : items) {
            String itemId = item.get("id");
            if (itemId == null || itemId.isBlank() || selectedIds.add(itemId)) {
                orderedItems.add(item);
            }
        }
        return orderedItems;
    }

    private List<Map<String, String>> applyItemSelection(List<Map<String, String>> items, PrintRenderOptions options) {
        if (options == null || options.selectedItemIds() == null || items.isEmpty()) {
            return items;
        }
        Set<String> selectedItemIds = new HashSet<>(options.selectedItemIds());
        return items.stream()
                .filter(item -> selectedItemIds.contains(item.get("id")))
                .toList();
    }

    private String renderTemplateHtml(PrintTemplate template, Map<String, String> data, List<Map<String, String>> items) {
        String templateHtml = template.getTemplateHtml();
        if ("COORD".equals(template.getTemplateType()) && layoutLodopRenderer.supports(templateHtml)) {
            return layoutLodopRenderer.render(template.getTemplateName(), templateHtml, data, items);
        }
        return templateHtml;
    }

    private String resolvePrintBusinessNo(Map<String, String> data) {
        return firstPresent(data, runtimeProperties.childTextValues(
                runtimeProperties.topLevelFields().path("businessNoKeys")
        ));
    }

    private String firstPresent(Map<String, String> data, List<String> keys) {
        for (String key : keys) {
            String value = data.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
