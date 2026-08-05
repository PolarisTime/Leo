package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 打印模板清单：print-template-manifest.json 中登记的模板文件元数据。
 * 作为部署时模板文件的单一事实源，{@link PrintTemplateFileSyncRunner} 据此自动登记、
 * 更新内容并在文件移除时自动停用对应模板。
 */
@Component
public class PrintTemplateManifest {

    static final String MANIFEST_RESOURCE = "print-template-manifest.json";

    @Getter
    public static class Item {
        private final String sourceRef;
        private final String billType;
        private final String templateName;
        private final String templateCode;
        private final boolean isDefault;
        private final String settlementCompanyName;

        Item(String sourceRef, String billType, String templateName, String templateCode,
             boolean isDefault, String settlementCompanyName) {
            this.sourceRef = sourceRef;
            this.billType = billType;
            this.templateName = templateName;
            this.templateCode = templateCode;
            this.isDefault = isDefault;
            this.settlementCompanyName = settlementCompanyName;
        }
    }

    private final List<Item> templates;
    private final Set<String> sourceRefs;

    public PrintTemplateManifest(ObjectMapper objectMapper) {
        JsonNode root = readRoot(objectMapper);
        List<Item> items = new ArrayList<>();
        JsonNode templatesNode = root.path("templates");
        if (templatesNode.isArray()) {
            for (JsonNode node : templatesNode) {
                items.add(new Item(
                        text(node, "sourceRef"),
                        text(node, "billType"),
                        text(node, "templateName"),
                        text(node, "templateCode"),
                        node.path("isDefault").asBoolean(false),
                        text(node, "settlementCompanyName")
                ));
            }
        }
        this.templates = Collections.unmodifiableList(items);

        Set<String> refs = new HashSet<>();
        for (Item item : items) {
            if (item.getSourceRef() != null && !item.getSourceRef().isBlank()) {
                refs.add(item.getSourceRef());
            }
        }
        this.sourceRefs = Collections.unmodifiableSet(refs);
    }

    /** 清单登记的全部模板项。 */
    public List<Item> getTemplates() {
        return templates;
    }

    /** 清单登记的全部 sourceRef 集合。 */
    public Set<String> getSourceRefs() {
        return sourceRefs;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private JsonNode readRoot(ObjectMapper objectMapper) {
        try {
            String content = new ClassPathResource(MANIFEST_RESOURCE)
                    .getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(content);
            if (node == null || !node.isObject()) {
                throw new IllegalStateException("打印模板清单不是合法 JSON 对象: " + MANIFEST_RESOURCE);
            }
            return node;
        } catch (IOException ex) {
            throw new IllegalStateException("读取打印模板清单失败: " + MANIFEST_RESOURCE, ex);
        }
    }
}
