package com.leo.erp.report;

import com.leo.erp.report.inventory.web.dto.InventoryReportItemResponse;
import com.leo.erp.report.inventory.web.dto.InventoryReportResponse;
import com.leo.erp.report.io.web.dto.IoReportResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ReportStableIdentityContractTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/leo/erp/report");

    @Test
    void ioReportShouldExposeRealLineAndMasterDataIds() throws IOException {
        assertRecordComponent(IoReportResponse.class, "id", Long.class);
        assertRecordComponent(IoReportResponse.class, "sourceDocumentId", Long.class);
        assertRecordComponent(IoReportResponse.class, "materialId", Long.class);
        assertRecordComponent(IoReportResponse.class, "warehouseId", Long.class);

        String source = readSource("io/repository/IoReportQueryRepository.java");
        assertThat(source).doesNotContain("ROW_NUMBER()");
        assertThat(source).contains("item.id AS id");
        assertThat(source).contains("item.material_id");
        assertThat(source).contains("item.warehouse_id");
    }

    @Test
    void inventoryReportShouldAggregateByStableStockIdentity() throws IOException {
        assertRecordComponent(InventoryReportResponse.class, "materialId", Long.class);
        assertRecordComponent(InventoryReportItemResponse.class, "id", Long.class);
        assertRecordComponent(InventoryReportItemResponse.class, "materialId", Long.class);
        assertRecordComponent(InventoryReportItemResponse.class, "warehouseId", Long.class);

        String source = readSource("inventory/repository/InventoryReportQueryRepository.java");
        assertThat(source).doesNotContain("ROW_NUMBER()");
        assertThat(source).doesNotContain("material.material_code = stock.material_code");
        assertThat(source).contains("movement.material_id", "movement.warehouse_id");
        assertThat(source).contains("material.id = stock.material_id");
    }

    private String readSource(String relativePath) throws IOException {
        return Files.readString(SOURCE_ROOT.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private void assertRecordComponent(Class<?> recordType, String name, Class<?> type) {
        var component = Arrays.stream(recordType.getRecordComponents())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst();
        assertThat(component)
                .as(recordType.getSimpleName() + " 应包含字段 " + name)
                .isPresent();
        assertThat(component.orElseThrow().getType()).isEqualTo(type);
    }
}
