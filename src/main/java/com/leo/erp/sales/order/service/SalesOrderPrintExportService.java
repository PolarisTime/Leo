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

    private static final String TEMPLATE_PATH = "print-forms/sales-order-print-v1.xlsx";
    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    private static final int DETAIL_START_ROW = 7;
    private static final int DETAIL_ROWS_PER_SHEET = 7;
    private static final int SUMMARY_ROW = 14;

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderPrintDocumentFactory printDocumentFactory;

    public SalesOrderPrintExportService(
            SalesOrderRepository salesOrderRepository,
            SalesOrderPrintDocumentFactory printDocumentFactory
    ) {
        this.salesOrderRepository = salesOrderRepository;
        this.printDocumentFactory = printDocumentFactory;
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportSalesOrderPrint(Long orderId) {
        return exportSalesOrderPrint(orderId, SalesOrderPrintXlsxOptions.defaults());
    }

    @Transactional(readOnly = true)
    public FileDownloadResponse exportSalesOrderPrint(Long orderId, SalesOrderPrintXlsxOptions options) {
        SalesOrder order = salesOrderRepository.findByIdAndDeletedFlagFalse(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "销售订单不存在"));
        SalesOrderPrintDocument document = printDocumentFactory.create(order, options, DETAIL_ROWS_PER_SHEET);

        try (XSSFWorkbook workbook = loadTemplateWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            int pageCount = document.pages().size();
            for (int pageIndex = 1; pageIndex < pageCount; pageIndex += 1) {
                workbook.cloneSheet(0);
            }

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex += 1) {
                XSSFSheet sheet = workbook.getSheetAt(pageIndex);
                workbook.setSheetName(pageIndex, pageCount == 1 ? "销售订单" : "销售订单-" + (pageIndex + 1));
                if (pageIndex > 0) {
                    copyPrintSetup(workbook.getSheetAt(0), sheet);
                }
                SalesOrderPrintPage page = document.pages().get(pageIndex);
                fillHeader(sheet, document);
                clearDetailRows(sheet);
                fillDetailRows(sheet, page.lines());
                fillSummary(sheet, page);
            }

            workbook.write(output);
            return new FileDownloadResponse(
                    safeFilename(document.orderNo()) + "-套打.xlsx",
                    XLSX_MEDIA_TYPE,
                    output.toByteArray(),
                    document.orderNo(),
                    order.getId(),
                    "sales-order"
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

    private XSSFWorkbook loadTemplateWorkbook() throws IOException {
        try (var input = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            return (XSSFWorkbook) WorkbookFactory.create(input);
        }
    }

    private void fillHeader(Sheet sheet, SalesOrderPrintDocument document) {
        setText(sheet, "A1", document.remark());
        setText(sheet, "B2", document.customerName());
        setText(sheet, "B4", document.projectName());
        setText(sheet, "I4", document.orderNo());

        LocalDate date = document.deliveryDate();
        if (date == null) {
            setText(sheet, "I5", "");
            setText(sheet, "J5", "");
            setText(sheet, "K5", "");
            return;
        }
        setText(sheet, "I5", String.valueOf(date.getYear()));
        setText(sheet, "J5", twoDigits(date.getMonthValue()));
        setText(sheet, "K5", twoDigits(date.getDayOfMonth()));
    }

    private void clearDetailRows(Sheet sheet) {
        for (int rowIndex = DETAIL_START_ROW; rowIndex < DETAIL_START_ROW + DETAIL_ROWS_PER_SHEET; rowIndex += 1) {
            for (int colIndex = 0; colIndex <= 7; colIndex += 1) {
                clearCell(sheet, rowIndex, colIndex);
            }
        }
    }

    private void fillDetailRows(Sheet sheet, List<SalesOrderPrintLine> items) {
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex += 1) {
            SalesOrderPrintLine item = items.get(itemIndex);
            int rowIndex = DETAIL_START_ROW + itemIndex;
            setText(sheet, rowIndex, 0, item.brand());
            setText(sheet, rowIndex, 1, item.category());
            setText(sheet, rowIndex, 2, item.material());
            setText(sheet, rowIndex, 3, item.spec());
            setNumber(sheet, rowIndex, 4, item.quantity());
            setText(sheet, rowIndex, 5, pieceWeight(item));
            setNumber(sheet, rowIndex, 6, item.weightTon());
            setNumber(sheet, rowIndex, 7, item.unitPrice());
        }
    }

    private void fillSummary(Sheet sheet, SalesOrderPrintPage page) {
        setText(sheet, SUMMARY_ROW, 3, "合计件数");
        setNumber(sheet, SUMMARY_ROW, 4, page.totalQuantity());
        setText(sheet, SUMMARY_ROW, 5, "合计吨位");
        setText(
                sheet,
                SUMMARY_ROW,
                6,
                formatDecimal(page.totalWeight(), PrecisionConstants.DISPLAY_WEIGHT_SCALE) + "T"
        );
    }

    private String pieceWeight(SalesOrderPrintLine item) {
        if (isCoil(item.category())) {
            return "-";
        }
        BigDecimal pieceWeight = item.pieceWeightTon();
        if (pieceWeight == null && item.weightTon() != null && item.quantity() != null && item.quantity() > 0) {
            pieceWeight = item.weightTon().divide(
                    BigDecimal.valueOf(item.quantity()),
                    PrecisionConstants.DISPLAY_WEIGHT_SCALE,
                    RoundingMode.HALF_UP
            );
        }
        return formatDecimal(pieceWeight, PrecisionConstants.DISPLAY_WEIGHT_SCALE);
    }

    private boolean isCoil(String category) {
        return "盘螺".equals(category) || "线材".equals(category);
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
