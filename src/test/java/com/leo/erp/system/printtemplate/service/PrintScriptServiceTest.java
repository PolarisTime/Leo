package com.leo.erp.system.printtemplate.service;

import com.leo.erp.attachment.api.AttachmentRecordAccess;
import com.leo.erp.system.printtemplate.repository.PrintTemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PrintScriptService.groupItemsForPdf 分组行构建测试。
 */
@ExtendWith(MockitoExtension.class)
class PrintScriptServiceTest {

    @Mock
    private PrintTemplateRepository templateRepository;

    @Mock
    private PrintRecordDataProvider dataProvider;

    @Mock
    private PrintRecordEnricher recordEnricher;

    @Mock
    private PrintRecordLayoutPreparer layoutPreparer;

    @Mock
    private PrintLayoutLodopRenderer layoutLodopRenderer;

    @Mock
    private AttachmentRecordAccess recordAccessService;

    @Mock
    private PrintRuntimeProperties runtimeProperties;

    @InjectMocks
    private PrintScriptService service;

    private Map<String, String> detail(String sourceNo, String customer, String project, String quantity, String weight, String plate) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("sourceNo", sourceNo);
        item.put("customerName", customer);
        item.put("projectName", project);
        item.put("quantity", quantity);
        item.put("weightTon", weight);
        item.put("vehiclePlate", plate);
        return item;
    }

    @Test
    void groupItemsForPdf_shouldBuildSourceAndProjectRowsWithoutSubtotals() {
        List<Map<String, String>> items = new ArrayList<>();
        // 来源单 A：两个项目分组
        items.add(detail("BIL-A", "客户甲", "项目一", "10", "1.5", "浙F12345"));
        items.add(detail("BIL-A", "客户甲", "项目一", "5", "0.8", "浙F12345"));
        items.add(detail("BIL-A", "客户甲", "项目二", "7", "2.2", "浙F12345"));
        // 来源单 B：单个项目分组
        items.add(detail("BIL-B", "客户乙", "项目三", "3", "2.0", "浙F67890"));

        List<Map<String, String>> rows = service.groupItemsForPdf(items);

        assertThat(rows).hasSize(10);

        // 来源单 A：source 头（车号去重）、project 头一、明细、project 头二、明细
        assertThat(rows.get(0)).containsEntry("isGroupHeader", "source")
                .containsEntry("sourceNo", "BIL-A")
                .containsEntry("vehiclePlate", "浙F12345")
                .containsEntry("totalQuantity", "22")
                .containsEntry("totalWeightTon", "4.5");

        assertThat(rows.get(1)).containsEntry("isGroupHeader", "project")
                .containsEntry("customerName", "客户甲")
                .containsEntry("projectName", "项目一");

        assertThat(rows.get(2)).containsEntry("index", "1");
        assertThat(rows.get(3)).containsEntry("index", "2");

        assertThat(rows.get(4)).containsEntry("isGroupHeader", "project")
                .containsEntry("projectName", "项目二");
        assertThat(rows.get(5)).containsEntry("index", "3");

        // 组间分隔行 + 来源单 B
        assertThat(rows.get(6)).containsEntry("isBlankRow", "true");
        assertThat(rows.get(7)).containsEntry("isGroupHeader", "source")
                .containsEntry("sourceNo", "BIL-B")
                .containsEntry("vehiclePlate", "浙F67890")
                .containsEntry("totalQuantity", "3");
        assertThat(rows.get(8)).containsEntry("isGroupHeader", "project")
                .containsEntry("customerName", "客户乙")
                .containsEntry("projectName", "项目三");
        assertThat(rows.get(9)).containsEntry("index", "1");
    }

    @Test
    void groupItemsForPdf_shouldHandleEmptyItems() {
        assertThat(service.groupItemsForPdf(List.of())).isEmpty();
    }

    @Test
    void groupItemsForPdf_shouldHandleMissingOptionalFields() {
        List<Map<String, String>> items = new ArrayList<>();
        Map<String, String> item = new LinkedHashMap<>();
        item.put("sourceNo", "BIL-X");
        // 缺 quantity / weightTon / vehiclePlate / customerName / projectName
        items.add(item);

        List<Map<String, String>> rows = service.groupItemsForPdf(items);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0)).containsEntry("isGroupHeader", "source")
                .containsEntry("vehiclePlate", "")
                .containsEntry("totalQuantity", "0")
                .containsEntry("totalWeightTon", "0");
        assertThat(rows.get(1)).containsEntry("isGroupHeader", "project")
                .containsEntry("customerName", "");
        assertThat(rows.get(2)).containsEntry("index", "1");
    }

    @Test
    void groupItemsForPdf_shouldMergeSameSourceWhenSourceNoBlank() {
        List<Map<String, String>> items = new ArrayList<>();
        items.add(detail("", "客户甲", "项目一", "2", "1.1", ""));
        items.add(detail("", "客户甲", "项目一", "3", "1.3", ""));

        List<Map<String, String>> rows = service.groupItemsForPdf(items);

        // 空 sourceNo 归入同一 unassigned 组，不再重复组间分隔
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)).containsEntry("isGroupHeader", "source");
        assertThat(rows.get(1)).containsEntry("isGroupHeader", "project");
        assertThat(rows.get(2)).containsEntry("index", "1");
        assertThat(rows.get(3)).containsEntry("index", "2");
        assertThat(rows).noneMatch(row -> "subtotal".equals(row.get("isGroupHeader")));
    }
}
