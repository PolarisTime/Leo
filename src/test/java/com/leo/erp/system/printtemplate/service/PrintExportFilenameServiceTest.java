package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.master.api.ProjectQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PrintExportFilenameService 极端情况测试（纯文件名构建）：
 * 全组件/空组件、日期格式、项目缩写回退链、超长截断、控制字符清洗、扩展名清洗。
 * <p>
 * 使用真实 PrintRuntimeProperties（读取 classpath print-runtime.json）做契约测试：
 * businessNoKeys 首键 billNo、dateParts.sourceKeys 含 deliveryDate/outboundDate。
 */
@ExtendWith(MockitoExtension.class)
class PrintExportFilenameServiceTest {

    @Mock
    private ProjectQuery projectQuery;

    private PrintExportFilenameService service;

    @BeforeEach
    void setUp() {
        service = new PrintExportFilenameService(
                projectQuery,
                new PrintRuntimeProperties(new ObjectMapper())
        );
    }

    // ---------- forOrder ----------

    @Test
    void forOrder_shouldBuildFilenameWithAllComponents() {
        // 注意 forOrder 的日期用 LocalDate.toString()（短横线），非 PDF 中文格式。
        String filename = service.forOrder(
                "CG20260810", LocalDate.of(2026, 8, 10), null, "嘉兴项目", "结算公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.2026-08-10.嘉兴项目.结算公司.pdf");
    }

    @Test
    void forOrder_shouldUseDashWhenBusinessDateNull() {
        String filename = service.forOrder(
                "CG20260810", null, null, "嘉兴项目", "结算公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.-.嘉兴项目.结算公司.pdf");
    }

    @Test
    void forOrder_shouldResolveProjectShortNameFromQuery() {
        when(projectQuery.findActiveById(5L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(5L, "嘉兴项目", "杭政", 1L, "P001")
        ));

        String filename = service.forOrder(
                "CG20260810", LocalDate.of(2026, 8, 10), 5L, "嘉兴项目", "结算公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.2026-08-10.杭政.结算公司.pdf");
    }

    @Test
    void forOrder_shouldFallbackToProjectNameWhenAbbreviatedNameBlank() {
        when(projectQuery.findActiveById(5L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(5L, "嘉兴项目", "  ", 1L, "P001")
        ));

        String filename = service.forOrder(
                "CG20260810", LocalDate.of(2026, 8, 10), 5L, "嘉兴项目", "结算公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.2026-08-10.嘉兴项目.结算公司.pdf");
    }

    @Test
    void forOrder_shouldFallbackToProjectNameWhenProjectNotFound() {
        when(projectQuery.findActiveById(5L)).thenReturn(Optional.empty());

        String filename = service.forOrder(
                "CG20260810", LocalDate.of(2026, 8, 10), 5L, "嘉兴项目", "结算公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.2026-08-10.嘉兴项目.结算公司.pdf");
    }

    @Test
    void forOrder_shouldFallbackToProjectNameWhenProjectIdNull() {
        String filename = service.forOrder(
                "CG20260810", LocalDate.of(2026, 8, 10), null, "嘉兴项目", "结算公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.2026-08-10.嘉兴项目.结算公司.pdf");
        verifyNoInteractions(projectQuery);
    }

    @Test
    void forOrder_shouldTruncateOverlongComponents() {
        // 业务号 65→64、项目/公司 73→72（BUSINESS_NO_MAX_LENGTH=64/PROJECT_MAX_LENGTH=72/COMPANY_MAX_LENGTH=72）。
        String longBusinessNo = "A".repeat(65);
        String longProject = "B".repeat(73);
        String longCompany = "C".repeat(73);

        String filename = service.forOrder(
                longBusinessNo, LocalDate.of(2026, 8, 10), null, longProject, longCompany, "pdf"
        );

        assertThat(filename).isEqualTo(
                "A".repeat(64) + ".2026-08-10." + "B".repeat(72) + "." + "C".repeat(72) + ".pdf"
        );
    }

