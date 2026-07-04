package com.leo.erp.attachment.service;

import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class PdfWatermarkServiceTest {

    @Test
    void shouldApplyWatermarkToPdf() throws Exception {
        byte[] pdf = samplePdf();

        var service = new PdfWatermarkService();
        byte[] watermarked = service.apply(new ByteArrayInputStream(pdf), "测试用户");

        assertThat(watermarked).isNotEmpty();
        assertThat(watermarked.length).isGreaterThan(pdf.length);
    }

    @Test
    void shouldFallbackToDefaultFontWhenHelveticaIsUnavailable() throws Exception {
        byte[] pdf = samplePdf();

        try (var fonts = mockStatic(PdfFontFactory.class, CALLS_REAL_METHODS)) {
            fonts.when(() -> PdfFontFactory.createFont("Helvetica", "WinAnsiEncoding"))
                    .thenThrow(new IllegalStateException("font missing"));
            var service = new PdfWatermarkService();

            byte[] watermarked = service.apply(new ByteArrayInputStream(pdf), "测试用户");

            assertThat(watermarked).isNotEmpty();
            assertThat(watermarked.length).isGreaterThan(pdf.length);
        }
    }

    private static byte[] samplePdf() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        Document doc = new Document(pdfDoc);
        doc.add(new Paragraph("Test content"));
        doc.close();
        return baos.toByteArray();
    }
}
