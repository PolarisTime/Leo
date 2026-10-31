package com.leo.erp.system.securitykey.api;

import java.util.List;

/**
 * 运行时密钥读取端口，供其它模块（JWT、附件加密、启动自检）消费。
 *
 * <p>其它模块不得直接依赖 {@code system.securitykey.service.SecurityKeyService} 的内部实现：
 * 轮换、缓存与指纹计算属于该模块自身职责，跨模块只需要"当前可用的密钥材料"。
 * ArchUnit 跨模块 service 规则会拦住其它写法。</p>
 */
public interface SecurityKeyQuery {

    /** 当前用于签发 JWT 的主密钥。 */
    ResolvedSecretMaterial getActiveJwtMaterial();

    /** 当前用于数据加密（附件内容等）的主密钥。 */
    ResolvedSecretMaterial getActiveDataMaterial();

    /** JWT 校验需要的密钥集合（当前主密钥 + 仍在宽限期内的历史密钥），按版本从新到旧。 */
    List<ResolvedSecretMaterial> getJwtVerificationMaterials();
}
