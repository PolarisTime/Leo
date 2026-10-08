package com.leo.erp.market;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

/**
 * 该日无行情数据：站点正常响应，但当天没有发布行情（休市、节假日），或报价页为空表。
 *
 * <p>与普通取数失败的差别是**语义**：这不是错误，而是「这天本来就没有数据」。补数需要据此把它记为
 * <b>跳过</b>而不是失败，否则节假日会污染失败清单、让人误以为系统有问题；对外仍然是
 * {@link BusinessException}（业务处理失败 4220），HTTP 契约保持不变。</p>
 */
public class QuoteNotPublishedException extends BusinessException {

    public QuoteNotPublishedException(String message) {
        super(ErrorCode.BUSINESS_ERROR, message);
    }
}
