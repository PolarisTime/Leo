package com.leo.erp.statement.freight.web;

import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapperImpl;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.service.FreightStatementCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemCommand;
import com.leo.erp.statement.freight.service.FreightStatementItemView;
import com.leo.erp.statement.freight.service.FreightStatementView;
import com.leo.erp.statement.freight.web.dto.FreightStatementRequest;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {
        FreightStatementWebMapperImpl.class,
        com.leo.erp.attachment.mapper.AttachmentWebMapperImpl.class
})
class FreightStatementWebMapperTest {

    @Autowired
    private FreightStatementWebMapper mapper;

    @Test
    void shouldMapRequestToCommand() {
        FreightStatementRequest request = new FreightStatementRequest(
                "FS-001",
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
        assertThat(command.items()).singleElement().extracting(FreightStatementItemCommand::sourceNo, FreightStatementItemCommand::warehouseName)
                .containsExactly("WL-1", "一号库");
    }

    @Test
    void shouldMapViewToResponse() {
        FreightStatementView view = new FreightStatementView(
                1L,
                "FS-001",
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
                        "/api/attachment/9/preview",
                        "/api/attachment/9/download"
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

    @Test
    void shouldReturnNullWhenMappingNullSource() {
        assertThat(mapper.toCommand(null)).isNull();
        assertThat(mapper.toItemCommand(null)).isNull();
        assertThat(mapper.toResponse(null)).isNull();
        assertThat(mapper.toItemResponse(null)).isNull();
    }

    @Test
    void shouldKeepNullItemsWhenMappingStatement() {
        FreightStatementRequest request = new FreightStatementRequest(
                "FS-NULL",
                "SF",
                "顺丰",
                1L,
                "结算公司",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new BigDecimal("0.000"),
                new BigDecimal("0.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "草稿",
                "未签署",
                null,
                null,
                null
        );

        FreightStatementCommand command = mapper.toCommand(request);

        assertThat(command.items()).isNull();
        assertThat(command.settlementCompanyId()).isEqualTo(1L);
        assertThat(command.settlementCompanyName()).isEqualTo("结算公司");

        FreightStatementView view = new FreightStatementView(
                2L,
                "FS-NULL",
                "SF",
                "顺丰",
                1L,
                "结算公司",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                new BigDecimal("0.000"),
                new BigDecimal("0.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "草稿",
                "未签署",
                null,
                List.of(),
                null,
                null
        );

        FreightStatementResponse response = mapper.toResponse(view);

        assertThat(response.items()).isNull();
        assertThat(response.settlementCompanyId()).isEqualTo(1L);
        assertThat(response.settlementCompanyName()).isEqualTo("结算公司");
    }
}
