package com.leo.erp.common.support;

import java.util.List;

/**
 * 取价地区目录端口。
 *
 * <p>运行时配置需要把可选取价地区下发给前端, 但地区清单由行情模块的抓取配置持有;
 * 若系统模块直接读取行情模块的配置类, 会形成
 * {@code market -> purchase -> system -> market} 的模块环。这里按既有
 * {@link MaterialCatalog}/{@link WarehouseCatalog} 的做法, 由 {@code common} 定义窄接口、
 * 行情模块提供实现, 依赖方向保持不变。</p>
 */
public interface QuoteRegionCatalog {

    /** 受支持的取价地区(城市中文名), 顺序与配置保持一致; 未配置时由实现给出内置默认。 */
    List<String> listSupportedQuoteRegions();
}
