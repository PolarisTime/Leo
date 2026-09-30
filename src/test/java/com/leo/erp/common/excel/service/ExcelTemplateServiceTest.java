package com.leo.erp.common.excel.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.annotation.ImportColumn;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导入模板生成测试（SXSSF 流式 + 模板字节缓存）。
 *
 * <p>三条性质：生成结果是<strong>可打开的合法 XLSX</strong>（SXSSF 改造不破坏内容）、
 * 缓存命中后返回<strong>独立副本</strong>（调用方改坏不影响后续下载）、
 * 非法 DTO 仍然 fail-fast。</p>
 */
class ExcelTemplateServiceTest {

    /** 顺序故意写成 2、1，验证按 order 排序而不是按记录组件声明顺序。 */
    record SampleImportRow(
            @ImportColumn(header = "品名", required = true, example = "螺丝", order = 2, regex = "[一-龥A-Za-z0-9]+")
            String name,
            @ImportColumn(header = "数量", required = false, example = "10", order = 1)
            Integer quantity
    ) {
    }

    record EmptyImportRow(String noAnnotation) {
    }

    private ExcelTemplateService service;

    @BeforeEach
    void setUp() {
        service = new ExcelTemplateService();
    }

    @Test
    void generateTemplate_shouldProduceOpenableWorkbookWithOrderedHeaders() throws Exception {
        byte[] bytes = service.generateTemplate(SampleImportRow.class);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            Sheet dataSheet = workbook.getSheet("数据模板");
            Sheet helpSheet = workbook.getSheet("填写说明");
            assertThat(dataSheet).isNotNull();
            assertThat(helpSheet).isNotNull();

            Row header = dataSheet.getRow(0);
            // order=1 的“数量”必须排在 order=2 的“品名”之前
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("数量");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("品名");
            // 帮助页首列为字段名
            assertThat(helpSheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("字段名");
        }
    }

    /** 缓存命中必须返回独立数组：调用方改坏了字节，也不能污染后续下载。 */
    @Test
    void generateTemplate_shouldReturnIndependentCopyPerCall() {
        byte[] first = service.generateTemplate(SampleImportRow.class);
        Arrays.fill(first, (byte) 0);

        byte[] second = service.generateTemplate(SampleImportRow.class);

        assertThat(second)
                .isNotEqualTo(first)
                .isEqualTo(service.generateTemplate(SampleImportRow.class));
        assertThat(second.length).isGreaterThan(0);
        // 首字节是 ZIP 魔数 PK，说明模板没有被上一次的清零破坏
        assertThat(second[0]).isEqualTo((byte) 0x50);
        assertThat(second[1]).isEqualTo((byte) 0x4B);
    }

    /** 并发首次生成：所有调用方都拿到同一份合法模板，且缓存只留一份。 */
    @Test
    void generateTemplate_shouldSurviveConcurrentFirstAccess() throws Exception {
        int threads = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<byte[]>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.generateTemplate(SampleImportRow.class);
                }));
            }
            start.countDown();
            byte[] expected = service.generateTemplate(SampleImportRow.class);
            for (var future : futures) {
                assertThat(future.get(10, java.util.concurrent.TimeUnit.SECONDS))
                        .as("并发生成必须产出与串行一致的模板")
                        .isEqualTo(expected);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void generateTemplate_withoutImportColumn_shouldFailFast() {
        assertThatThrownBy(() -> service.generateTemplate(EmptyImportRow.class))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
