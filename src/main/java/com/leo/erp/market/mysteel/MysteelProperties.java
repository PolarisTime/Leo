package com.leo.erp.market.mysteel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mysteel 行情抓取与商品匹配配置。
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
        /** 商品品牌 -> Mysteel 钢厂别名映射, 未列出 = 同名直接匹配。 */
        private Map<String, String> brandAlias = defaultBrandAlias();
        /** 商品类别 -> Mysteel 品名映射, 未列出 = 同名直接匹配。 */
        private Map<String, String> breedMap = defaultBreedMap();
        /** 商品长度 -> 每吨加价(元), 如 12米 +30。 */
        private Map<String, Integer> lengthPremium = defaultLengthPremium();

        public List<String> getIgnoredMaterials() {
            return ignoredMaterials;
        }

        public void setIgnoredMaterials(List<String> ignoredMaterials) {
            this.ignoredMaterials = ignoredMaterials;
        }

        public Map<String, String> getBrandAlias() {
            return brandAlias;
        }

        public void setBrandAlias(Map<String, String> brandAlias) {
            this.brandAlias = brandAlias;
        }

        public Map<String, String> getBreedMap() {
            return breedMap;
        }

        public void setBreedMap(Map<String, String> breedMap) {
            this.breedMap = breedMap;
        }

        public Map<String, Integer> getLengthPremium() {
            return lengthPremium;
        }

        public void setLengthPremium(Map<String, Integer> lengthPremium) {
            this.lengthPremium = lengthPremium;
        }

        private static Map<String, String> defaultBrandAlias() {
            Map<String, String> alias = new LinkedHashMap<>();
            alias.put("万泰", "浙江万泰");
            alias.put("中新", "中新钢铁");
            alias.put("中杭", "今胜中杭");
            alias.put("圆钢", "今胜中杭");
            alias.put("新梅鹿", "隆鑫/新梅鹿");
            alias.put("芜湖富鑫", "安徽富鑫");
            alias.put("铜陵富鑫", "安徽富鑫");
            return alias;
        }

        private static Map<String, String> defaultBreedMap() {
            Map<String, String> breed = new LinkedHashMap<>();
            breed.put("直条", "螺纹钢");
            breed.put("盘螺", "盘螺");
            breed.put("圆钢", "圆钢");
            return breed;
        }

        private static Map<String, Integer> defaultLengthPremium() {
            Map<String, Integer> premium = new LinkedHashMap<>();
            premium.put("12米", 30);
            return premium;
        }
    }
}
