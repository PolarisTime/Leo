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

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest("permission-management", "模板A", "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不合法");
    }

    @Test
    void shouldRejectDangerousHtmlTemplate() {
        PrintTemplateService service = new PrintTemplateService(
                repository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog()
        );

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest(
                "purchase-order",
                "模板A",
                "<img src=x onerror=alert(1)>",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容包含不允许的脚本或危险标签");
    }

    @Test
    void shouldRejectDangerousLodopTemplate() {
        PrintTemplateService service = new PrintTemplateService(
                repository(),
                new SnowflakeIdGenerator(0L),
                mapper(),
                new ModuleCatalog()
        );

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest(
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

    @Test
    void shouldRejectEmptyBillType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest("", "模板A", "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("适用页面不能为空");
    }

    @Test
    void shouldRejectNullTemplateName() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest("purchase-order", null, "<div/>", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板名称不能为空");
    }

    @Test
    void shouldRejectEmptyTemplateHtml() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest("purchase-order", "模板A", "", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板内容不能为空");
    }

    @Test
    void shouldRejectInvalidTemplateType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest("purchase-order", "模板A", "<div/>", "INVALID")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模板类型仅支持");
    }

    @Test
    void shouldAllowPdfFormTypeWithDangerousHtml() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        // PDF_FORM 类型不检查危险 HTML
        var result = service.create(new PrintTemplateRequest("purchase-order", "模板A", "<script>alert(1)</script>", "PDF_FORM"));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateValidTemplate() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        var result = service.create(new PrintTemplateRequest("purchase-order", "模板A", "<div>安全内容</div>", null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldListByBillType() {
        PrintTemplateService service = new PrintTemplateService(
                repository(), new SnowflakeIdGenerator(0L), mapper(), new ModuleCatalog());

        var result = service.listByBillType("purchase-order");
        assertThat(result).isNotNull();
    }
}
