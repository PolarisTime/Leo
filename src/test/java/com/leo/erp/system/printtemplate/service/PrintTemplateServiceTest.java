package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintTemplateServiceTest {

    @Test
    void shouldRejectUnsupportedBillType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog()
        );

        assertThatThrownBy(() -> service.create(request("permission-management", "模板A", "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不合法");
    }

    @Test
    void shouldRejectHtmlTemplateType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog()
        );

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
        PrintTemplateService service = new PrintTemplateService(
                repository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog()
        );

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

    private PrintTemplateMapper mapper() {
        return Mappers.getMapper(PrintTemplateMapper.class);
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

    @Test
    void shouldRejectEmptyBillType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(request("", "模板A", "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不能为空");
    }

    @Test
    void shouldRejectNullTemplateName() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(request("purchase-order", null, "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称不能为空");
    }

    @Test
    void shouldRejectEmptyTemplateHtml() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(request("purchase-order", "模板A", "", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容不能为空");
    }

    @Test
    void shouldRejectInvalidTemplateType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(request("purchase-order", "模板A", "<div/>", "INVALID")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板类型仅支持");
    }

    @Test
    void shouldAllowPdfFormTypeWithoutTemplateHtmlOrAssetRef() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

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
    void shouldRejectPdfFormWithInvalidAssetRef() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

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
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

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
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

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
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        var result = service.listByBillType("purchase-order");
        assertThat(result).isNotNull();
    }
}
