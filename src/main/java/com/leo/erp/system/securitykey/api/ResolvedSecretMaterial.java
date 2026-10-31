package com.leo.erp.system.securitykey.api;

import java.time.LocalDateTime;

/**
 * 运行时密钥材料快照（JWT 主密钥 / 数据加密主密钥）。
 *
 * <p>属于 {@code system.securitykey} 模块对外暴露的只读契约：调用方（JWT 签发/校验、附件内容加密、
 * 启动自检）只持有快照，不接触密钥仓储实体与轮换逻辑；密钥的读取、优先级与指纹计算仍由
 * {@code system.securitykey} 内部实现。</p>
 *
 * @param source      密钥来源（{@code CONFIG_FILE} / {@code DATABASE}）
 * @param version     密钥版本号；配置来源为 {@code null}
 * @param secretValue 密钥明文（仅在进程内使用，禁止写日志或返回响应体）
 * @param activatedAt 启用时间；配置来源为 {@code null}
 * @param retiredAt   退役时间；未退役为 {@code null}
 * @param fingerprint 密钥指纹（用于日志/自检对比，不可逆）
 */
public record ResolvedSecretMaterial(
        String source,
        Integer version,
        String secretValue,
        LocalDateTime activatedAt,
        LocalDateTime retiredAt,
        String fingerprint
) {
}
