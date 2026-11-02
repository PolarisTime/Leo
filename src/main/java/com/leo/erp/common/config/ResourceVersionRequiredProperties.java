package com.leo.erp.common.config;

/**
 * 写接口乐观并发的「是否强制资源版本前置条件」配置基类。
 *
 * <p>各单据模块的 {@code *Properties} 只差 {@code @ConfigurationProperties} 前缀与默认值，
 * 主体（一个布尔 + 读写方法）完全相同；此前 7 份逐字复制，口径调整要改 7 处。
 * 现在统一继承本类，子类只负责声明前缀，必要时用 {@code super(false)} 覆盖默认值。</p>
 *
 * <p>语义：开启时缺少 {@code X-Resource-Version} 返回 428；关闭时缺少版本仍允许更新，
 * 但**带了版本照样校验 412**。默认开启（{@code true}）。</p>
 */
public abstract class ResourceVersionRequiredProperties {

    private boolean requireResourceVersion;

    protected ResourceVersionRequiredProperties() {
        this(true);
    }

    /**
     * @param requireResourceVersion 缺少版本前置条件时是否拒绝（428）；
     *                               默认值由基类给出（{@code true}），个别模块可传 {@code false}
     */
    protected ResourceVersionRequiredProperties(boolean requireResourceVersion) {
        this.requireResourceVersion = requireResourceVersion;
    }

    public boolean isRequireResourceVersion() {
        return requireResourceVersion;
    }

    public void setRequireResourceVersion(boolean requireResourceVersion) {
        this.requireResourceVersion = requireResourceVersion;
    }
}
