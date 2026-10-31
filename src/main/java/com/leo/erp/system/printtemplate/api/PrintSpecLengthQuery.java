package com.leo.erp.system.printtemplate.api;

/**
 * 打印规格拼接只读端口：按定尺主数据的「打印规格后缀」拼出展示规格（如 12米 → {@code *12}）。
 *
 * <p>后缀来源是 {@code md_length_premium.spec_suffix}，运行期可变；本端口只暴露纯粹的
 * "spec + length → 展示规格"换算，不泄漏 {@code LengthSpecCatalog} 的读取细节。</p>
 */
public interface PrintSpecLengthQuery {

    /** 拼接规格：spec 为空时返回空串；定尺无后缀或已含该后缀时不重复追加。 */
    String apply(String spec, String length);
}
