package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
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
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse" -> false;
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse" -> false;
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
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse" -> false;
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse" -> false;
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
        PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
        return new PrintTemplateService(
                repository,
                companySettingRepository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog(),
                new PrintPdfFormTemplateValidator(objectMapper),
                runtimeProperties
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
    void shouldCreateTemplateWithSettlementCompany() {
        PrintTemplateService service = service(repository());

        var result = service.create(new PrintTemplateRequest(
                "sales-order",
                "TEST9 模板",
                "TEST9_TEMPLATE",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                330050675528433664L,
                "错误主体",
                1,
                "ACTIVE"
        ));

        assertThat(result.settlementCompanyId()).isEqualTo("330050675528433664");
        assertThat(result.settlementCompanyName()).isEqualTo("TEST9");
    }

    @Test
    void shouldIgnoreSettlementCompanyNameWithoutId() {
        PrintTemplateService service = service(repository());

        var result = service.create(new PrintTemplateRequest(
                "sales-order",
                "未关联模板",
                "UNASSIGNED_TEMPLATE",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                null,
                "伪造主体",
                1,
                "ACTIVE"
        ));

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isNull();
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

    @Test
    void shouldRejectNullEmptyOrBlankUploadJsonFile() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
        assertThatThrownBy(() -> service.uploadJson(
                1L,
                new MockMultipartFile("file", "layout.json", "application/json", new byte[0])
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("layout.json", "   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("", minimalPdfFormLayout())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
    }

    @Test
    void shouldReturnBillTypeFromTemplate() {
        PrintTemplate template = pdfTemplate();
        template.setBillType("purchase-order");
        PrintTemplateService service = service(repository(template));

        assertThat(service.getBillType(1L)).isEqualTo("purchase-order");
    }

    @Test
    void shouldRejectDuplicateNameOnCreate() {
        PrintTemplateService service = service(repositoryWithDuplicates(true, false));

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名打印模板");
    }

    @Test
    void shouldRejectDuplicateCodeOnCreate() {
        PrintTemplateService service = service(repositoryWithDuplicates(false, true));

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同编码打印模板");
    }

    @Test
    void shouldRejectDuplicateNameOnUpdateWhenChanged() {
        PrintTemplate template = coordTemplate();
        PrintTemplateService service = service(repositoryWithTemplateAndDuplicates(template, true, false));

        assertThatThrownBy(() -> service.update(1L, request(
                "purchase-order",
                "其他模板",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名打印模板");
    }

    @Test
    void shouldRejectDuplicateCodeOnUpdateWhenChanged() {
        PrintTemplate template = coordTemplate();
        PrintTemplateService service = service(repositoryWithTemplateAndDuplicates(template, false, true));

        assertThatThrownBy(() -> service.update(1L, request(
                "purchase-order",
                "模板A",
                "TPL_B",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同编码打印模板");
    }

    @Test
    void shouldAllowDuplicateChecksWhenUpdateKeepsSameEntityNameAndCode() {
        PrintTemplate template = coordTemplate();
        PrintTemplateService service = service(repositoryWithTemplateAndDuplicates(template, true, true));

        var response = service.update(1L, request(
                "purchase-order",
                " 模板A ",
                " TPL_A ",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        ));

        assertThat(response.templateName()).isEqualTo("模板A");
        assertThat(response.templateCode()).isEqualTo("TPL_A");
    }

    @Test
    void shouldUpdateTemplateAndKeepExistingCodeWhenBlank() {
        PrintTemplate template = coordTemplate();
        PrintTemplateService service = service(repository(template));

        var response = service.update(1L, request(
                "purchase-order",
                " 模板A更新 ",
                " ",
                " LODOP.PRINT_INIT('安全内容'); ",
                " coord ",
                " lodop ",
                "ignored.pdf",
                2,
                " disabled "
        ));

        assertThat(response.templateName()).isEqualTo("模板A更新");
        assertThat(response.templateCode()).isEqualTo("TPL_A");
        assertThat(response.templateHtml()).isEqualTo("LODOP.PRINT_INIT('安全内容');");
        assertThat(response.templateType()).isEqualTo("COORD");
        assertThat(response.engine()).isEqualTo("LODOP");
        assertThat(response.assetRef()).isNull();
        assertThat(response.status()).isEqualTo("DISABLED");
    }

    @Test
    void shouldRejectUpdateWhenSettlementCompanyMissing() {
        PrintTemplate template = coordTemplate();
        PrintTemplateService service = service(repository(template), companySettingRepository(Optional.empty()));

        assertThatThrownBy(() -> service.update(1L, new PrintTemplateRequest(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                404L,
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结算主体不存在");
    }

    @Test
    void shouldRejectPdfFormWithLodopEngine() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                minimalPdfFormLayout(),
                "PDF_FORM",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF_FORM 模板必须使用 PDF_FORM 引擎");
    }

    @Test
    void shouldRejectCoordWithPdfFormEngine() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "PDF_FORM",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COORD 模板必须使用 LODOP 引擎");
    }

    @Test
    void shouldRejectUnsupportedEngine() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "HTML",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("渲染引擎仅支持");
    }

    @Test
    void shouldRejectInvalidVersionAndStatus() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                0,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板版本号必须大于 0");
        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "DELETED"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板状态仅支持");
    }

    @Test
    void shouldNormalizeTemplateCodeAndAssetRef() {
        PrintTemplateService service = service(repository());

        var coord = service.create(request(
                "purchase-order",
                "模板A",
                " tpl 中文@1 ",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                null,
                "ignored.pdf",
                1,
                "ACTIVE"
        ));
        var pdf = service.create(request(
                "purchase-order",
                "模板B",
                "PDF_CODE",
                minimalPdfFormLayout(),
                "PDF_FORM",
                null,
                " print-forms/base.PDF ",
                1,
                "ACTIVE"
        ));

        assertThat(coord.templateCode()).isEqualTo("TPL____1");
        assertThat(coord.assetRef()).isNull();
        assertThat(pdf.engine()).isEqualTo("PDF_FORM");
        assertThat(pdf.assetRef()).isEqualTo("print-forms/base.PDF");
    }

    @Test
    void shouldRejectPdfFormWithInvalidJsonContent() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                "[]",
                "PDF_FORM",
                "PDF_FORM",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON 对象");
    }

    @Test
    void shouldRejectNullOrBlankUploadFileNameAndIoFailure() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile(null, minimalPdfFormLayout())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("", minimalPdfFormLayout())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
        assertThatThrownBy(() -> service.uploadJson(1L, jsonFile("   ", minimalPdfFormLayout())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
        assertThatThrownBy(() -> service.uploadJson(1L, failingJsonFile()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取上传 JSON 文件失败");
    }

    @Test
    void shouldStripBomAndSimpleFilenameOnUpload() {
        PrintTemplate template = pdfTemplate();
        template.setVersionNo(null);
        PrintTemplateService service = service(repository(template));
        String content = minimalPdfFormLayout();

        var response = service.uploadJson(1L, jsonFile("C:\\temp\\layout.json", "\uFEFF " + content + " "));

        assertThat(response.templateHtml()).isEqualTo(content);
        assertThat(response.versionNo()).isEqualTo(2);
    }

    @Test
    void shouldRejectNullBillTypeAndNonPrintableCatalogModule() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.listByBillType(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不能为空");
        assertThatThrownBy(() -> service.listByBillType("material"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不合法");
    }

    @Test
    void shouldRejectBlankTemplateNameAndNullCoordTemplateHtml() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "   ",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称不能为空");
        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "TPL_A",
                null,
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容不能为空");
    }

    @Test
    void shouldDefaultBlankTemplateCodeTypeEngineAndStatusOnCreate() {
        PrintTemplateService service = service(repository());

        var response = service.create(request(
                "purchase-order",
                "模板A",
                "   ",
                "LODOP.PRINT_INIT('安全内容');",
                "   ",
                "   ",
                null,
                null,
                "   "
        ));

        assertThat(response.templateCode()).startsWith("TPL_");
        assertThat(response.templateType()).isEqualTo("COORD");
        assertThat(response.engine()).isEqualTo("LODOP");
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectTemplateCodeThatNormalizesToBlank() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                String.valueOf((char) 0),
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板编码不合法");
    }

    @Test
    void shouldKeepExistingCodeWhenUpdateRequestCodeIsNull() {
        PrintTemplate template = coordTemplate();
        PrintTemplateService service = service(repository(template));

        var response = service.update(1L, request(
                "purchase-order",
                "模板A更新",
                null,
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        ));

        assertThat(response.templateCode()).isEqualTo("TPL_A");
        assertThat(response.templateName()).isEqualTo("模板A更新");
    }

    @Test
    void shouldRejectNullOrBlankExistingTemplateCodeOnUpdateWhenRequestCodeDefaultsToExisting() {
        PrintTemplate nullCodeTemplate = coordTemplate();
        nullCodeTemplate.setTemplateCode(null);
        PrintTemplateService nullCodeService = service(repository(nullCodeTemplate));

        assertThatThrownBy(() -> nullCodeService.update(1L, request(
                "purchase-order",
                "模板A",
                null,
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板编码不能为空");

        PrintTemplate blankCodeTemplate = coordTemplate();
        blankCodeTemplate.setTemplateCode("   ");
        PrintTemplateService blankCodeService = service(repository(blankCodeTemplate));

        assertThatThrownBy(() -> blankCodeService.update(1L, request(
                "purchase-order",
                "模板A",
                null,
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板编码不能为空");
    }

    @Test
    void shouldUseDefaultPdfLayoutWhenPdfHtmlAndAssetRefAreBlank() {
        PrintTemplateService service = service(repository());

        var response = service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                "   ",
                "PDF_FORM",
                "   ",
                "   ",
                null,
                "   "
        ));

        assertThat(response.templateHtml()).contains("采购单");
        assertThat(response.engine()).isEqualTo("PDF_FORM");
        assertThat(response.assetRef()).isNull();
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldRejectPdfFormDefaultLayoutReadFailure() {
        PrintTemplateService service = service(repository(), runtimePropertiesWithMissingDefaultLayout());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                null,
                "PDF_FORM",
                null,
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("读取 PDF_FORM 默认布局失败");
    }

    @Test
    void shouldRejectPdfFormAssetRefStartingWithSlashOrWithoutPdfExtension() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                minimalPdfFormLayout(),
                "PDF_FORM",
                "PDF_FORM",
                "/print-forms/base.pdf",
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 底版资源路径不合法");
        assertThatThrownBy(() -> service.create(request(
                "purchase-order",
                "模板A",
                "PDF_CODE",
                minimalPdfFormLayout(),
                "PDF_FORM",
                "PDF_FORM",
                "print-forms/base.txt",
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PDF 底版资源路径不合法");
    }

    @Test
    void shouldRejectDuplicateNameWhenBillTypeOrSettlementCompanyChanged() {
        PrintTemplate billTypeChangedTemplate = coordTemplate();
        PrintTemplateService billTypeChangedService = service(
                repositoryWithTemplateAndDuplicates(billTypeChangedTemplate, true, false)
        );

        assertThatThrownBy(() -> billTypeChangedService.update(1L, request(
                "sales-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名打印模板");

        PrintTemplate companyChangedTemplate = coordTemplate();
        PrintTemplateService companyChangedService = service(
                repositoryWithTemplateAndDuplicates(companyChangedTemplate, true, false)
        );

        assertThatThrownBy(() -> companyChangedService.update(1L, new PrintTemplateRequest(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                330050675528433664L,
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同名打印模板");
    }

    @Test
    void shouldRejectDuplicateCodeWhenBillTypeOrSettlementCompanyChanged() {
        PrintTemplate billTypeChangedTemplate = coordTemplate();
        PrintTemplateService billTypeChangedService = service(
                repositoryWithTemplateAndDuplicates(billTypeChangedTemplate, false, true)
        );

        assertThatThrownBy(() -> billTypeChangedService.update(1L, request(
                "sales-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同编码打印模板");

        PrintTemplate companyChangedTemplate = coordTemplate();
        PrintTemplateService companyChangedService = service(
                repositoryWithTemplateAndDuplicates(companyChangedTemplate, false, true)
        );

        assertThatThrownBy(() -> companyChangedService.update(1L, new PrintTemplateRequest(
                "purchase-order",
                "模板A",
                "TPL_A",
                "LODOP.PRINT_INIT('安全内容');",
                "COORD",
                "LODOP",
                null,
                330050675528433664L,
                null,
                1,
                "ACTIVE"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同编码打印模板");
    }

    @Test
    void shouldRejectUploadWhenBytesAreEmptyOrTooLargeAfterFileValidation() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(1L, emptyBytesAfterValidationJsonFile()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
        assertThatThrownBy(() -> service.uploadJson(1L, oversizedBytesAfterValidationJsonFile()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能超过 1MB");
    }

    @Test
    void shouldRejectUploadWhenOriginalFilenameOverrideIsNullOrBlank() {
        PrintTemplateService service = service(repository(pdfTemplate()));

        assertThatThrownBy(() -> service.uploadJson(
                1L,
                jsonFileWithOriginalFilename(null, minimalPdfFormLayout())
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
        assertThatThrownBy(() -> service.uploadJson(
                1L,
                jsonFileWithOriginalFilename("   ", minimalPdfFormLayout())
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
    }

    @Test
    void shouldRejectNullPdfTemplateContentWhenValidatedDefensively() {
        PrintTemplateService service = service(repository());

        assertThatThrownBy(() -> invokeValidateTemplateContent(service, null, "PDF_FORM"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是合法 JSON");
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

    private PrintTemplateRepository repositoryWithDuplicates(boolean duplicateName, boolean duplicateCode) {
        return (PrintTemplateRepository) Proxy.newProxyInstance(
                PrintTemplateRepository.class.getClassLoader(),
                new Class[]{PrintTemplateRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse" -> duplicateName;
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse" -> duplicateCode;
                    case "save" -> args[0];
                    case "toString" -> "PrintTemplateRepositoryDuplicateStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PrintTemplateRepository repositoryWithTemplateAndDuplicates(
            PrintTemplate template,
            boolean duplicateName,
            boolean duplicateCode
    ) {
        return (PrintTemplateRepository) Proxy.newProxyInstance(
                PrintTemplateRepository.class.getClassLoader(),
                new Class[]{PrintTemplateRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateNameAndDeletedFlagFalse" -> duplicateName;
                    case "existsByBillTypeAndSettlementCompanyIdAndTemplateCodeAndDeletedFlagFalse" -> duplicateCode;
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(template);
                    case "save" -> args[0];
                    case "toString" -> "PrintTemplateRepositoryDuplicateStub";
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

    private PrintTemplate coordTemplate() {
        PrintTemplate template = new PrintTemplate();
        template.setId(1L);
        template.setBillType("purchase-order");
        template.setTemplateName("模板A");
        template.setTemplateCode("TPL_A");
        template.setTemplateHtml("LODOP.PRINT_INIT('安全内容');");
        template.setTemplateType("COORD");
        template.setEngine("LODOP");
        template.setVersionNo(1);
        template.setStatus("ACTIVE");
        template.setSyncMode("MANUAL");
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

    private MockMultipartFile failingJsonFile() {
        return new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                minimalPdfFormLayout().getBytes(StandardCharsets.UTF_8)
        ) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("failed");
            }
        };
    }

    private MockMultipartFile emptyBytesAfterValidationJsonFile() {
        return new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                new byte[0]
        ) {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return 1;
            }
        };
    }

    private MockMultipartFile oversizedBytesAfterValidationJsonFile() {
        byte[] bytes = new byte[1024 * 1024 + 1];
        return new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                bytes
        ) {
            @Override
            public long getSize() {
                return 1;
            }
        };
    }

    private MockMultipartFile jsonFileWithOriginalFilename(String originalFilename, String content) {
        return new MockMultipartFile(
                "file",
                "layout.json",
                "application/json",
                content.getBytes(StandardCharsets.UTF_8)
        ) {
            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }
        };
    }

    private void invokeValidateTemplateContent(
            PrintTemplateService service,
            String templateHtml,
            String templateType
    ) throws Throwable {
        var method = PrintTemplateService.class.getDeclaredMethod(
                "validateTemplateContent",
                String.class,
                String.class
        );
        method.setAccessible(true);
        try {
            method.invoke(service, templateHtml, templateType);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    private String minimalPdfFormLayout() {
        return "{\"static\":[{\"type\":\"text\",\"text\":\"测试\",\"left\":20,\"top\":20,\"width\":100,\"height\":20}]}";
    }

    private CompanySettingRepository companySettingRepository() {
        return companySettingRepository(Optional.of(companySetting(330050675528433664L, "TEST9")));
    }

    private PrintRuntimeProperties runtimePropertiesWithMissingDefaultLayout() {
        return new PrintRuntimeProperties(new ObjectMapper()) {
            @Override
            String defaultPdfFormLayout(String billType) {
                return "print-forms/missing-default.layout.json";
            }
        };
    }

    private PrintTemplateService service(
            PrintTemplateRepository repository,
            PrintRuntimeProperties runtimeProperties
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new PrintTemplateService(
                repository,
                companySettingRepository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog(),
                new PrintPdfFormTemplateValidator(objectMapper),
                runtimeProperties
        );
    }

    private PrintTemplateService service(
            PrintTemplateRepository repository,
            CompanySettingRepository companySettingRepository
    ) {
        ObjectMapper objectMapper = new ObjectMapper();
        PrintRuntimeProperties runtimeProperties = new PrintRuntimeProperties(objectMapper);
        return new PrintTemplateService(
                repository,
                companySettingRepository,
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog(),
                new PrintPdfFormTemplateValidator(objectMapper),
                runtimeProperties
        );
    }

    private CompanySettingRepository companySettingRepository(Optional<CompanySetting> company) {
        return (CompanySettingRepository) Proxy.newProxyInstance(
                CompanySettingRepository.class.getClassLoader(),
                new Class[]{CompanySettingRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> company;
                    case "toString" -> "CompanySettingRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private CompanySetting companySetting(Long id, String companyName) {
        CompanySetting company = new CompanySetting();
        company.setId(id);
        company.setCompanyName(companyName);
        return company;
    }
}
