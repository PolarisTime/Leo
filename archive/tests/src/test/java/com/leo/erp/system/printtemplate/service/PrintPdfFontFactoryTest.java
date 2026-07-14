package com.leo.erp.system.printtemplate.service;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrintPdfFontFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReturnFirstReadableSimsunFont() throws IOException {
        PrintPdfFontFactory factory = new PrintPdfFontFactory();
        PdfFont expectedFont = Mockito.mock(PdfFont.class);

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class);
             MockedStatic<PdfFontFactory> fonts = Mockito.mockStatic(PdfFontFactory.class)) {
            files.when(() -> Files.isReadable(Mockito.any(Path.class))).thenReturn(true);
            fonts.when(() -> PdfFontFactory.createFont(
                    "/usr/share/fonts/truetype/windows/simsun.ttc,0",
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            )).thenReturn(expectedFont);

            PdfFont font = factory.createChineseFont();

            assertThat(font).isSameAs(expectedFont);
        }
    }

    @Test
    void shouldFallbackToDefaultFontWhenChineseFontCreationFails() throws IOException {
        PrintPdfFontFactory factory = new PrintPdfFontFactory();
        PdfFont defaultFont = Mockito.mock(PdfFont.class);

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class);
             MockedStatic<PdfFontFactory> fonts = Mockito.mockStatic(PdfFontFactory.class)) {
            files.when(() -> Files.isReadable(Mockito.any(Path.class))).thenReturn(false);
            fonts.when(() -> PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H"))
                    .thenThrow(new IOException("missing builtin font"));
            fonts.when(PdfFontFactory::createFont).thenReturn(defaultFont);

            PdfFont font = factory.createChineseFont();

            assertThat(font).isSameAs(defaultFont);
        }
    }

    @Test
    void shouldReturnReadableFallbackChineseFontWhenBuiltinFontFails() throws IOException {
        PrintPdfFontFactory factory = new PrintPdfFontFactory();
        PdfFont fallbackFont = Mockito.mock(PdfFont.class);
        String fallbackPath = "/usr/share/fonts/google-droid-sans-fonts/DroidSansFallbackFull.ttf";

        try (MockedStatic<Files> files = Mockito.mockStatic(Files.class);
             MockedStatic<PdfFontFactory> fonts = Mockito.mockStatic(PdfFontFactory.class)) {
            files.when(() -> Files.isReadable(Mockito.any(Path.class)))
                    .thenAnswer(invocation -> fallbackPath.equals(invocation.getArgument(0).toString()));
            fonts.when(() -> PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H"))
                    .thenThrow(new IOException("missing builtin font"));
            fonts.when(() -> PdfFontFactory.createFont(
                    fallbackPath,
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            )).thenReturn(fallbackFont);

            PdfFont font = factory.createChineseFont();

            assertThat(font).isSameAs(fallbackFont);
        }
    }

    @Test
    void shouldAppendCollectionIndexForUppercaseTtcFontPath() throws IOException {
        Path fontPath = Files.createFile(tempDir.resolve("custom.TTC"));
        PrintPdfFontFactory factory = new PrintPdfFontFactory();
        PdfFont expectedFont = Mockito.mock(PdfFont.class);

        try (MockedStatic<PdfFontFactory> fonts = Mockito.mockStatic(PdfFontFactory.class)) {
            fonts.when(() -> PdfFontFactory.createFont(
                    fontPath + ",0",
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            )).thenReturn(expectedFont);

            PdfFont font = ReflectionTestUtils.invokeMethod(factory, "createFontFromPath", fontPath.toString());

            assertThat(font).isSameAs(expectedFont);
        }
    }

    @Test
    void shouldReturnFallbackWhenLatinFontCreationFails() throws IOException {
        PrintPdfFontFactory factory = new PrintPdfFontFactory();
        PdfFont fallback = Mockito.mock(PdfFont.class);

        try (MockedStatic<PdfFontFactory> fonts = Mockito.mockStatic(PdfFontFactory.class)) {
            fonts.when(() -> PdfFontFactory.createFont(StandardFonts.HELVETICA))
                    .thenThrow(new IOException("latin font unavailable"));

            PdfFont font = factory.createLatinFont(fallback);

            assertThat(font).isSameAs(fallback);
        }
    }

    @Test
    void shouldReturnNullWhenReadableFontPathCannotBeCreated() throws IOException {
        Path fontPath = Files.createFile(tempDir.resolve("broken.ttf"));
        PrintPdfFontFactory factory = new PrintPdfFontFactory();

        try (MockedStatic<PdfFontFactory> fonts = Mockito.mockStatic(PdfFontFactory.class)) {
            fonts.when(() -> PdfFontFactory.createFont(
                    fontPath.toString(),
                    PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED
            )).thenThrow(new RuntimeException("broken font"));

            PdfFont font = ReflectionTestUtils.invokeMethod(factory, "createFontFromPath", fontPath.toString());

            assertThat(font).isNull();
        }
    }
}
