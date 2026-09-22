package com.leo.erp.market.steelx;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 西本新干线(steelx2.com)行情抓取配置。
 *
 * <p>西本按地区(城市)报价: 宿主名为 {@code {slug}.steelx2.com}, 路径统一,
 * 故以"城市中文名 -> 子域名 slug"的映射承载; 仅支持配置中列出的城市。</p>
 */
@ConfigurationProperties(prefix = "leo.market.steelx-quote")
public class SteelxProperties {

    private boolean enabled = true;
    /** 数据源标识。 */
    private String source = "STEELX";
    /** 建材报价路径模板。 */
    private String quotationPath = "/city/Quotation/quotation/1/index.html";
    private int requestTimeoutMs = 20000;
    /** 通过该 SSH 主机远程 curl 取数; 为空则直连。 */
    private String fetchSshHost = "";
    private long rateLimitMillis = 1000;
    /** 城市与子域名映射(列表承载, Spring 属性不支持中文键)。 */
    private List<Region> regions = defaultRegions();
    private Sync sync = new Sync();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getQuotationPath() {
        return quotationPath;
    }

    public void setQuotationPath(String quotationPath) {
        this.quotationPath = quotationPath;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public String getFetchSshHost() {
        return fetchSshHost;
    }

    public void setFetchSshHost(String fetchSshHost) {
        this.fetchSshHost = fetchSshHost;
    }

    public long getRateLimitMillis() {
        return rateLimitMillis;
    }

    public void setRateLimitMillis(long rateLimitMillis) {
        this.rateLimitMillis = rateLimitMillis;
    }

    public List<Region> getRegions() {
        return regions;
    }

    public void setRegions(List<Region> regions) {
        this.regions = regions;
    }

    /** 城市中文名 -> 子域名 Map 视图。 */
    public Map<String, String> regionMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Region region : regions) {
            if (region.getName() != null && region.getSlug() != null) {
                map.put(region.getName(), region.getSlug());
            }
        }
        return map;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    /** 子域名 slug -> 城市中文名(反向视图)。 */
    public Map<String, String> regionBySlug() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : regionMap().entrySet()) {
            result.put(entry.getValue(), entry.getKey());
        }
        return result;
    }

    /** 该地区是否受支持。 */
    public boolean supportsRegion(String region) {
        return region != null && regionMap().containsKey(region.trim());
    }

    /** 拼接指定地区的报价页 URL。 */
    public String quotationUrl(String region) {
        String slug = regionMap().get(region == null ? null : region.trim());
        if (slug == null) {
            throw new IllegalArgumentException("不支持的地区: " + region);
        }
        return "https://" + slug + ".steelx2.com" + quotationPath;
    }

    public List<String> supportedRegions() {
        return List.copyOf(regionMap().keySet());
    }

    private static List<Region> defaultRegions() {
        List<Region> list = new ArrayList<>();
        list.add(new Region("杭州", "hangzhou"));
        list.add(new Region("上海", "shanghai"));
        list.add(new Region("宁波", "ningbo"));
        list.add(new Region("嘉兴", "jiaxing"));
        list.add(new Region("绍兴", "shaoxing"));
        return list;
    }

    /** 地区映射项: 城市中文名 -> 子域名。 */
    public static class Region {
        private String name;
        private String slug;

        public Region() {
        }

        public Region(String name, String slug) {
            this.name = name;
            this.slug = slug;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }
    }

    /** 定时抓取配置。 */
    public static class Sync {
        private boolean enabled = true;
        /** 西本每日仅一个价(上午发布), 默认 10:20 抓取。 */
        private String cron = "0 20 10 * * *";
        private String zone = "Asia/Shanghai";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }
}
