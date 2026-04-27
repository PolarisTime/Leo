package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import com.leo.erp.system.printtemplate.mapper.PrintTemplateMapper;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

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

        assertThatThrownBy(() -> service.create(new PrintTemplateRequest("permission-management", "模板A", "<div/>", "1")))
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
                "purchase-orders",
                "模板A",
                "<img src=x onerror=alert(1)>",
                "1"
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
                "purchase-orders",
                "模板A",
                "LODOP.PRINT_INIT('test'); window.alert('xss');",
                "1"
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
                    case "findAllByBillTypeAndDeletedFlagFalseOrderByIsDefaultDescUpdatedAtDescIdDesc" -> java.util.List.of();
                    case "save" -> args[0];
                    case "toString" -> "PrintTemplateRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private PrintTemplateMapper mapper() {
        return new PrintTemplateMapper() {
            @Override
            public com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse toResponse(com.leo.erp.system.printtemplate.domain.entity.PrintTemplate entity) {
                return new com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse(
                        entity.getId(),
                        entity.getTemplateName(),
                        entity.getTemplateHtml(),
                        entity.getIsDefault(),
                        entity.getBillType(),
                        entity.getCreatedAt(),
                        entity.getUpdatedAt()
                );
            }
        };
    }
}
