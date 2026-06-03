package com.leo.erp.attachment.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PdfWatermarkServiceTest {

    @Test
    void shouldApplyWatermarkToPdf() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
        Document doc = new Document(pdfDoc);
        doc.add(new Paragraph("Test content"));
        doc.close();

        var service = new PdfWatermarkService();
        byte[] watermarked = service.apply(new ByteArrayInputStream(baos.toByteArray()), "测试用户");

        assertThat(watermarked).isNotEmpty();
        assertThat(watermarked.length).isGreaterThan(baos.size());
    }
}
