package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;

/**
 * 商品行写结果: 新行内容 + 单据最新版本(服务端权威)。
 * <p>调用方必须以 {@link #version()} 回填本地版本, 不得自行 +1 猜测。</p>
 */
public record QuoteSheetItemWrite(QuoteSheetResponse.ItemResponse item, Long version) {
}
