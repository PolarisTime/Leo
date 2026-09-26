package com.leo.erp.attachment.support;

/**
 * 附件存储后端类型标识与其存储路径前缀(单一来源)。
 *
 * <p>类型值即 {@code leo.attachment.storage.type} 的配置取值, 亦是各 {@code AttachmentStorage#type()}
 * 的返回值与存储路径(如 {@code local:key} / {@code s3:bucket/key})的前缀。</p>
 */
public final class AttachmentStorageTypes {

    private AttachmentStorageTypes() {
    }

    /** 本地文件系统。 */
    public static final String LOCAL = "local";
    /** S3 兼容对象存储。 */
    public static final String S3 = "s3";

    /** 本地存储路径前缀。 */
    public static final String LOCAL_PREFIX = LOCAL + ":";
    /** S3 存储路径前缀。 */
    public static final String S3_PREFIX = S3 + ":";
}
