package com.leo.erp.common.export;

import java.time.Instant;

/**
 * 导出产物（export artifact）：一次导出请求同步生成的文件表示。
 *
 * <p>生命周期是「有界缓存」而不是持久资源：创建后可在 TTL 内被 {@code GET /{id}} 与
 * {@code GET /{id}/content} 回读，过期或进程重启后回读返回 404。这是导出文件的天然语义——
 * 它是一次性下载物，不是需要长期保存的业务实体；长期保存请走附件资源。</p>
 *
 * <p>{@code parentType}/{@code parentId} 表达「产物从属于哪个业务资源」，
 * 例如销售订单套打导出记录 {@code parentType=sales-order}、{@code parentId=订单 id}，
 * 内容端点据此校验路径中的父资源与产物是否匹配。</p>
 *
 * @param id          雪花 id（对外 JSON 由全局 Jackson 配置序列化为十进制字符串）
 * @param kind        产物种类，见 {@link ExportArtifactKinds}
 * @param parentType  父资源类型，无父资源时为 {@code null}
 * @param parentId    父资源雪花 id，无父资源时为 {@code null}
 * @param ownerUserId 创建者用户 id，用于回读时的归属校验；系统任务可为 {@code null}
 * @param fileName    下载文件名（Content-Disposition）
 * @param contentType MIME 类型
 * @param content     文件字节
 * @param createdAt   创建时间
 * @param expiresAt   过期时间
 */
public record ExportArtifact(
        long id,
        String kind,
        String parentType,
        Long parentId,
        Long ownerUserId,
        String fileName,
        String contentType,
        byte[] content,
        Instant createdAt,
        Instant expiresAt
) {

    /** 字节数；远小于 2^53-1，对外按 JSON number 输出。 */
    public long size() {
        return content == null ? 0L : content.length;
    }
}
