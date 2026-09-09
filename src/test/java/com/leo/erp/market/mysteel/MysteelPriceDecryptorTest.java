package com.leo.erp.market.mysteel;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysteelPriceDecryptorTest {

    private static final byte[] IV = "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8);

    private static final Map<String, String> DIGIT_TO_LETTER = new HashMap<>();

    static {
        String digits = "0123456789";
        String letters = "ABCDEFGHIJ";
        for (int i = 0; i < digits.length(); i++) {
            DIGIT_TO_LETTER.put(digits.substring(i, i + 1), letters.substring(i, i + 1));
        }
    }

    @Test
    void decryptValue_reversesAndSubstitutes() {
        MysteelPriceDecryptor decryptor = MysteelPriceDecryptor.create(
                Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)),
                encryptedMappingBase64());
        // 3260 -> 反转 0623 -> 替换 AGCD -> 反转 DCGA
        assertThat(decryptor.decryptValue("DCGA")).isEqualTo("3260");
        // 0 -> 反转 0 -> A
        assertThat(decryptor.decryptValue("A")).isEqualTo("0");
        // 非映射字符保持原样且参与反转(涨跌为 "-" 时)
        assertThat(decryptor.decryptValue("DA-A")).isEqualTo("30-0");
    }

    @Test
    void decryptValue_roundTripsArbitraryPrice() {
        MysteelPriceDecryptor decryptor = MysteelPriceDecryptor.create(
                Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8)),
                encryptedMappingBase64());
        for (String price : new String[] {"3430", "3260", "3510", "9999", "100"}) {
            assertThat(decryptor.decryptValue(encryptValue(price))).isEqualTo(price);
        }
    }

    @Test
    void create_rejectsMalformedMapping() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> MysteelPriceDecryptor.create(key, encryptedBase64("bad-mapping", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("映射表");
    }

    @Test
    void create_rejectsEmptyMapping() {
        String key = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> MysteelPriceDecryptor.create(key, encryptedBase64("", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("映射表");
    }

    /** 按加密口径构造价格密文: 反转 -> 数字替换为字母 -> 反转。 */
    private String encryptValue(String plain) {
        String reversed = new StringBuilder(plain).reverse().toString();
        StringBuilder mapped = new StringBuilder(reversed.length());
        for (int i = 0; i < reversed.length(); i++) {
            String ch = reversed.substring(i, i + 1);
            mapped.append(DIGIT_TO_LETTER.getOrDefault(ch, ch));
        }
        return mapped.reverse().toString();
    }

    private String encryptedMappingBase64() {
        StringBuilder mapping = new StringBuilder();
        for (Map.Entry<String, String> entry : DIGIT_TO_LETTER.entrySet()) {
            if (mapping.length() > 0) {
                mapping.append(',');
            }
            // 明文-密文对: 数字-字母
            mapping.append(entry.getKey()).append('-').append(entry.getValue());
        }
        return encryptedBase64(mapping.toString(), true);
    }

    /** AES-128-CBC + 自定义 \t 填充(与抓取站口径一致, 非 PKCS)。 */
    private String encryptedBase64(String plain, boolean pad) {
        try {
            byte[] data = pad ? padWithTab(plain) : plain.getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec("0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(IV));
            return Base64.getEncoder().encodeToString(cipher.doFinal(data));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] padWithTab(String plain) {
        int blockSize = 16;
        int paddedLength = ((plain.length() + blockSize - 1) / blockSize) * blockSize;
        if (paddedLength == 0) {
            paddedLength = blockSize;
        }
        byte[] result = new byte[paddedLength];
        byte[] plainBytes = plain.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(plainBytes, 0, result, 0, plainBytes.length);
        for (int i = plainBytes.length; i < paddedLength; i++) {
            result[i] = '\t';
        }
        return result;
    }
}
