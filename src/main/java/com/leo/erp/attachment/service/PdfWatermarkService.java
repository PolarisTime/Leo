package com.leo.erp.attachment.service;

import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.extgstate.PdfExtGState;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class PdfWatermarkService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PdfWatermarkService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float OPACITY = 0.10f;
    private static final float FONT_SIZE = 24f;
    private static final double ANGLE = Math.toRadians(-35);

    public byte[] apply(InputStream pdfStream, String username) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument doc = new PdfDocument(new PdfReader(pdfStream), new PdfWriter(out));
        int pages = doc.getNumberOfPages();
        String text = username + "  " + LocalDateTime.now().format(TIME_FORMATTER);

        PdfFont font;
        try {
            font = PdfFontFactory.createFont("Helvetica", "WinAnsiEncoding");
        } catch (Exception e) {
            font = PdfFontFactory.createFont();
            log.warn("Helvetica 字体不可用，回退到默认字体: {}", e.getMessage());
        }

        for (int i = 1; i <= pages; i++) {
            com.itextpdf.kernel.geom.Rectangle pageSize = doc.getPage(i).getPageSize();
            float w = pageSize.getWidth();
            float h = pageSize.getHeight();

            PdfCanvas pdfCanvas = new PdfCanvas(doc.getPage(i));
            pdfCanvas.saveState();
            PdfExtGState gs = new PdfExtGState();
            gs.setFillOpacity(OPACITY);
            pdfCanvas.setExtGState(gs);

            Canvas canvas = new Canvas(pdfCanvas, pageSize);
            canvas.setFont(font);
            canvas.setFontSize(FONT_SIZE);
            canvas.setFontColor(DeviceGray.BLACK);

            float stepX = 180f;
            float stepY = 100f;
            for (float y = -h; y < h * 2; y += stepY) {
                for (float x = -w; x < w * 2; x += stepX) {
                    canvas.showTextAligned(
                            new Paragraph(text),
                            x, y, i, TextAlignment.CENTER, VerticalAlignment.MIDDLE,
                            (float) ANGLE
                    );
                }
            }
            canvas.close();
            pdfCanvas.restoreState();
        }

        doc.close();
        return out.toByteArray();
    }
}
