package com.leo.erp.attachment.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.json.NumericLongSerializer;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        String name,
        String fileName,
        String originalFileName,
        String contentType,
        /**
         * 字节数, 不是雪花 ID: 全局 Long 序列化为字符串的规则只用于 ID 精度保护,
         * 这里必须保持 JSON number, 否则前端按 number 声明的契约校验会整条失败。
         */
        @JsonSerialize(using = NumericLongSerializer.class) Long fileSize,
        String sourceType,
        String uploader,
        LocalDateTime uploadTime,
        Boolean previewSupported,
        String previewType,
        String previewUrl,
        String downloadUrl,
        String storageType,
        String storageLabel
) {
    public AttachmentResponse(
            Long id,
            String name,
            String fileName,
            String originalFileName,
            String contentType,
            Long fileSize,
            String sourceType,
            String uploader,
            LocalDateTime uploadTime,
            Boolean previewSupported,
            String previewType,
            String previewUrl,
            String downloadUrl
    ) {
        this(id, name, fileName, originalFileName, contentType, fileSize, sourceType, uploader, uploadTime,
                previewSupported, previewType, previewUrl, downloadUrl, null, null);
    }
}
