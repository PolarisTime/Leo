package com.leo.erp.common.export;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

/**
 * 导出产物回读的公共校验：存在性（含过期）+ 归属。
 *
 * <p>统一在一处实现，避免每个 content/metadata 端点各写一遍而出现口径差异：
 * 不存在、已过期、归属不符一律返回 404（不泄露「这个 id 存在但属于别人」）。</p>
 */
public final class ExportArtifactAccess {

    private static final String NOT_FOUND_MESSAGE = "导出结果不存在或已过期，请重新创建导出请求";

    private ExportArtifactAccess() {
    }

    /**
     * 读取产物；不存在/过期/种类不符/归属不符时抛 404。
     *
     * @param store       产物存储
     * @param kind        期望的产物种类
     * @param id          产物雪花 id
     * @param requesterId 当前请求用户 id；为空（系统调用/未注入）时跳过归属校验
     */
    public static ExportArtifact require(ExportArtifactStore store,
                                         String kind,
                                         Long id,
                                         Long requesterId) {
        if (store == null || id == null || id <= 0L) {
            throw notFound();
        }
        ExportArtifact artifact = store.find(kind, id).orElseThrow(ExportArtifactAccess::notFound);
        if (artifact.ownerUserId() != null
                && requesterId != null
                && !artifact.ownerUserId().equals(requesterId)) {
            throw notFound();
        }
        return artifact;
    }

    /** 校验产物归属的父资源（如销售订单 id）；不匹配按不存在处理。 */
    public static ExportArtifact requireParent(ExportArtifact artifact, String parentType, Long parentId) {
        if (artifact.parentId() != null
                && (!parentType.equals(artifact.parentType()) || !artifact.parentId().equals(parentId))) {
            throw notFound();
        }
        return artifact;
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
    }
}
