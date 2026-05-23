package com.leo.erp.attachment.service;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class ImageWatermarkService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final float OPACITY = 0.12f;
    private static final int TILE_SPACING = 200;
    private static final int FONT_SIZE = 14;
    private static final double ANGLE = -30.0;

    public byte[] apply(InputStream imageStream, String username) throws IOException {
        BufferedImage original = ImageIO.read(imageStream);
        if (original == null) {
            throw new IOException("无法解析图片文件");
        }
        String text = username + "  " + LocalDateTime.now().format(TIME_FMT);

        BufferedImage watermarked = new BufferedImage(
                original.getWidth(), original.getHeight(), original.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : original.getType());
        Graphics2D g = watermarked.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(original, 0, 0, null);

            g.setColor(Color.DARK_GRAY);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OPACITY));
            Font font = new Font("SansSerif", Font.PLAIN, FONT_SIZE);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int textWidth = fm.stringWidth(text);

            AffineTransform originalTransform = g.getTransform();
            g.rotate(Math.toRadians(ANGLE), original.getWidth() / 2.0, original.getHeight() / 2.0);

            for (int y = -original.getHeight(); y < original.getHeight() * 2; y += TILE_SPACING) {
                for (int x = -original.getWidth(); x < original.getWidth() * 2; x += TILE_SPACING) {
                    g.drawString(text, x - textWidth / 2, y);
                }
            }

            g.setTransform(originalTransform);
        } finally {
            g.dispose();
        }

        String format = guessFormat(original);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(watermarked, format, out);
        return out.toByteArray();
    }

    private String guessFormat(BufferedImage image) {
        switch (image.getType()) {
            case BufferedImage.TYPE_BYTE_BINARY, BufferedImage.TYPE_BYTE_INDEXED -> { return "png"; }
            default -> { return "jpg"; }
        }
    }
}
