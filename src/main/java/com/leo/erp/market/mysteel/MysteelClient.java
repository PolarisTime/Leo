package com.leo.erp.market.mysteel;

import com.leo.erp.common.retry.TransientCallException;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Mysteel 行情来源: 列表页查找文章、下载文章。
 * 传输由 {@link MysteelFetcher} 负责; 本类负责缓存与页面解析, 缓存用于同一轮批量补数复用。
 *
 * <p><b>为什么必须按日期查询而不是读列表页第 1 页:</b>列表页每页固定 50 篇，
 * 第 1 页只覆盖最近约 17 个交易日；更早的日期在第 1 页上根本不存在，补数会直接报「未找到行情文章」。
 * 站点支持 {@code ?startTime=yyyy-MM-dd&endTime=yyyy-MM-dd} 过滤（实测可用），
 * 因此这里按目标日期查询，让任意历史日期都可定位，且无需翻页。</p>
 */
@Component
public class MysteelClient {

    /** 过滤后的有效页面长度下限：低于它说明拿到的不是正常列表页（被截断/异常页），值得重试。 */
    private static final int MIN_VALID_HTML_LENGTH = 10000;
    /** 同一日期的列表页短时缓存，供同一轮批量处理复用。 */
    private static final long LIST_CACHE_MILLIS = 60_000L;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Pattern ARTICLE_LINK = Pattern.compile(
            "<a href=\"(https://jiancai\\.mysteel\\.com/m/[^\"]+)\"[^>]*title=\"[^\\\"]*");

    private final MysteelProperties properties;
    private final MysteelFetcher fetcher;

    private volatile String cachedListHtml;
    private volatile LocalDate cachedListDate;
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
        String listHtml = listHtml(date);
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

    /** 按日期过滤的列表页地址; 站点对未编码的冒号等字符兼容, 这里仍做最小化编码。 */
    static String urlFor(String baseUrl, LocalDate date) {
        String day = URLEncoder.encode(date.format(DAY), StandardCharsets.UTF_8);
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + "startTime=" + day + "&endTime=" + day;
    }

    private String listHtml(LocalDate date) {
        long now = System.currentTimeMillis();
        String html = cachedListHtml;
        if (html != null && date.equals(cachedListDate) && now - cachedListAt < LIST_CACHE_MILLIS) {
            return html;
        }
        String fetched = fetcher.fetch(urlFor(properties.getListUrl(), date), "行情列表页",
                this::requireIntactPage);
        cachedListHtml = fetched;
        cachedListDate = date;
        cachedListAt = now;
        return fetched;
    }

    /**
     * 列表页内容校验：页面被截断时抛可重试的瞬时失败，在传输层同一个重试预算内重试。
     *
     * <p>注意「该日无行情文章」**不是**这里的失败：节假日页面结构完整、只是没有链接，
     * 由调用方按文章数为 0 判定并记为跳过。</p>
     */
    private void requireIntactPage(String html) {
        if (html.length() < MIN_VALID_HTML_LENGTH) {
            throw new TransientCallException("列表页内容不完整(长度 " + html.length() + "), 疑似被截断");
        }
    }
}
