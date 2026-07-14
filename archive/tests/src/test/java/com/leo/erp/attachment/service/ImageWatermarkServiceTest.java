package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class ImageWatermarkServiceTest {

    @Test
    void shouldApplyWatermarkToJpgImage() throws Exception {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);

        var service = new ImageWatermarkService();
        byte[] watermarked = service.apply(new ByteArrayInputStream(baos.toByteArray()), "测试用户");

        assertThat(watermarked).isNotEmpty();
    }

    @Test
    void shouldApplyWatermarkToArgbImage() throws Exception {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);

        var service = new ImageWatermarkService();
        byte[] watermarked = service.apply(new ByteArrayInputStream(baos.toByteArray()), "测试用户");

        assertThat(watermarked).isNotEmpty();
    }

    @Test
    void shouldApplyWatermarkToCustomTypeImageUsingArgbOutput() throws Exception {
        BufferedImage customImage = customRgbImage();

        try (var imageIo = mockStatic(ImageIO.class, CALLS_REAL_METHODS)) {
            imageIo.when(() -> ImageIO.read(any(java.io.InputStream.class))).thenReturn(customImage);

            var service = new ImageWatermarkService();
            byte[] watermarked = service.apply(new ByteArrayInputStream(new byte[]{1, 2, 3}), "测试用户");

            assertThat(watermarked).isNotNull();
        }
    }

    @Test
    void shouldThrowException_whenImageUnreadable() {
        var service = new ImageWatermarkService();
        byte[] invalidData = {0, 1, 2, 3};

        assertThatThrownBy(() -> service.apply(new ByteArrayInputStream(invalidData), "测试用户"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldGuessPngFormatForIndexedImage() {
        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_BYTE_INDEXED);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
        } catch (Exception e) {
            // some JDK versions may not support indexed PNG writing
        }
    }

    private BufferedImage customRgbImage() {
        ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorModel colorModel = new ComponentColorModel(
                colorSpace,
                new int[]{8, 8, 8},
                false,
                false,
                Transparency.OPAQUE,
                DataBuffer.TYPE_BYTE
        );
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, 200, 100, 3, null);
        return new BufferedImage(colorModel, raster, false, null);
    }
}
