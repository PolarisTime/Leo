package com.leo.erp.attachment.mapper;

import com.leo.erp.attachment.service.PageUploadRuleDetail;
import com.leo.erp.attachment.service.UpdatePageUploadRuleCommand;
import com.leo.erp.attachment.web.dto.UploadRuleRequest;
import com.leo.erp.attachment.web.dto.UploadRuleResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRuleWebMapperImplTest {

    private final UploadRuleWebMapper mapper = new UploadRuleWebMapperImpl();

    @Test
    void shouldMapDetailToResponse() {
        PageUploadRuleDetail detail = new PageUploadRuleDetail(
                10L,
                "sales-order",
                "销售订单",
                "SO_ATTACHMENT",
                "销售订单附件",
                "{module}-{date}-{filename}",
                "正常",
                "业务附件",
                "SO-202607.pdf"
        );

        UploadRuleResponse response = mapper.toResponse(detail);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.moduleKey()).isEqualTo("sales-order");
        assertThat(response.moduleName()).isEqualTo("销售订单");
        assertThat(response.ruleCode()).isEqualTo("SO_ATTACHMENT");
        assertThat(response.ruleName()).isEqualTo("销售订单附件");
        assertThat(response.renamePattern()).isEqualTo("{module}-{date}-{filename}");
        assertThat(response.status()).isEqualTo("正常");
        assertThat(response.remark()).isEqualTo("业务附件");
        assertThat(response.previewFileName()).isEqualTo("SO-202607.pdf");
    }

    @Test
    void shouldMapRequestToCommand() {
        UploadRuleRequest request = new UploadRuleRequest(
                "{recordNo}-{filename}",
                "禁用",
                "暂停上传"
        );

        UpdatePageUploadRuleCommand command = mapper.toCommand(request);

        assertThat(command.renamePattern()).isEqualTo("{recordNo}-{filename}");
        assertThat(command.status()).isEqualTo("禁用");
        assertThat(command.remark()).isEqualTo("暂停上传");
    }

    @Test
    void shouldReturnNullWhenSourceIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toCommand(null)).isNull();
    }
}
