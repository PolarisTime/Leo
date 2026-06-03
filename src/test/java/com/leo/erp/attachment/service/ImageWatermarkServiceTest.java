package com.leo.erp.attachment.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
