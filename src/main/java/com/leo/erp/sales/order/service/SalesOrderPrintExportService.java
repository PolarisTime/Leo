package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocument;
import com.leo.erp.sales.order.service.print.SalesOrderPrintDocumentFactory;
import com.leo.erp.sales.order.service.print.SalesOrderPrintLine;
import com.leo.erp.sales.order.service.print.SalesOrderPrintPage;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayout;
import com.leo.erp.system.printtemplate.service.PrintXlsxExportLayoutProvider;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class SalesOrderPrintExportService {

    private static final String MODULE_KEY = "sales-order";
    private static final String CELL_TYPE_NUMBER = "number";
    private static final String CELL_TYPE_PIECE_WEIGHT = "pieceWeight";
    private static final String FIELD_DELIVERY_DATE = "deliveryDate";
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderPrintDocumentFactory printDocumentFactory;
    private final PrintXlsxExportLayoutProvider layoutProvider;

    public SalesOrderPrintExportService(
            SalesOrderRepository salesOrderRepository,
            SalesOrderPrintDocumentFactory printDocumentFactory,
            PrintXlsxExportLayoutProvider layoutProvider
    ) {
        this.salesOrderRepository = salesOrderRepository;
        this.printDocumentFactory = printDocumentFactory;
        this.layoutProvider = layoutProvider;
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportSalesOrderPrint(Long orderId) {
        return exportSalesOrderPrint(orderId, SalesOrderPrintXlsxOptions.defaults());
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportSalesOrderPrint(Long orderId, SalesOrderPrintXlsxOptions options) {
        SalesOrder order = salesOrderRepository.findByIdAndDeletedFlagFalse(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在"));
        PrintXlsxExportLayout layout = layoutProvider.layout(MODULE_KEY);
        SalesOrderPrintDocument document = printDocumentFactory.create(order, options, layout.rowsPerPage());

        try (XSSFWorkbook workbook = loadTemplateWorkbook(layout); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int pageCount = document.pages().size();
            for (int pageIndex = 1; pageIndex < pageCount; pageIndex += 1) {
                workbook.cloneSheet(0);
            }

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
                XSSFSheet sheet = workbook.getSheetAt(pageIndex);
                workbook.setSheetName(pageIndex, sheetName(layout, pageCount, pageIndex));
                if (pageIndex > 0) {
                    copyPrintSetup(workbook.getSheetAt(0), sheet);
                }
                SalesOrderPrintPage page = document.pages().get(pageIndex);
                fillHeader(sheet, document, layout);
                clearDetailRows(sheet, layout);
                fillDetailRows(sheet, page.lines(), layout);
                fillSummary(sheet, page, layout);
            }

            workbook.write(output);
            return new FileDownloadResponse(
                    safeFilename(document.orderNo()) + layout.filenameSuffix(),
                    XLSX_MEDIA_TYPE,
                    output.toByteArray(),
                    document.orderNo(),
                    order.getId(),
                    layout.moduleKey()
            );
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "销售订单套打 Excel 生成失败");
        }
    }

    private void copyPrintSetup(Sheet source, Sheet target) {
        target.setMargin(Sheet.LeftMargin, source.getMargin(Sheet.LeftMargin));
        target.setMargin(Sheet.RightMargin, source.getMargin(Sheet.RightMargin));
        target.setMargin(Sheet.TopMargin, source.getMargin(Sheet.TopMargin));
        target.setMargin(Sheet.BottomMargin, source.getMargin(Sheet.BottomMargin));
        target.setMargin(Sheet.HeaderMargin, source.getMargin(Sheet.HeaderMargin));
        target.setMargin(Sheet.FooterMargin, source.getMargin(Sheet.FooterMargin));

        PrintSetup sourceSetup = source.getPrintSetup();
        PrintSetup targetSetup = target.getPrintSetup();
        targetSetup.setPaperSize(sourceSetup.getPaperSize());
        targetSetup.setLandscape(sourceSetup.getLandscape());
        targetSetup.setPageStart(sourceSetup.getPageStart());
        targetSetup.setFitWidth(sourceSetup.getFitWidth());
        targetSetup.setFitHeight(sourceSetup.getFitHeight());
        targetSetup.setScale(sourceSetup.getScale());
        target.setAutobreaks(source.getAutobreaks());
        target.setFitToPage(source.getFitToPage());
        target.setHorizontallyCenter(source.getHorizontallyCenter());
        target.setVerticallyCenter(source.getVerticallyCenter());
    }

    private XSSFWorkbook loadTemplateWorkbook(PrintXlsxExportLayout layout) throws IOException {
        try (var input = new ClassPathResource(layout.templateResource()).getInputStream()) {
            return (XSSFWorkbook) WorkbookFactory.create(input);
        }
    }

    private String sheetName(PrintXlsxExportLayout layout, int pageCount, int pageIndex) {
        String baseName = layout.sheetName().isBlank() ? "Sheet" : layout.sheetName();
        return pageCount == 1 ? baseName : baseName + "-" + (pageIndex + 1);
    }

    private void fillHeader(Sheet sheet, SalesOrderPrintDocument document, PrintXlsxExportLayout layout) {
        for (PrintXlsxExportLayout.HeaderCell cell : layout.headerCells()) {
            setText(sheet, cell.cell(), headerValue(document, cell.field()));
        }
    }

    private void clearDetailRows(Sheet sheet, PrintXlsxExportLayout layout) {
        for (int rowIndex = layout.detailStartRow(); rowIndex < layout.detailStartRow() + layout.rowsPerPage(); rowIndex += 1) {
            for (int colIndex = 0; colIndex <= layout.detailEndColumn(); colIndex += 1) {
                clearCell(sheet, rowIndex, colIndex);
            }
        }
    }

    private void fillDetailRows(Sheet sheet, List<SalesOrderPrintLine> items, PrintXlsxExportLayout layout) {
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex += 1) {
            SalesOrderPrintLine item = items.get(itemIndex);
            int rowIndex = layout.detailStartRow() + itemIndex;
            for (PrintXlsxExportLayout.DetailColumn column : layout.detailColumns()) {
                setDetailValue(sheet, rowIndex, item, column, layout.pieceWeight());
            }
        }
    }

    private void fillSummary(Sheet sheet, SalesOrderPrintPage page, PrintXlsxExportLayout layout) {
        for (PrintXlsxExportLayout.SummaryCell cell : layout.summary().cells()) {
            if (CELL_TYPE_NUMBER.equals(cell.type())) {
                setNumber(sheet, layout.summary().row(), cell.column(), summaryNumber(page, cell.field()));
                continue;
            }
            setText(sheet, layout.summary().row(), cell.column(), summaryText(page, cell));
        }
    }

    private void setDetailValue(
            Sheet sheet,
            int rowIndex,
            SalesOrderPrintLine item,
            PrintXlsxExportLayout.DetailColumn column,
            PrintXlsxExportLayout.PieceWeight pieceWeight
    ) {
        if (CELL_TYPE_NUMBER.equals(column.type())) {
            Object value = lineValue(item, column.field());
            if (value instanceof Integer integer) {
                setNumber(sheet, rowIndex, column.column(), integer);
            } else if (value instanceof BigDecimal decimal) {
                setNumber(sheet, rowIndex, column.column(), decimal);
            } else {
                clearCell(sheet, rowIndex, column.column());
            }
            return;
        }
        if (CELL_TYPE_PIECE_WEIGHT.equals(column.type())) {
            setText(sheet, rowIndex, column.column(), pieceWeight(item, pieceWeight));
            return;
        }
        setText(sheet, rowIndex, column.column(), stringValue(lineValue(item, column.field())));
    }

    private String headerValue(SalesOrderPrintDocument document, String field) {
        LocalDate date = document.deliveryDate();
        if ("orderNo".equals(field)) {
            return document.orderNo();
        }
        if ("customerName".equals(field)) {
            return document.customerName();
        }
        if ("projectName".equals(field)) {
            return document.projectName();
        }
        if ("remark".equals(field)) {
            return document.remark();
        }
        if ("deliveryYear".equals(field)) {
            return date == null ? "" : String.valueOf(date.getYear());
        }
        if ("deliveryMonth".equals(field)) {
            return date == null ? "" : twoDigits(date.getMonthValue());
        }
        if ("deliveryDay".equals(field)) {
            return date == null ? "" : twoDigits(date.getDayOfMonth());
        }
        if (FIELD_DELIVERY_DATE.equals(field)) {
            return date == null ? "" : date.toString();
        }
        return "";
    }

    private Object lineValue(SalesOrderPrintLine item, String field) {
        return switch (field) {
            case "brand" -> item.brand();
            case "category" -> item.category();
            case "material" -> item.material();
            case "spec" -> item.spec();
            case "quantity" -> item.quantity();
            case "weightTon" -> item.weightTon();
            case "unitPrice" -> item.unitPrice();
            case "pieceWeightTon" -> item.pieceWeightTon();
            default -> null;
        };
    }

    private Integer summaryNumber(SalesOrderPrintPage page, String field) {
        if ("totalQuantity".equals(field)) {
            return page.totalQuantity();
        }
        return null;
    }

    private String summaryText(SalesOrderPrintPage page, PrintXlsxExportLayout.SummaryCell cell) {
        if (!cell.text().isBlank()) {
            return cell.text();
        }
        if ("totalWeight".equals(cell.field())) {
            return formatDecimal(page.totalWeight(), cell.scale()) + cell.suffix();
        }
        Object value = summaryNumber(page, cell.field());
        return value == null ? "" : String.valueOf(value);
    }

    private String pieceWeight(SalesOrderPrintLine item, PrintXlsxExportLayout.PieceWeight config) {
        if (shouldSuppressPieceWeight(item, config)) {
            return config.replacement();
        }
        BigDecimal pieceWeight = item.pieceWeightTon();
        if (pieceWeight == null && item.weightTon() != null && item.quantity() != null && item.quantity() > 0) {
            pieceWeight = item.weightTon().divide(
                    BigDecimal.valueOf(item.quantity()),
                    config.scale(),
                    RoundingMode.HALF_UP
            );
        }
        return formatDecimal(pieceWeight, config.scale());
    }

    private boolean shouldSuppressPieceWeight(SalesOrderPrintLine item, PrintXlsxExportLayout.PieceWeight config) {
        for (PrintXlsxExportLayout.SuppressRule rule : config.suppressWhen()) {
            String value = stringValue(lineValue(item, rule.field()));
            if (!value.isBlank() && rule.values().contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void setText(Sheet sheet, String address, String value) {
        Cell cell = getCell(sheet, address);
        cell.removeFormula();
        cell.setCellValue(value == null ? "" : value);
    }

    private void setText(Sheet sheet, int rowIndex, int colIndex, String value) {
        Cell cell = getCell(sheet, rowIndex, colIndex);
        cell.removeFormula();
        cell.setCellValue(value == null ? "" : value);
    }

    private void setNumber(Sheet sheet, int rowIndex, int colIndex, Integer value) {
        Cell cell = getCell(sheet, rowIndex, colIndex);
        cell.removeFormula();
        if (value == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(value);
    }

    private void setNumber(Sheet sheet, int rowIndex, int colIndex, BigDecimal value) {
        Cell cell = getCell(sheet, rowIndex, colIndex);
        cell.removeFormula();
        if (value == null) {
            cell.setBlank();
            return;
        }
        cell.setCellValue(value.doubleValue());
    }

    private void clearCell(Sheet sheet, int rowIndex, int colIndex) {
        Cell cell = getCell(sheet, rowIndex, colIndex);
        cell.removeFormula();
        cell.setBlank();
    }

    private Cell getCell(Sheet sheet, String address) {
        CellReference reference = new CellReference(address);
        return getCell(sheet, reference.getRow(), reference.getCol());
    }

    private Cell getCell(Sheet sheet, int rowIndex, int colIndex) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) {
            row = sheet.createRow(rowIndex);
        }
        Cell cell = row.getCell(colIndex);
        if (cell != null) {
            return cell;
        }
        cell = row.createCell(colIndex);
        copyStyleFromRowFirstCell(row, cell);
        return cell;
    }

    private void copyStyleFromRowFirstCell(Row row, Cell cell) {
        Cell firstCell = row.getCell(0);
        if (firstCell == null || firstCell.getCellStyle() == null) {
            return;
        }
        XSSFCellStyle style = (XSSFCellStyle) firstCell.getCellStyle();
        cell.setCellStyle(style);
    }

    private String formatDecimal(BigDecimal value, int scale) {
        if (value == null) {
            return "";
        }
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private String safeFilename(String raw) {
        String value = raw == null || raw.isBlank() ? "销售订单" : raw.trim();
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i += 1) {
            char ch = value.charAt(i);
            builder.append("\\/:*?\"<>|".indexOf(ch) >= 0 ? '_' : ch);
        }
        return builder.toString();
    }
}
