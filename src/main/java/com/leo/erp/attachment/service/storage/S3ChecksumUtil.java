package com.leo.erp.attachment.service.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

@Component
public class S3ChecksumUtil {

    private static final byte[] EMPTY_BYTES = new byte[0];

    public String hexSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public String hexSha256(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    public String hexSha256(String content) {
        return hexSha256(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String emptyBodyHash() {
        return hexSha256(EMPTY_BYTES);
    }

    public String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte item : data) {
            builder.append(String.format(Locale.ROOT, "%02x", item));
        }
        return builder.toString();
    }
}
