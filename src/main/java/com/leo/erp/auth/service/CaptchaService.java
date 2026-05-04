package com.leo.erp.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

@Service
public class CaptchaService {

    private static final String CAPTCHA_PREFIX = "auth:captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final String CHAR_POOL = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CHAR_COUNT = 4;
    private static final int IMAGE_WIDTH = 130;
    private static final int IMAGE_HEIGHT = 48;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;

    public CaptchaService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CaptchaResult generate() {
        String code = generateCode();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, code, CAPTCHA_TTL);

        BufferedImage image = renderImage(code);
        String base64Image = encodeToBase64(image);

        return new CaptchaResult(captchaId, base64Image);
    }

    public boolean verify(String captchaId, String inputCode) {
        if (captchaId == null || captchaId.isBlank() || inputCode == null || inputCode.isBlank()) {
            return false;
        }
        String key = CAPTCHA_PREFIX + captchaId;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            return false;
        }
        redisTemplate.delete(key);
        return stored.equalsIgnoreCase(inputCode.trim());
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CHAR_COUNT);
        for (int i = 0; i < CHAR_COUNT; i++) {
            sb.append(CHAR_POOL.charAt(RANDOM.nextInt(CHAR_POOL.length())));
        }
        return sb.toString();
    }

    private BufferedImage renderImage(String code) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

        // noise dots
        g.setColor(new Color(200, 200, 200));
        for (int i = 0; i < 60; i++) {
            int x = RANDOM.nextInt(IMAGE_WIDTH);
            int y = RANDOM.nextInt(IMAGE_HEIGHT);
            g.fillOval(x, y, 2, 2);
        }

        // interference lines
        for (int i = 0; i < 3; i++) {
            g.setColor(randomColor(80, 160));
            int x1 = RANDOM.nextInt(IMAGE_WIDTH / 3);
            int y1 = RANDOM.nextInt(IMAGE_HEIGHT);
            int x2 = IMAGE_WIDTH - RANDOM.nextInt(IMAGE_WIDTH / 3);
            int y2 = RANDOM.nextInt(IMAGE_HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // characters
        int charWidth = IMAGE_WIDTH / (CHAR_COUNT + 1);
        Font[] fonts = {
                new Font("Arial", Font.BOLD, 28),
                new Font("Arial", Font.ITALIC, 26),
        };

        for (int i = 0; i < code.length(); i++) {
            g.setFont(fonts[RANDOM.nextInt(fonts.length)]);
            g.setColor(randomColor(20, 120));

            double rotate = (RANDOM.nextDouble() - 0.5) * 0.4;
            int x = charWidth * (i + 1) + RANDOM.nextInt(8) - 4;
            int y = IMAGE_HEIGHT / 2 + RANDOM.nextInt(10) - 5;

            g.rotate(rotate, x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.rotate(-rotate, x, y);
        }

        g.dispose();
        return image;
    }

    private String encodeToBase64(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode captcha image", e);
        }
    }

    private Color randomColor(int min, int max) {
        int r = min + RANDOM.nextInt(max - min);
        int g = min + RANDOM.nextInt(max - min);
        int b = min + RANDOM.nextInt(max - min);
        return new Color(r, g, b);
    }

    public record CaptchaResult(String captchaId, String captchaImage) {
    }
}
