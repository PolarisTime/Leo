package com.leo.erp.market.mysteel;

import java.util.Map;

/**
 * Mysteel 价格解密(逆向自 mysteel_encrypt.js, 与既有 Python 脚本口径一致):
 * <ul>
 *   <li>密钥 k 属性为 Base64 的 AES-128 密钥, 每篇文章随机;</li>
 *   <li>密文 m 属性为 AES-128-CBC(固定 IV)加密的 10 对字符映射表, 明文形如 "3-V,6-L,...";</li>
 *   <li>加密采用自定义 \t 填充(非 PKCS), 解密后需剥离尾部 \t;</li>
 *   <li>价格字段密文 = 原文反转后逐字符替换(数字-&gt;大写字母), 解密即先反转再替换再反转。</li>
 * </ul>
 */
public final class MysteelPriceDecryptor {

    static final byte[] FIXED_IV = "abcdefghijklmnop".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private static final char PAD_CHAR = '\t';
    private static final String CIPHER_TRANSFORMATION = "AES/CBC/NoPadding";

    private final Map<String, String> letterToDigit;

    private MysteelPriceDecryptor(Map<String, String> letterToDigit) {
        this.letterToDigit = letterToDigit;
    }

    public static MysteelPriceDecryptor create(String aesKeyBase64, String encryptedMapping) {
        return new MysteelPriceDecryptor(resolveMapping(aesKeyBase64, encryptedMapping));
    }

    /** 解密单个价格/涨跌: 反转 + 逐字符替换 + 反转。 */
    public String decryptValue(String encrypted) {
        String reversed = new StringBuilder(encrypted).reverse().toString();
        StringBuilder mapped = new StringBuilder(reversed.length());
        for (int i = 0; i < reversed.length(); i++) {
            String ch = reversed.substring(i, i + 1);
            mapped.append(letterToDigit.getOrDefault(ch, ch));
        }
        return mapped.reverse().toString();
    }

    private static Map<String, String> resolveMapping(String aesKeyBase64, String encryptedMapping) {
        String plain = decryptAesCbc(aesKeyBase64, encryptedMapping);
        Map<String, String> mapping = new java.util.LinkedHashMap<>();
        for (String pair : plain.split(",")) {
            String[] parts = pair.split("-");
            if (parts.length != 2 || parts[0].length() != 1 || parts[1].length() != 1) {
                throw new IllegalStateException("映射表格式异常: " + pair);
            }
            mapping.put(parts[1], parts[0]);
        }
        if (mapping.isEmpty()) {
            throw new IllegalStateException("映射表为空");
        }
        return mapping;
    }

    private static String decryptAesCbc(String aesKeyBase64, String encryptedBase64) {
        try {
            javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(
                    java.util.Base64.getDecoder().decode(aesKeyBase64), "AES");
            javax.crypto.spec.IvParameterSpec iv = new javax.crypto.spec.IvParameterSpec(FIXED_IV);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, iv);
            byte[] decrypted = cipher.doFinal(java.util.Base64.getDecoder().decode(encryptedBase64));
            String plain = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
            int endIndex = plain.length();
            while (endIndex > 0 && plain.charAt(endIndex - 1) == PAD_CHAR) {
                endIndex--;
            }
            return plain.substring(0, endIndex);
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("行情价格解密失败", ex);
        }
    }
}
