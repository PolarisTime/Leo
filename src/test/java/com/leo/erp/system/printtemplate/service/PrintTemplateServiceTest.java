package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintTemplateServiceTest {

    @Test
    void shouldRejectUnsupportedBillType() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request("permission-management", "模板A", "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不合法");
    }

    @Test
    void shouldRejectHtmlTemplateType() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "<img src=x onerror=alert(1)>",
                "HTML"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板类型仅支持 COORD 或 PDF_FORM");
    }

    @Test
    void shouldRejectDangerousLodopTemplate() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "LODOP.PRINT_INIT('test'); window.alert('xss');",
                "COORD"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容包含不允许的脚本或危险标签");
    }

    private PrintTemplateRepository repository() {
        return (PrintTemplateRepository) Proxy.newProxyInstance(
                PrintTemplateRepository.class.getClassLoader(),
                new Class[]{PrintTemplateRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByBillTypeAndTemplateNameAndDeletedFlagFalse" -> false;
                    case "existsByBillTypeAndTemplateCodeAndDeletedFlagFalse" -> false;
                    case "findAllByBillTypeAndDeletedFlagFalseOrderByUpdatedAtDescIdDesc" -> java.util.List.of();
                    case "save" -> args[0];
                    case "toString" -> "PrintTemplateRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PrintTemplateRepository repository(PrintTemplate template) {
        return (PrintTemplateRepository) Proxy.newProxyInstance(
                PrintTemplateRepository.class.getClassLoader(),
                new Class[]{PrintTemplateRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByBillTypeAndTemplateNameAndDeletedFlagFalse" -> false;
                    case "existsByBillTypeAndTemplateCodeAndDeletedFlagFalse" -> false;
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(template);
                    case "save" -> args[0];
                    case "toString" -> "PrintTemplateRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PrintTemplateMapper mapper() {
        return Mappers.getMapper(PrintTemplateMapper.class);
    }

    private PrintTemplateService service(PrintTemplateRepository repository) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new PrintTemplateService(
                repository,
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog(),
                new PrintPdfFormTemplateValidator(objectMapper)
        );
    }

    private PrintTemplateRequest request(String billType, String templateName, String templateHtml, String templateType) {
        return new PrintTemplateRequest(
                billType,
                templateName,
                null,
                templateHtml,
                templateType,
                null,
                null,
                null,
                null
        );
    }

    private PrintTemplateRequest request(
            String billType,
            String templateName,
            String templateCode,
            String templateHtml,
            String templateType,
            String engine,
            String assetRef,
            Integer versionNo,
            String status
    ) {
        return new PrintTemplateRequest(
                billType,
                templateName,
                templateCode,
                templateHtml,
                templateType,
                engine,
                assetRef,
                versionNo,
                status
        );
    }

    private PrintTemplateRequest pdfRequest(String billType) {
        return request(
                billType,
                "默认 PDF",
                "PDF_" + billType,
                null,
                "PDF_FORM",
                "PDF_FORM",
                null,
                null,
                "ACTIVE"
        );
    }

    @Test
    void shouldRejectEmptyBillType() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request("", "模板A", "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不能为空");
    }

    @Test
    void shouldRejectNullTemplateName() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request("purchase-order", null, "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称不能为空");
    }

    @Test
    void shouldRejectEmptyTemplateHtml() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request("purchase-order", "模板A", "", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容不能为空");
    }

    @Test
    void shouldRejectInvalidTemplateType() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request("purchase-order", "模板A", "<div/>", "INVALID")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板类型仅支持");
    }

    @Test
    void shouldAllowPdfFormTypeWithoutTemplateHtmlOrAssetRef() {
        PrintTemplateService service = service(repository());

        var result = service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                null,
                "PDF_FORM",
                "PDF_FORM",
                null,
                2,
                "ACTIVE"
        ));

        assertThat(result).isNotNull();
        assertThat(result.templateCode()).isEqualTo("PDF_CODE");
        assertThat(result.templateType()).isEqualTo("PDF_FORM");
        assertThat(result.engine()).isEqualTo("PDF_FORM");
        assertThat(result.assetRef()).isNull();
        assertThat(result.versionNo()).isEqualTo(2);
        assertThat(result.templateHtml()).contains("\"page\"");
        assertThat(result.templateHtml()).contains("\"static\"");
    }

    @Test
    void shouldUseBillTypeSpecificDefaultPdfFormLayout() {
        PrintTemplateService service = service(repository());

        var purchase = service.create(pdfRequest("purchase-order"));
        var sales = service.create(pdfRequest("sales-order"));
        var logistics = service.create(pdfRequest("freight-bill"));
        var statement = service.create(pdfRequest("customer-statement"));

        assertThat(purchase.templateHtml()).contains("采购单");
        assertThat(sales.templateHtml()).contains("销售单");
        assertThat(logistics.templateHtml()).contains("物流配送单");
        assertThat(statement.templateHtml()).contains("对账单");
    }

    @Test
    void shouldRejectPdfFormWithInvalidAssetRef() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                null,
                null,
                "PDF_FORM",
                null,
                "../private/template.pdf",
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 底版资源路径不合法");
    }

    @Test
    void shouldRejectHtmlEngine() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                null,
                "<div/>",
                "HTML",
                "LODOP",
                null,
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板类型仅支持 COORD 或 PDF_FORM");
    }

    @Test
    void shouldCreateValidTemplate() {
        PrintTemplateService service = service(repository());

        var result = service.create(request("purchase-order", "模板A", "LODOP.PRINT_INIT('安全内容');", null));
        assertThat(result).isNotNull();
        assertThat(result.templateCode()).startsWith("TPL_");
        assertThat(result.templateType()).isEqualTo("COORD");
        assertThat(result.engine()).isEqualTo("LODOP");
        assertThat(result.versionNo()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldListByBillType() {
        PrintTemplateService service = service(repository());

        var result = service.listByBillType("purchase-order");
        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectUpdateForFileManagedTemplate() {
        PrintTemplate template = pdfTemplate();
        template.setSyncMode("FILE");
        template.setSourceRef("print-forms/yingjie-a4-remark.layout.json");

        PrintTemplateService service = service(repository(template));

        assertThatThrownBy(() -> service.update(1L, request(
                "sales-order",
                "颖捷A4打印_带备注 PDF",
                "SALES_ORDER_YINGJIE_A4_REMARK_PDF",
                "{\"page\":{}}",
                "PDF_FORM",
                "PDF_FORM",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件托管模板请通过上传 JSON 或修改源文件后重启同步");
    }

    @Test
    void shouldUploadJsonForPdfFormTemplateAndDisableFileSync() {
        PrintTemplate template = pdfTemplate();
        template.setVersionNo(3);
        template.setSyncMode("FILE");
        template.setSourceRef("print-forms/yingjie-a4-remark.layout.json");
        template.setSourceChecksum("old-checksum");
        PrintTemplateService service = service(repository(template));
        String content = minimalPdfFormLayout();

        var response = service.uploadJson(1L, jsonFile("layout.json", content));

        assertThat(response.templateHtml()).isEqualTo(content);
        assertThat(response.versionNo()).isEqualTo(4);
        assertThat(response.syncMode()).isEqualTo("MANUAL");
        assertThat(response.sourceRef()).isNull();
        assertThat(response.sourceChecksum()).isNull();
    }

    @Test
    void shouldRejectUploadForNonPdfFormTemplate() {
        PrintTemplate template = pdfTemplate();
        template.setTemplateType("COORD");
        template.setEngine("LODOP");

        PrintTemplateService service = service(repository(template));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.json", "{\"page\":{}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅 PDF_FORM 模板支持上传 JSON");
    }

    @Test
    void shouldRejectUploadWhenTemplateMissing() {
        PrintTemplateService service = service(repository(Optional.empty()));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.json", "{\"page\":{}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("打印模板不存在");
    }

    @Test
    void shouldRejectUploadWhenJsonIsNotObject() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.json", "[]")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 对象");
    }

    @Test
    void shouldRejectUploadWhenJsonHasTrailingTokens() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.json", minimalPdfFormLayout() + "\n{}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON");
    }

    @Test
    void shouldRejectUploadWhenPdfFormLayoutIsLegacyFormConfig() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(
                1L,
                jsonFile("layout.json", "{\"form\":\"YINGJIE_A4_REMARK\"}")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持 form 专用配置");
    }

    @Test
    void shouldRejectUploadWhenPdfFormLayoutHasNoRenderableContent() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.json", "{\"page\":{\"width\":595}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须配置通用字段或明细布局");
    }

    @Test
    void shouldRejectUploadWhenUtf8IsInvalid() {
        PrintTemplateService service = service(repository(pdfTemplate()));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                new byte[]{(byte) 0xC3, (byte) 0x28}
        );

        assertThatThrownBy(() -> service.uploadJson(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void shouldRejectUploadWhenFileIsTooLarge() {
        PrintTemplateService service = service(repository(pdfTemplate()));
        byte[] bytes = new byte[1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "layout.json", "application/json", bytes);

        assertThatThrownBy(() -> service.uploadJson(1L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 1MB");
    }

    @Test
    void shouldRejectUploadWhenFilenameIsNotJson() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.txt", "{\"page\":{}}")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
    }

    private PrintTemplateRepository repository(Optional<PrintTemplate> template) {
        return (PrintTemplateRepository) Proxy.newProxyInstance(
                PrintTemplateRepository.class.getClassLoader(),
                new Class[]{PrintTemplateRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> template;
                    case "save" -> args[0];
                    case "toString" -> "PrintTemplateRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PrintTemplate pdfTemplate() {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType("sales-order");
        template.setTemplateName("颖捷A4打印_带备注 PDF");
        template.setTemplateCode("SALES_ORDER_YINGJIE_A4_REMARK_PDF");
        template.setTemplateHtml("{}");
        template.setTemplateType("PDF_FORM");
        template.setEngine("PDF_FORM");
        template.setVersionNo(1);
        template.setStatus("ACTIVE");
        return template;
    }

    private MockMultipartFile jsonFile(String filename, String content) {
        return new MockMultipartFile(
                "file",
                filename,
                "application/json",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String minimalPdfFormLayout() {
        return "{\"static\":[{\"type\":\"text\",\"text\":\"测试\",\"left\":20,\"top\":20,\"width\":100,\"height\":20}]}";
    }
}
