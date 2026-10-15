package com.leo.erp.master.api;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 值映射维度白名单。
 *
 * <p>取值与数据库 CHECK 约束 {@code ck_value_alias_dimension} 保持一致；新增维度必须同时
 * 修改约束（新 Flyway 脚本）与这里，否则写入会在数据库层被拒。</p>
 *
 * <p>本枚举是跨模块契约的一部分（行情匹配按维度取归一映射），因此与 {@link ValueAliasQuery}
 * 一起放在 {@code master.api}：放在 {@code master.valuealias.service} 会被 Spring Modulith
 * 判为「market 依赖 master 未暴露类型」。枚举值本身不随模块内部结构变化。</p>
 */
public enum ValueAliasDimension {

    /** 商品类别，如「直条」与「螺纹钢」是同一类别的两种写法。 */
    CATEGORY("类别"),

    /** 材质，如 HRB400E 与 HRB400 的写法差异。 */
    MATERIAL("材质"),

    /** 定尺，如 9米 / 12米 的多种写法。 */
    LENGTH("定尺"),

    /** 品牌，如「万泰」与「浙江万泰」。 */
    BRAND("品牌");

    private final String label;

    ValueAliasDimension(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 宽松解析：忽略大小写与首尾空白；无法识别时返回空。 */
    public static Optional<ValueAliasDimension> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(dimension -> dimension.name().equals(normalized))
                .findFirst();
    }

    /**
     * 严格解析：无法识别时抛 422 语义错误。
     *
     * <p>用 {@link ErrorCode#BUSINESS_ERROR} 而非 {@code VALIDATION_ERROR}，因为维度非法属于
     * 语义错误（请求格式本身是合法的）。</p>
     */
    public static ValueAliasDimension require(String raw) {
        return parse(raw).orElseThrow(() -> new BusinessException(
                ErrorCode.BUSINESS_ERROR, "维度必须是 CATEGORY / MATERIAL / LENGTH / BRAND 之一"));
    }
}
