package com.leo.erp.system.printtemplate.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class PrintPdfFontFactory {

    private static final List<String> SIMSUN_FONT_CANDIDATES = List.of(
            "/usr/share/fonts/truetype/windows/simsun.ttc",
            "/usr/share/fonts/truetype/windows/simsun.ttf",
            "/usr/share/fonts/truetype/msttcorefonts/simsun.ttc",
            "/usr/share/fonts/truetype/msttcorefonts/simsun.ttf",
            "/usr/share/fonts/simsun.ttc",
            "/usr/share/fonts/simsun.ttf",
            "/System/Library/Fonts/Supplemental/Songti.ttc",
            "/Library/Fonts/Songti.ttc",
            "C:/Windows/Fonts/simsun.ttc",
            "C:/Windows/Fonts/simsun.ttf"
    );
    private static final List<String> CHINESE_FONT_FALLBACKS = List.of(
            "/usr/share/fonts/google-droid-sans-fonts/DroidSansFallbackFull.ttf",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/wenquanyi/wqy-microhei/wqy-microhei.ttc"
    );

    PdfFont createChineseFont() throws IOException {
        for (String fontPath : SIMSUN_FONT_CANDIDATES) {
            PdfFont font = createFontFromPath(fontPath);
            if (font != null) {
                return font;
            }
        }
        try {
            return PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        } catch (Exception ignored) {
            for (String fontPath : CHINESE_FONT_FALLBACKS) {
                PdfFont font = createFontFromPath(fontPath);
                if (font != null) {
                    return font;
                }
            }
        }
        return PdfFontFactory.createFont();
    }

    PdfFont createLatinFont(PdfFont fallback) {
        try {
            return PdfFontFactory.createFont(StandardFonts.HELVETICA);
        } catch (IOException ex) {
            return fallback;
        }
    }

    private PdfFont createFontFromPath(String fontPath) throws IOException {
        Path path = Path.of(fontPath);
        if (!Files.isReadable(path)) {
            return null;
        }
        String normalizedPath = path.toString();
        try {
            if (normalizedPath.endsWith(".ttc") || normalizedPath.endsWith(".TTC")) {
                normalizedPath = normalizedPath + ",0";
            }
            return PdfFontFactory.createFont(
                    normalizedPath,
                    PdfEncodings.IDENTITY_H,
                    EmbeddingStrategy.PREFER_EMBEDDED
            );
        } catch (Exception ignored) {
            return null;
        }
    }
}
