package com.leo.erp.system.printtemplate.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PrintPdfFontFactory {

    private static final String DEFAULT_FONT_RESOURCE = "fonts/PingFangSC-Regular.ttf";

    private final byte[] defaultFontBytes;

    public PrintPdfFontFactory() {
        try {
            defaultFontBytes = new ClassPathResource(DEFAULT_FONT_RESOURCE).getContentAsByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("无法加载默认 PDF 字体 PingFang SC", ex);
        }
    }

    PdfFont createDefaultFont() throws IOException {
        return PdfFontFactory.createFont(
                defaultFontBytes,
                PdfEncodings.IDENTITY_H,
                EmbeddingStrategy.FORCE_EMBEDDED
        );
    }
}
