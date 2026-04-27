package com.leo.erp.statement.freight.web;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.attachment.mapper.AttachmentWebMapper;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.statement.freight.service.FreightStatementCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemView;
import com.leo.erp.statement.freight.service.FreightStatementView;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementWebMapperTest {

    private final FreightStatementWebMapper mapper = new FreightStatementWebMapper(new AttachmentWebMapper());

    @Test
    void shouldMapRequestToCommand() {
        FreightStatementRequest request = new FreightStatementRequest(
                "FS-001",
                "WL-1",
                "顺丰",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                new BigDecimal("10.000"),
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("80.00"),
                "待审核",
                "未签署",
                "附件A",
                List.of(11L, 12L),
                "备注",
                List.of(new FreightBillItemRequest(
                        "WL-1",
                        "客户A",
                        "项目A",
                        "M001",
                        "螺纹钢",
                        "宝钢",
                        "钢材",
                        "HRB400",
                        "18",
                        "12m",
                        10,
                        "支",
                        new BigDecimal("0.500"),
                        2,
                        "B001",
                        new BigDecimal("5.000"),
                        "一号库"
                ))
        );

        FreightStatementCommand command = mapper.toCommand(request);

        assertThat(command.statementNo()).isEqualTo("FS-001");
        assertThat(command.attachmentIds()).containsExactly(11L, 12L);
        assertThat(command.items()).singleElement().extracting(FreightStatementItemCommand::sourceNo, FreightStatementItemCommand::warehouseName)
                .containsExactly("WL-1", "一号库");
    }

    @Test
    void shouldMapViewToResponse() {
        FreightStatementView view = new FreightStatementView(
                1L,
                "FS-001",
                "WL-1",
                "顺丰",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                new BigDecimal("10.000"),
                new BigDecimal("100.00"),
                new BigDecimal("20.00"),
                new BigDecimal("80.00"),
                "待审核",
                "未签署",
                "附件A.pdf",
                List.of(new AttachmentView(
                        9L,
                        "附件A.pdf",
                        "renamed.pdf",
                        "原始.pdf",
                        "application/pdf",
                        123L,
                        "PAGE_UPLOAD",
                        "tester",
                        LocalDateTime.of(2026, 4, 25, 12, 0),
                        true,
                        "pdf",
                        "/api/attachments/9/preview",
                        "/api/attachments/9/download"
                )),
                "备注",
                List.of(new FreightStatementItemView(
                        101L,
                        1,
                        "WL-1",
                        "客户A",
                        "项目A",
                        "M001",
                        "螺纹钢",
                        "宝钢",
                        "钢材",
                        "HRB400",
                        "18",
                        "12m",
                        10,
                        "支",
                        new BigDecimal("0.500"),
                        2,
                        "B001",
                        new BigDecimal("5.000"),
                        "一号库"
                ))
        );

        FreightStatementResponse response = mapper.toResponse(view);

        assertThat(response.attachments()).singleElement()
                .extracting(item -> item.id(), item -> item.name(), item -> item.previewType())
                .containsExactly(9L, "附件A.pdf", "pdf");
        assertThat(response.items()).singleElement()
                .extracting(item -> item.id(), item -> item.sourceNo(), item -> item.warehouseName())
                .containsExactly(101L, "WL-1", "一号库");
    }
}
