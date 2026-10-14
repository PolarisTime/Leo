-- 长度加价表(可维护数据): 行情匹配时按定尺加价, 如「12米 +30 元/吨」。
-- 说明:
--   1) 原先该规则同时写在 application.yml 的 leo.market.steel-quote.match.length-premiums
--      与 MysteelProperties.defaultLengthPremiums()(Java 默认值), 两处内容相同,
--      属同一业务口径的重复定义, 调整加价必须改代码并重启。
--   2) 与 md_value_alias 的区别: 那张表做的是「同一含义多种写法」的文本归一,
--      本表承载的是「定尺 -> 数值加价」, 语义不同, 故独立建表。
--   3) 加价允许为负(优惠), 不做非负约束; 同一长度只允许一条未删除记录(部分唯一索引)。
--   4) 应用侧保留原 yml 配置与 Java 默认值作为兜底, 本表为空时行为与改造前一致。
-- 幂等: 建表 IF NOT EXISTS, 种子 ON CONFLICT DO NOTHING, 权限登记 ON CONFLICT DO NOTHING。

CREATE TABLE IF NOT EXISTS public.md_length_premium (
    id bigint NOT NULL,
    length_name character varying(32) NOT NULL,
    premium integer NOT NULL,
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
    CONSTRAINT md_length_premium_pkey PRIMARY KEY (id),
    CONSTRAINT ck_length_premium_length CHECK (length_name::text <> ''::text),
    -- 状态取值必须与 StatusConstants 一致: NORMAL=正常 / DISABLED=禁用
    -- (V187 曾误写为「停用」, 见 V188 的修正说明)
    CONSTRAINT ck_length_premium_status CHECK (status::text IN ('正常', '禁用'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_length_premium_length
    ON public.md_length_premium USING btree (length_name) WHERE deleted_flag = false;

CREATE INDEX IF NOT EXISTS idx_length_premium_status
    ON public.md_length_premium USING btree (status, sort_order);

COMMENT ON TABLE public.md_length_premium IS
    '定尺加价规则: 行情匹配时按定尺对基准价加价(元/吨); 支持禁用而不删除';
COMMENT ON COLUMN public.md_length_premium.length_name IS '定尺名称(如 12米), 需与商品资料的 length 写法一致才可匹配';
COMMENT ON COLUMN public.md_length_premium.premium IS '每吨加价(元), 允许为负表示优惠';
COMMENT ON COLUMN public.md_length_premium.sort_order IS '展示与匹配顺序';
COMMENT ON COLUMN public.md_length_premium.status IS '状态: 正常 / 禁用; 仅正常参与匹配';
COMMENT ON COLUMN public.md_length_premium.deleted_flag IS '软删除标记, true 表示已删除; 仅 false 的行参与唯一性约束';

INSERT INTO public.md_length_premium
    (id, length_name, premium, sort_order, status, remark, created_by, created_name)
VALUES
    (1, '12米', 30, 1, '正常', '原 yml length-premiums 与 MysteelProperties 默认值', 0, 'flyway')
ON CONFLICT DO NOTHING;

-- 权限兜底登记: 权限目录由 PermissionCatalogSync 启动时按 PermissionCodes.all() 幂等同步,
-- 本段只做兜底登记, 与 V171 / V187 的处理一致。
INSERT INTO public.sys_permission (code, resource, action, field, description)
VALUES
    ('length-premiums:read', 'length-premiums', 'read', NULL, '定尺加价查询'),
    ('length-premiums:create', 'length-premiums', 'create', NULL, '定尺加价创建'),
    ('length-premiums:update', 'length-premiums', 'update', NULL, '定尺加价编辑'),
    ('length-premiums:delete', 'length-premiums', 'delete', NULL, '定尺加价删除')
ON CONFLICT (code) DO NOTHING;
