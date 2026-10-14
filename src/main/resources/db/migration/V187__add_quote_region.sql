-- 取价地区表(可维护数据): 西本新干线按地区(城市)报价, 宿主名为 {slug}.steelx2.com。
-- 说明:
--   1) 原先 69 条「城市中文名 -> 子域名 slug」只写在 application.yml 的 leo.market.steelx-quote.regions,
--      新增城市必须改配置并重启; 应用侧另有 5 条 Java 默认值(杭州/上海/宁波/嘉兴/绍兴)仅作配置缺失时兜底。
--   2) slug 与城市名并非总是拼音直拼: 无锡=jswuxi、福州=fjfuzhou、台州=zjtaizhou、哈尔滨=haerbin、
--      成都=chengdo(西本自身拼写)、莱芜=laifu, 请勿"纠正"为拼音, 否则抓取 404。本表原样固化现有取值。
--   3) 同一城市名与同一 slug 各自只允许一条未删除记录(部分唯一索引), 软删后可重建。
--   4) 应用侧保留原 yml 配置与 Java 默认值作为兜底, 本表为空时行为与改造前一致。
-- 幂等: 建表 IF NOT EXISTS, 种子 ON CONFLICT DO NOTHING, 权限登记 ON CONFLICT DO NOTHING。

CREATE TABLE IF NOT EXISTS public.md_quote_region (
    id bigint NOT NULL,
    region_name character varying(32) NOT NULL,
    slug character varying(64) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(16) DEFAULT '正常'::character varying NOT NULL,
    remark character varying(255),
    created_by bigint DEFAULT 0 NOT NULL,
    created_name character varying(64) DEFAULT 'system'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_name character varying(64),
    updated_at timestamp without time zone,
    deleted_flag boolean DEFAULT false NOT NULL,
    CONSTRAINT md_quote_region_pkey PRIMARY KEY (id),
    -- slug 会拼进主机名, 只允许小写字母/数字/连字符, 防止拼出非法 URL
    CONSTRAINT ck_quote_region_slug CHECK (slug::text ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_quote_region_status CHECK (status::text IN ('正常', '停用'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_quote_region_name
    ON public.md_quote_region USING btree (region_name) WHERE deleted_flag = false;

CREATE UNIQUE INDEX IF NOT EXISTS uk_quote_region_slug
    ON public.md_quote_region USING btree (slug) WHERE deleted_flag = false;

CREATE INDEX IF NOT EXISTS idx_quote_region_status
    ON public.md_quote_region USING btree (status, sort_order);

COMMENT ON TABLE public.md_quote_region IS
    '取价地区(城市)清单: 城市中文名 -> 西本子域名 slug; 支持停用而不删除, 停用后不再参与抓取与下拉';
COMMENT ON COLUMN public.md_quote_region.region_name IS '城市中文名(如 杭州), 未删除记录内唯一';
COMMENT ON COLUMN public.md_quote_region.slug IS '西本子域名前缀(如 hangzhou); 非拼音直拼, 见迁移头部说明';
COMMENT ON COLUMN public.md_quote_region.sort_order IS '展示与抓取顺序, 与原 yml 中的排列一致';
COMMENT ON COLUMN public.md_quote_region.status IS '状态: 正常 / 停用; 仅正常参与抓取与下拉';
COMMENT ON COLUMN public.md_quote_region.deleted_flag IS '软删除标记, true 表示已删除; 仅 false 的行参与唯一性约束';

INSERT INTO public.md_quote_region
    (id, region_name, slug, sort_order, status, remark, created_by, created_name)
VALUES
    (1, '上海', 'shanghai', 1, '正常', '西本城市站 shanghai.steelx2.com', 0, 'flyway'),
    (2, '杭州', 'hangzhou', 2, '正常', '西本城市站 hangzhou.steelx2.com', 0, 'flyway'),
    (3, '宁波', 'ningbo', 3, '正常', '西本城市站 ningbo.steelx2.com', 0, 'flyway'),
    (4, '嘉兴', 'jiaxing', 4, '正常', '西本城市站 jiaxing.steelx2.com', 0, 'flyway'),
    (5, '绍兴', 'shaoxing', 5, '正常', '西本城市站 shaoxing.steelx2.com', 0, 'flyway'),
    (6, '湖州', 'huzhou', 6, '正常', '西本城市站 huzhou.steelx2.com', 0, 'flyway'),
    (7, '金华', 'jinhua', 7, '正常', '西本城市站 jinhua.steelx2.com', 0, 'flyway'),
    (8, '丽水', 'lishui', 8, '正常', '西本城市站 lishui.steelx2.com', 0, 'flyway'),
    (9, '台州', 'zjtaizhou', 9, '正常', '西本城市站 zjtaizhou.steelx2.com', 0, 'flyway'),
    (10, '温州', 'wenzhou', 10, '正常', '西本城市站 wenzhou.steelx2.com', 0, 'flyway'),
    (11, '南京', 'nanjing', 11, '正常', '西本城市站 nanjing.steelx2.com', 0, 'flyway'),
    (12, '苏州', 'suzhou', 12, '正常', '西本城市站 suzhou.steelx2.com', 0, 'flyway'),
    (13, '无锡', 'jswuxi', 13, '正常', '西本城市站 jswuxi.steelx2.com', 0, 'flyway'),
    (14, '常州', 'changzhou', 14, '正常', '西本城市站 changzhou.steelx2.com', 0, 'flyway'),
    (15, '镇江', 'zhenjiang', 15, '正常', '西本城市站 zhenjiang.steelx2.com', 0, 'flyway'),
    (16, '扬州', 'yangzhou', 16, '正常', '西本城市站 yangzhou.steelx2.com', 0, 'flyway'),
    (17, '南通', 'nantong', 17, '正常', '西本城市站 nantong.steelx2.com', 0, 'flyway'),
    (18, '泰州', 'taizhou', 18, '正常', '西本城市站 taizhou.steelx2.com', 0, 'flyway'),
    (19, '盐城', 'yancheng', 19, '正常', '西本城市站 yancheng.steelx2.com', 0, 'flyway'),
    (20, '淮安', 'huaian', 20, '正常', '西本城市站 huaian.steelx2.com', 0, 'flyway'),
    (21, '宿迁', 'suqian', 21, '正常', '西本城市站 suqian.steelx2.com', 0, 'flyway'),
    (22, '徐州', 'xuzhou', 22, '正常', '西本城市站 xuzhou.steelx2.com', 0, 'flyway'),
    (23, '连云港', 'lianyungang', 23, '正常', '西本城市站 lianyungang.steelx2.com', 0, 'flyway'),
    (24, '合肥', 'hefei', 24, '正常', '西本城市站 hefei.steelx2.com', 0, 'flyway'),
    (25, '芜湖', 'wuhu', 25, '正常', '西本城市站 wuhu.steelx2.com', 0, 'flyway'),
    (26, '马鞍山', 'maanshan', 26, '正常', '西本城市站 maanshan.steelx2.com', 0, 'flyway'),
    (27, '蚌埠', 'bengbu', 27, '正常', '西本城市站 bengbu.steelx2.com', 0, 'flyway'),
    (28, '福州', 'fjfuzhou', 28, '正常', '西本城市站 fjfuzhou.steelx2.com', 0, 'flyway'),
    (29, '厦门', 'xiamen', 29, '正常', '西本城市站 xiamen.steelx2.com', 0, 'flyway'),
    (30, '泉州', 'quanzhou', 30, '正常', '西本城市站 quanzhou.steelx2.com', 0, 'flyway'),
    (31, '南昌', 'nanchang', 31, '正常', '西本城市站 nanchang.steelx2.com', 0, 'flyway'),
    (32, '九江', 'jiujiang', 32, '正常', '西本城市站 jiujiang.steelx2.com', 0, 'flyway'),
    (33, '济南', 'jinan', 33, '正常', '西本城市站 jinan.steelx2.com', 0, 'flyway'),
    (34, '青岛', 'qingdao', 34, '正常', '西本城市站 qingdao.steelx2.com', 0, 'flyway'),
    (35, '烟台', 'yantai', 35, '正常', '西本城市站 yantai.steelx2.com', 0, 'flyway'),
    (36, '潍坊', 'weifang', 36, '正常', '西本城市站 weifang.steelx2.com', 0, 'flyway'),
    (37, '临沂', 'linyi', 37, '正常', '西本城市站 linyi.steelx2.com', 0, 'flyway'),
    (38, '日照', 'rizhao', 38, '正常', '西本城市站 rizhao.steelx2.com', 0, 'flyway'),
    (39, '莱芜', 'laifu', 39, '正常', '西本城市站 laifu.steelx2.com', 0, 'flyway'),
    (40, '北京', 'beijing', 40, '正常', '西本城市站 beijing.steelx2.com', 0, 'flyway'),
    (41, '天津', 'tianjin', 41, '正常', '西本城市站 tianjin.steelx2.com', 0, 'flyway'),
    (42, '石家庄', 'shijiazhuang', 42, '正常', '西本城市站 shijiazhuang.steelx2.com', 0, 'flyway'),
    (43, '唐山', 'tangshan', 43, '正常', '西本城市站 tangshan.steelx2.com', 0, 'flyway'),
    (44, '邯郸', 'handan', 44, '正常', '西本城市站 handan.steelx2.com', 0, 'flyway'),
    (45, '沧州', 'cangzhou', 45, '正常', '西本城市站 cangzhou.steelx2.com', 0, 'flyway'),
    (46, '太原', 'taiyuan', 46, '正常', '西本城市站 taiyuan.steelx2.com', 0, 'flyway'),
    (47, '包头', 'baotou', 47, '正常', '西本城市站 baotou.steelx2.com', 0, 'flyway'),
    (48, '沈阳', 'shenyang', 48, '正常', '西本城市站 shenyang.steelx2.com', 0, 'flyway'),
    (49, '大连', 'dalian', 49, '正常', '西本城市站 dalian.steelx2.com', 0, 'flyway'),
    (50, '长春', 'changchun', 50, '正常', '西本城市站 changchun.steelx2.com', 0, 'flyway'),
    (51, '哈尔滨', 'haerbin', 51, '正常', '西本城市站 haerbin.steelx2.com', 0, 'flyway'),
    (52, '武汉', 'wuhan', 52, '正常', '西本城市站 wuhan.steelx2.com', 0, 'flyway'),
    (53, '郑州', 'zhengzhou', 53, '正常', '西本城市站 zhengzhou.steelx2.com', 0, 'flyway'),
    (54, '洛阳', 'luoyang', 54, '正常', '西本城市站 luoyang.steelx2.com', 0, 'flyway'),
    (55, '安阳', 'anyang', 55, '正常', '西本城市站 anyang.steelx2.com', 0, 'flyway'),
    (56, '长沙', 'changsha', 56, '正常', '西本城市站 changsha.steelx2.com', 0, 'flyway'),
    (57, '广州', 'guangzhou', 57, '正常', '西本城市站 guangzhou.steelx2.com', 0, 'flyway'),
    (58, '深圳', 'shenzhen', 58, '正常', '西本城市站 shenzhen.steelx2.com', 0, 'flyway'),
    (59, '南宁', 'nanning', 59, '正常', '西本城市站 nanning.steelx2.com', 0, 'flyway'),
    (60, '海口', 'haikou', 60, '正常', '西本城市站 haikou.steelx2.com', 0, 'flyway'),
    (61, '重庆', 'chongqing', 61, '正常', '西本城市站 chongqing.steelx2.com', 0, 'flyway'),
    (62, '成都', 'chengdo', 62, '正常', '西本城市站 chengdo.steelx2.com', 0, 'flyway'),
    (63, '贵阳', 'guiyang', 63, '正常', '西本城市站 guiyang.steelx2.com', 0, 'flyway'),
    (64, '昆明', 'kunming', 64, '正常', '西本城市站 kunming.steelx2.com', 0, 'flyway'),
    (65, '西安', 'xian', 65, '正常', '西本城市站 xian.steelx2.com', 0, 'flyway'),
    (66, '兰州', 'lanzhou', 66, '正常', '西本城市站 lanzhou.steelx2.com', 0, 'flyway'),
    (67, '西宁', 'xining', 67, '正常', '西本城市站 xining.steelx2.com', 0, 'flyway'),
    (68, '银川', 'yinchuan', 68, '正常', '西本城市站 yinchuan.steelx2.com', 0, 'flyway'),
    (69, '乌鲁木齐', 'wulumuqi', 69, '正常', '西本城市站 wulumuqi.steelx2.com', 0, 'flyway')
ON CONFLICT DO NOTHING;

-- 权限兜底登记: 权限目录由 PermissionCatalogSync 启动时按 PermissionCodes.all() 幂等同步,
-- 本段只做兜底登记, 与 V171 对 value-aliases 的处理一致。
INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES
    ('quote-regions:read', 'quote-regions', 'read', NULL, '取价地区查询'),
    ('quote-regions:create', 'quote-regions', 'create', NULL, '取价地区创建'),
    ('quote-regions:update', 'quote-regions', 'update', NULL, '取价地区编辑'),
    ('quote-regions:delete', 'quote-regions', 'delete', NULL, '取价地区删除')
ON CONFLICT (code) DO NOTHING;
