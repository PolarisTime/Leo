package com.leo.erp.system.printtemplate.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PrintPdfFontFactory {

    private static final String DEFAULT_FONT_RESOURCE = "fonts/PingFangSC-Regular.ttf";

    /**
     * FontProgram 为解析后的共享字体数据（线程安全、可跨文档复用），
     * 启动时解析一次；PdfFont 实例不可跨文档复用，每次渲染基于缓存程序创建。
     */
    private final FontProgram defaultFontProgram;

    public PrintPdfFontFactory() {
        try {
            byte[] fontBytes = new ClassPathResource(DEFAULT_FONT_RESOURCE).getContentAsByteArray();
            defaultFontProgram = FontProgramFactory.createFont(fontBytes, true);
        } catch (IOException ex) {
            throw new IllegalStateException("无法加载默认 PDF 字体 PingFang SC", ex);
        }
    }

    PdfFont createDefaultFont() throws IOException {
        return PdfFontFactory.createFont(
                defaultFontProgram,
                PdfEncodings.IDENTITY_H,
                EmbeddingStrategy.FORCE_EMBEDDED
        );
    }
}
