package com.leo.erp.market.mysteel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mysteel 行情抓取与商品匹配配置。
 *
 * <p>映射类配置统一使用列表 + ASCII 键(from/to 等)承载, 因为 Spring 属性名不允许中文键;
 * 业务层仍通过 Map 视图访问。</p>
 */
@Validated
@ConfigurationProperties(prefix = "leo.market.steel-quote")
public class MysteelProperties {

    private boolean enabled = true;
    private String market = "杭州";
    private String listUrl = "https://jiancai.mysteel.com/market/pa228a15472aa0aaaaa1.html";
    private int requestTimeoutMs = 20000;
    private long rateLimitMillis = 1000;
    private Sync sync = new Sync();
    private Match match = new Match();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public String getListUrl() {
        return listUrl;
    }

    public void setListUrl(String listUrl) {
        this.listUrl = listUrl;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public long getRateLimitMillis() {
        return rateLimitMillis;
    }

    public void setRateLimitMillis(long rateLimitMillis) {
        this.rateLimitMillis = rateLimitMillis;
    }

    public Sync getSync() {
        return sync;
    }

    public void setSync(Sync sync) {
        this.sync = sync;
    }

    public Match getMatch() {
        return match;
    }

    public void setMatch(Match match) {
        this.match = match;
    }

    public static class Sync {
        private boolean enabled = true;
        private String cron = "0 35 10,13,16 * * *";
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

    public static class Match {
        /** 忽略的材质(不参与匹配, 直接返回无网价)。 */
        private List<String> ignoredMaterials = List.of("HRB500E");
        /** 商品品牌 -> Mysteel 钢厂别名映射。 */
        private List<AliasEntry> brandAliases = defaultBrandAliases();
        /** 商品类别 -> Mysteel 品名映射。 */
        private List<AliasEntry> breedMappings = defaultBreedMappings();
        /** 商品长度 -> 每吨加价(元), 如 12米 +30。 */
        private List<LengthPremium> lengthPremiums = defaultLengthPremiums();

        public List<String> getIgnoredMaterials() {
            return ignoredMaterials;
        }

        public void setIgnoredMaterials(List<String> ignoredMaterials) {
            this.ignoredMaterials = ignoredMaterials;
        }

        public List<AliasEntry> getBrandAliases() {
            return brandAliases;
        }

        public void setBrandAliases(List<AliasEntry> brandAliases) {
            this.brandAliases = brandAliases;
        }

        public List<AliasEntry> getBreedMappings() {
            return breedMappings;
        }

        public void setBreedMappings(List<AliasEntry> breedMappings) {
            this.breedMappings = breedMappings;
        }

        public List<LengthPremium> getLengthPremiums() {
            return lengthPremiums;
        }

        public void setLengthPremiums(List<LengthPremium> lengthPremiums) {
            this.lengthPremiums = lengthPremiums;
        }

        /** 品牌别名 Map 视图。 */
        public Map<String, String> getBrandAlias() {
            return toMap(brandAliases);
        }

        /** 类别映射 Map 视图。 */
        public Map<String, String> getBreedMap() {
            return toMap(breedMappings);
        }

        /** 长度加价 Map 视图。 */
        public Map<String, Integer> getLengthPremium() {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (LengthPremium item : lengthPremiums) {
                if (item.getLength() != null) {
                    result.put(item.getLength(), item.getPremium());
                }
            }
            return result;
        }

        private static Map<String, String> toMap(List<AliasEntry> entries) {
            Map<String, String> result = new LinkedHashMap<>();
            for (AliasEntry entry : entries) {
                if (entry.getFrom() != null && entry.getTo() != null) {
                    result.put(entry.getFrom(), entry.getTo());
                }
            }
            return result;
        }

        private static List<AliasEntry> defaultBrandAliases() {
            List<AliasEntry> entries = new ArrayList<>();
            entries.add(new AliasEntry("万泰", "浙江万泰"));
            entries.add(new AliasEntry("中新", "中新钢铁"));
            entries.add(new AliasEntry("中杭", "今胜中杭"));
            entries.add(new AliasEntry("圆钢", "今胜中杭"));
            entries.add(new AliasEntry("新梅鹿", "隆鑫/新梅鹿"));
            entries.add(new AliasEntry("芜湖富鑫", "安徽富鑫"));
            entries.add(new AliasEntry("铜陵富鑫", "安徽富鑫"));
            return entries;
        }

        private static List<AliasEntry> defaultBreedMappings() {
            List<AliasEntry> entries = new ArrayList<>();
            entries.add(new AliasEntry("直条", "螺纹钢"));
            entries.add(new AliasEntry("盘螺", "盘螺"));
            entries.add(new AliasEntry("圆钢", "圆钢"));
            return entries;
        }

        private static List<LengthPremium> defaultLengthPremiums() {
            List<LengthPremium> entries = new ArrayList<>();
            entries.add(new LengthPremium("12米", 30));
            return entries;
        }
    }

    /** 映射项: from -> to。 */
    public static class AliasEntry {
        private String from;
        private String to;

        public AliasEntry() {
        }

        public AliasEntry(String from, String to) {
            this.from = from;
            this.to = to;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }
    }

    /** 长度加价项。 */
    public static class LengthPremium {
        private String length;
        private Integer premium;

        public LengthPremium() {
        }

        public LengthPremium(String length, Integer premium) {
            this.length = length;
            this.premium = premium;
        }

        public String getLength() {
            return length;
        }

        public void setLength(String length) {
            this.length = length;
        }

        public Integer getPremium() {
            return premium;
        }

        public void setPremium(Integer premium) {
            this.premium = premium;
        }
    }
}
