package com.leo.erp.attachment.support;

/**
 * 附件加密与完整性校验相关算法/尺寸常量(单一来源)。
 */
public final class AttachmentCryptoConstants {

    private AttachmentCryptoConstants() {
    }

    /** 附件内容加密算法。 */
    public static final String CONTENT_CIPHER = "AES/GCM/NoPadding";
    public static final String KEY_ALGORITHM = "AES";
    /** 完整性/主密钥派生摘要算法。 */
    public static final String DIGEST_ALGORITHM = "SHA-256";
    /** 直传凭证签名算法。 */
    public static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 附件加密主密钥派生前缀(与主密钥材料拼接后摘要)。 */
    public static final String KEY_DERIVATION_PREFIX = "attachment-content:";

    /** 流式读写缓冲大小。 */
    public static final int STREAM_BUFFER_SIZE = 8192;
}
