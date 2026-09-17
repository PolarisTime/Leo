package com.leo.erp.market.quotation.web;

/**
 * 报价单写接口的资源版本前置条件解析。
 *
 * <p>实现已抽取到 {@link com.leo.erp.common.web.ResourceVersionPrecondition} 供多个模块复用;
 * 本类仅保留原有包路径与常量作为兼容门面。</p>
 */
public final class ResourceVersionPrecondition {

    /** 资源版本前置条件的规范请求/响应头名。 */
    public static final String HEADER = com.leo.erp.common.web.ResourceVersionPrecondition.HEADER;

    /** 兼容别名(非标准弱验证器用法)。 */
    public static final String LEGACY_HEADER =
            com.leo.erp.common.web.ResourceVersionPrecondition.LEGACY_HEADER;

    private ResourceVersionPrecondition() {
    }

    public static Long parse(String resourceVersion, String ifMatch, boolean required) {
        return com.leo.erp.common.web.ResourceVersionPrecondition.parse(resourceVersion, ifMatch, required);
    }
}
