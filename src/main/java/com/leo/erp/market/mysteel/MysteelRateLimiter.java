package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 请求节流: 相邻 Mysteel 请求间隔, 降低触发站点风控概率。
 */
@Component
public class MysteelRateLimiter {

    private final long intervalMillis;

    public MysteelRateLimiter(MysteelProperties properties) {
        this.intervalMillis = properties.getRateLimitMillis();
    }

    /** 获取一次请求许可(按配置间隔休眠)。 */
    public void acquire() {
        if (intervalMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "行情同步被中断");
        }
    }
}