    @Test
    void forOrder_shouldTruncateByUtf8Bytes() {
        // 中文每字 3 字节：80 字中文 = 240 字节 ≤ 72 上限时按字节截断到 24 个中文字符。
        String longChinese = "嘉".repeat(80);

        String filename = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, longChinese, "公司", "pdf"
        );

        assertThat(filename).isEqualTo("CG1.2026-08-10." + "嘉".repeat(24) + ".公司.pdf");
    }

    @Test
    void forOrder_shouldNotSplitUtf8SurrogatePair() {
        // 增补平面字符（4 字节）放不下时整体丢弃，不产生残缺代理对。
        String emoji = "😀".repeat(40); // 40 × 4 = 160 字节

        String filename = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, emoji, "公司", "pdf"
        );

        // 72 / 4 = 18 个完整 emoji
        assertThat(filename).isEqualTo("CG1.2026-08-10." + "😀".repeat(18) + ".公司.pdf");
    }

    @Test
    void forOrder_shouldSanitizeControlAndReservedCharacters() {
        // 控制字符（<32）与 \/:*?"<>| 统一替换为 '_'。
        String filename = service.forOrder(
                "A:B*C", LocalDate.of(2026, 8, 10), null, "嘉兴\n项目", "结算|公司", "pdf"
        );

        assertThat(filename).isEqualTo("A_B_C.2026-08-10.嘉兴_项目.结算_公司.pdf");
    }

    @Test
    void forOrder_shouldStripTrailingDotsAndSpaces() {
        String filename = service.forOrder(
                "CG20260810", LocalDate.of(2026, 8, 10), null, "嘉兴项目. ", "结算公司..", "pdf"
        );

        assertThat(filename).isEqualTo("CG20260810.2026-08-10.嘉兴项目.结算公司.pdf");
    }

    @Test
    void forOrder_shouldUseDashForEmptyComponent() {
        String filename = service.forOrder(
                "", LocalDate.of(2026, 8, 10), null, "项目A", "", "pdf"
        );

        assertThat(filename).isEqualTo("-.2026-08-10.项目A.-.pdf");
    }

    @Test
    void forOrder_shouldSanitizeAndLowercaseExtension() {
        String withSpaces = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, "项目A", "公司X", "PDF "
        );
        String withDots = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, "项目A", "公司X", ".p.df"
        );
        String withSymbols = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, "项目A", "公司X", "pdf!"
        );

        // 去非字母数字后小写：三种输入均归一为 pdf。
        assertThat(withSpaces).isEqualTo("CG1.2026-08-10.项目A.公司X.pdf");
        assertThat(withDots).isEqualTo("CG1.2026-08-10.项目A.公司X.pdf");
        assertThat(withSymbols).isEqualTo("CG1.2026-08-10.项目A.公司X.pdf");
    }

    @Test
    void forOrder_shouldNotAppendExtensionWhenBlank() {
        String noExtension = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, "项目A", "公司X", ""
        );
        String nullExtension = service.forOrder(
                "CG1", LocalDate.of(2026, 8, 10), null, "项目A", "公司X", null
        );

        assertThat(noExtension).isEqualTo("CG1.2026-08-10.项目A.公司X");
        assertThat(nullExtension).isEqualTo("CG1.2026-08-10.项目A.公司X");
    }

    // ---------- fromPrintData ----------

    @Test
    void fromPrintData_shouldUseDataBusinessNoWhenPresent() {
        String filename = service.fromPrintData(
                Map.of(
                        "billNo", "CG001",
                        "deliveryDate", "2026-08-10",
                        "projectName", "项目A",
                        "settlementCompanyName", "公司X"
                ),
                "fallback-001",
                "pdf"
        );

        assertThat(filename).isEqualTo("CG001.2026年08月10日.项目A.公司X.pdf");
    }

    @Test
    void fromPrintData_shouldUseFallbackBusinessNoWhenDataMissing() {
        String filename = service.fromPrintData(null, "FB001", null);

        assertThat(filename).isEqualTo("FB001.-.-.-");
    }

    @Test
    void fromPrintData_shouldResolveProjectShortNameFromProjectId() {
        when(projectQuery.findActiveById(5L)).thenReturn(Optional.of(
                new ProjectQuery.ProjectSnapshot(5L, "项目A", "杭政", 1L, "P001")
        ));

        String filename = service.fromPrintData(
                Map.of("billNo", "CG001", "projectId", "5", "projectName", "项目A"),
                "FB001",
                "pdf"
        );

        assertThat(filename).isEqualTo("CG001.-.杭政.-.pdf");
    }

    @Test
    void fromPrintData_shouldUseProjectShortNameKeyWhenPresent() {
        String filename = service.fromPrintData(
                Map.of(
                        "billNo", "CG001",
                        "projectShortName", "缩写A",
                        "deliveryDate", "2026-08-10",
                        "settlementCompanyName", "公司X"
                ),
                "FB001",
                "pdf"
        );

        assertThat(filename).isEqualTo("CG001.2026年08月10日.缩写A.公司X.pdf");
        verifyNoInteractions(projectQuery);
    }

    @Test
    void fromPrintData_shouldUseSettlementCompanyName() {
        String filename = service.fromPrintData(
                Map.of("billNo", "CG001", "settlementCompanyName", "嘉兴颖捷建材有限公司"),
                "FB001",
                "xlsx"
        );

        assertThat(filename).isEqualTo("CG001.-.-.嘉兴颖捷建材有限公司.xlsx");
    }

    @Test
    void fromPrintData_shouldFormatPdfDateFromDeliveryAndOutboundKeys() {
        String viaDelivery = service.fromPrintData(
                Map.of("billNo", "CG001", "deliveryDate", "2026-08-10"),
                "FB001",
                "pdf"
        );
        String viaOutbound = service.fromPrintData(
                Map.of("billNo", "CG001", "outboundDate", "2026/8/10"),
                "FB001",
                "pdf"
        );

        assertThat(viaDelivery).isEqualTo("CG001.2026年08月10日.-.-.pdf");
        assertThat(viaOutbound).isEqualTo("CG001.2026年08月10日.-.-.pdf");
    }

    // ---------- formatPdfDate（经 fromPrintData 触发） ----------

    @Test
    void formatPdfDate_shouldUseDashWhenBlank() {
        String filename = service.fromPrintData(Map.of("billNo", "CG001"), "FB001", "pdf");

        assertThat(filename).isEqualTo("CG001.-.-.-.pdf");
    }

    @Test
    void formatPdfDate_shouldReturnDatePartWhenTooFewParts() {
        String filename = service.fromPrintData(
                Map.of("billNo", "CG001", "deliveryDate", "2026"),
                "FB001",
                "pdf"
        );

        assertThat(filename).isEqualTo("CG001.2026.-.-.pdf");
    }

    @Test
    void formatPdfDate_shouldReturnRawWhenInvalidDate() {
        String filename = service.fromPrintData(
                Map.of("billNo", "CG001", "deliveryDate", "2026-13-45"),
                "FB001",
                "pdf"
        );

        // 非法日期抛异常被吞，原串返回。
        assertThat(filename).isEqualTo("CG001.2026-13-45.-.-.pdf");
    }

    @Test
    void formatPdfDate_shouldStripDateTimeSuffix() {
        String filename = service.fromPrintData(
                Map.of("billNo", "CG001", "deliveryDate", "2026-08-10 12:00:00"),
                "FB001",
                "pdf"
        );

        assertThat(filename).isEqualTo("CG001.2026年08月10日.-.-.pdf");
    }
}
