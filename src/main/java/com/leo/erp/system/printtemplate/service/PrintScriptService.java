package com.leo.erp.system.printtemplate.service;

import com.leo.erp.attachment.service.AttachmentRecordAccessService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PrintScriptService {

    private final PrintTemplateRepository templateRepository;
    private final PrintRecordDataProvider dataProvider;
    private final PrintRecordEnricher recordEnricher;
    private final PrintRecordLayoutPreparer layoutPreparer;
    private final PrintLayoutLodopRenderer layoutLodopRenderer;
    private final AttachmentRecordAccessService recordAccessService;
    private final PrintRuntimeProperties runtimeProperties;

    public PrintScriptService(
            PrintTemplateRepository templateRepository,
            PrintRecordDataProvider dataProvider,
            PrintRecordEnricher recordEnricher,
            PrintRecordLayoutPreparer layoutPreparer,
            PrintLayoutLodopRenderer layoutLodopRenderer,
            AttachmentRecordAccessService recordAccessService,
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
        items = layoutPreparer.prepare(moduleKey, template.getTemplateName(), template.getTemplateHtml(), data, items);
        if ("COORD".equals(template.getTemplateType())) {
            items = appendLengthToSpec(items);
        }
        applyPrintOptions(data, items, options);

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
        return recordCompanyId.isBlank() && recordCompanyName.isBlank();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    public List<String> listBrands(String moduleKey, List<Long> recordIds) {
        dataProvider.requireSupported(moduleKey);
        if (recordIds == null || recordIds.isEmpty()) {
            return List.of();
        }
        for (Long recordId : recordIds) {
            recordAccessService.assertRecordExists(moduleKey, recordId);
        }
        return dataProvider.listBrands(moduleKey, recordIds);
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
