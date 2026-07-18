package com.leo.erp.common.config;

import com.leo.erp.common.web.PageQuerySettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "leo.pagination")
public class PageQueryProperties implements PageQuerySettings {

    @Min(1)
    @Max(200)
    private int defaultListPageSize = 30;

    @Override
    public int getDefaultListPageSize() {
        return defaultListPageSize;
    }

    public void setDefaultListPageSize(int defaultListPageSize) {
        this.defaultListPageSize = defaultListPageSize;
    }
}
