package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印模板清单与模板文件的契约：清单登记的每个 sourceRef 必须真实存在，
 * `.layout.json` 模板必须能通过 {@link PrintPdfFormTemplateValidator} 校验。
 *
 * <p>背景：模板文件是「文件同步」的唯一事实源，启动时 {@link PrintTemplateFileSyncRunner}
 * 会读文件覆盖库中模板；模板写坏（JSON 非法、缺 static/fields/table）会让启动同步失败。
 * 本用例在普通单元测试阶段就拦住坏模板，不依赖真实库。</p>
 */
class PrintTemplateManifestContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PrintTemplateManifest manifest = new PrintTemplateManifest(objectMapper);
    private final PrintPdfFormTemplateValidator validator =
            new PrintPdfFormTemplateValidator(objectMapper);

    @Test
    void everyManifestTemplateShouldExistAndPassPdfFormValidation() throws Exception {
        assertThat(manifest.getTemplates()).isNotEmpty();
        for (PrintTemplateManifest.Item item : manifest.getTemplates()) {
            ClassPathResource resource = new ClassPathResource(item.getSourceRef());
            assertThat(resource.exists())
                    .as("模板文件不存在: " + item.getSourceRef())
                    .isTrue();
            if (!item.getSourceRef().endsWith(".layout.json")) {
                continue;
            }
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            JsonNode node = validator.validate(content);
            assertThat(node.isObject())
                    .as("PDF_FORM 模板必须是 JSON 对象: " + item.getSourceRef())
                    .isTrue();
        }
    }

    @Test
    void customerStatementFreightStatementAndAllocationTemplatesShouldBeRegistered() {
        assertThat(manifest.getTemplates())
                .extracting(PrintTemplateManifest.Item::getBillType)
                .contains(
                        "customer-statement",
                        "freight-statement",
                        "sales-return",
                        "payment",
                        "receipt"
                );
    }
}
