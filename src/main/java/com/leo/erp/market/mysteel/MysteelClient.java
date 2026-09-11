package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mysteel 行情来源: 列表页查找文章、下载文章。
 * 传输由 {@link MysteelFetcher} 负责; 本类负责缓存与页面解析, 缓存用于同一轮批量补数复用。
 */
@Component
public class MysteelClient {

    private static final int MIN_VALID_HTML_LENGTH = 10000;
    private static final long LIST_CACHE_MILLIS = 60_000L;
    private static final Pattern ARTICLE_LINK = Pattern.compile(
            "<a href=\"(https://jiancai\\.mysteel\\.com/m/[^\"]+)\"[^>]*title=\"[^\\\"]*");

    private final MysteelProperties properties;
    private final MysteelFetcher fetcher;

    private volatile String cachedListHtml;
    private volatile long cachedListAt;

    public MysteelClient(MysteelProperties properties, MysteelFetcher fetcher) {
        this.properties = properties;
        this.fetcher = fetcher;
    }

    /** 在列表页查找指定日期最新一篇杭州建筑钢材价格行情文章。 */
    public Optional<String> findLatestArticleUrl(LocalDate date) {
        return findArticleUrls(date).stream().findFirst();
    }

    /** 在列表页查找指定日期当天所有杭州建筑钢材价格行情文章(按列表顺序, 新→旧)。 */
    public List<String> findArticleUrls(LocalDate date) {
        String listHtml = listHtml();
        Pattern pattern = Pattern.compile(ARTICLE_LINK.pattern()
                + suffix(date));
        Set<String> urls = new LinkedHashSet<>();
        pattern.matcher(listHtml).results().forEach(result -> urls.add(result.group(1)));
        return new ArrayList<>(urls);
    }

    /** 下载行情文章 HTML。 */
    public String fetchArticle(String articleUrl) {
        return fetcher.fetch(articleUrl, "行情文章");
    }

    private static String suffix(LocalDate date) {
        return date.getMonthValue() + "月" + date.getDayOfMonth()
                + "日[^\\\"]*杭州市场建筑钢材价格行情\"[^>]*>";
    }

    private String listHtml() {
        long now = System.currentTimeMillis();
        String html = cachedListHtml;
        if (html != null && now - cachedListAt < LIST_CACHE_MILLIS) {
            return html;
        }
        String fetched = fetcher.fetch(properties.getListUrl(), "行情列表页");
        if (isInvalidPage(fetched)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "列表页返回安全验证页(疑似触发IP风控), 请稍后再试");
        }
        cachedListHtml = fetched;
        cachedListAt = now;
        return fetched;
    }

    private boolean isInvalidPage(String html) {
        return fetcher.looksLikeRiskControl(html) || html.length() < MIN_VALID_HTML_LENGTH;
    }
}
