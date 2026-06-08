package com.leo.erp.system.printtemplate.mapper;

import com.leo.erp.system.printtemplate.domain.entity.PrintTemplate;
import com.leo.erp.system.printtemplate.web.dto.PrintTemplateResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PrintTemplateMapperTest {

    private final PrintTemplateMapper mapper = Mappers.getMapper(PrintTemplateMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        PrintTemplate entity = new PrintTemplate();
        entity.setId(1L);
        entity.setBillType("purchase-order");
        entity.setTemplateName("采购订单模板");
        entity.setTemplateCode("PURCHASE_ORDER_DEFAULT");
        entity.setTemplateHtml("<div>模板内容</div>");
        entity.setTemplateType("HTML");
        entity.setEngine("BROWSER_HTML");
        entity.setAssetRef(null);
        entity.setVersionNo(3);
        entity.setStatus("ACTIVE");
        entity.setIsDefault(false);
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 0, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 1, 16, 12, 0, 0));

        PrintTemplateResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.billType()).isEqualTo("purchase-order");
        assertThat(response.templateName()).isEqualTo("采购订单模板");
        assertThat(response.templateCode()).isEqualTo("PURCHASE_ORDER_DEFAULT");
        assertThat(response.templateHtml()).isEqualTo("<div>模板内容</div>");
        assertThat(response.templateType()).isEqualTo("HTML");
        assertThat(response.engine()).isEqualTo("BROWSER_HTML");
        assertThat(response.assetRef()).isNull();
        assertThat(response.versionNo()).isEqualTo(3);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.createTime()).isEqualTo(LocalDateTime.of(2026, 1, 15, 10, 0, 0));
        assertThat(response.updateTime()).isEqualTo(LocalDateTime.of(2026, 1, 16, 12, 0, 0));
    }

    @Test
    void shouldMapWithDefaultType() {
        PrintTemplate entity = new PrintTemplate();
        entity.setId(2L);
        entity.setBillType("sales-order");
        entity.setTemplateName("销售订单模板");
        entity.setTemplateCode("SALES_ORDER_DEFAULT");
        entity.setTemplateHtml("<div>销售模板</div>");
        entity.setIsDefault(true);

        PrintTemplateResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.billType()).isEqualTo("sales-order");
    }
}
