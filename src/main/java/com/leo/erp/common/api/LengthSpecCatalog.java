package com.leo.erp.common.api;

import java.util.Map;
import java.util.Set;

/**
 * 定尺（商品长度）目录的跨模块只读端口。
 *
 * <p>定尺主数据落在 {@code md_length_premium}（定价域），但消费方分布在商品主数据（长度校验）、
 * 打印导出与销售单据（规格后缀）等模块。若消费方直接依赖定价域的服务，会在既有的
 * 「定价 → 商品主数据 → 打印域」依赖之上闭合出新的模块环，架构门禁
 * （{@code ModuleBoundaryVerifier} 的循环边规则）会硬阻断；因此契约放在平台层
 * {@code common.api}，由定价域实现、各消费方按接口注入。</p>
 *
 * <p>这里只声明读取口径，不复制任何业务规则：唯一实现仍是 {@code LengthPremiumService}。</p>
 */
public interface LengthSpecCatalog {

    /**
     * 正常状态的定尺名称（如 {@code 9米}、{@code 12米}），按维护顺序返回。
     *
     * <p>返回空集合表示定尺主数据尚未维护，调用方应保持改造前的行为而不是拦截全部数据。</p>
     */
    Set<String> activeLengthNames();

    /**
     * 正常状态且配置了打印后缀的定尺：定尺名称 → 规格后缀（如 {@code 12米 → *12}）。
     *
     * <p>未配置后缀、或后缀为空串的定尺不会出现在结果中，表示打印时不在规格后追加内容。</p>
     */
    Map<String, String> activeSpecSuffixMap();
}
