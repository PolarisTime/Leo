package com.leo.erp.master.material.service;

import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.excel.service.ExcelTemplateService;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.web.dto.MaterialImportDTO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class MaterialDocumentService {

    private static final List<String> MATERIAL_EXPORT_HEADERS = List.of(
            "商品编码", "品牌", "材质", "类别", "规格", "长度", "单位", "数量单位", "件重(吨)", "每件支数", "单价", "备注"
    );
    private static final CSVFormat MATERIAL_CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setRecordSeparator("\r\n")
            .build();

    private final MaterialRepository materialRepository;
    private final ExcelExportService excelExportService;
    private final ExcelTemplateService excelTemplateService;

    public MaterialDocumentService(MaterialRepository materialRepository,
                                   ExcelExportService excelExportService,
                                   ExcelTemplateService excelTemplateService) {
        this.materialRepository = materialRepository;
        this.excelExportService = excelExportService;
        this.excelTemplateService = excelTemplateService;
    }

    @Transactional(readOnly = true)
    public byte[] csvTemplate() {
        StringWriter writer = new StringWriter();
        writer.append('﻿');
        try (CSVPrinter printer = new CSVPrinter(writer, MATERIAL_CSV_FORMAT)) {
            printer.printRecord(MATERIAL_EXPORT_HEADERS);
            printer.printRecord(
                    "RB400-18-12", "敬业", "HRB400", "螺纹钢", "18", "12米",
                    "吨", "件", "0.002", "1", "3500.00", "否", "示例数据，可删除"
            );
        } catch (IOException exception) {
            throw new IllegalStateException("生成商品资料导入模板CSV失败", exception);
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String keyword) {
        List<Material> materials = materialRepository.findAll(
                MaterialSearchPolicy.search(keyword),
                MaterialSearchPolicy.DEFAULT_SORT
        );
        StringWriter writer = new StringWriter();
        writer.append('\uFEFF');
        try (CSVPrinter printer = new CSVPrinter(writer, MATERIAL_CSV_FORMAT)) {
            printer.printRecord(MATERIAL_EXPORT_HEADERS);
            for (Material material : materials) {
                printer.printRecord(
                        safe(material.getMaterialCode()),
                        safe(material.getBrand()),
                        safe(material.getMaterial()),
                        safe(material.getCategory()),
                        safe(material.getSpec()),
                        safe(material.getLength()),
                        safe(material.getUnit()),
                        safe(material.getQuantityUnit()),
                        formatDecimal(material.getPieceWeightTon(), PrecisionConstants.WEIGHT_SCALE),
                        material.getPiecesPerBundle() == null ? "" : material.getPiecesPerBundle().toString(),
                        formatDecimal(material.getUnitPrice(), 2),
                        safe(material.getRemark())
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException("导出商品资料CSV失败", exception);
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportSpreadsheet(String keyword) {
        List<Material> materials = materialRepository.findAll(
                MaterialSearchPolicy.search(keyword),
                MaterialSearchPolicy.DEFAULT_SORT
        );
        List<MaterialImportDTO> rows = materials.stream().map(this::toSpreadsheetRow).toList();
        return excelExportService.export(rows, MaterialImportDTO.class);
    }

    @Transactional(readOnly = true)
    public byte[] spreadsheetTemplate() {
        return excelTemplateService.generateTemplate(MaterialImportDTO.class);
    }

    private MaterialImportDTO toSpreadsheetRow(Material material) {
        return new MaterialImportDTO(
                material.getMaterialCode(),
                material.getBrand(),
                material.getMaterial(),
                material.getCategory(),
                material.getSpec(),
                material.getLength(),
                material.getUnit(),
                material.getQuantityUnit(),
                material.getPieceWeightTon() == null ? null : material.getPieceWeightTon().toPlainString(),
                material.getPiecesPerBundle() == null ? null : material.getPiecesPerBundle().toString(),
                material.getUnitPrice() == null ? null : material.getUnitPrice().toPlainString(),
                material.getRemark(),
                material.getMaterialType()
        );
    }

    private String formatDecimal(BigDecimal value, int scale) {
        if (value == null) {
            return "";
        }
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
