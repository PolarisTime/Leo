-- 项目允许品牌/材质新增「处理方式」: 决定匹配不上时是阻断还是仅提醒(力度可配)。
-- 背景: V176 引入的允许品牌/材质只有「提醒」一种力度, 业务无法按项目性质区分:
--       业主要求锁定的项目必须拦住(否则等于没管), 而参考性项目只需要提示。
-- 收敛口径(三家主流 ERP 的共同做法是「同一规则、力度可配」):
--   1) OFF   —— 保留清单但关闭校验(清单作为参考信息留存, 不回退成删除数据);
--   2) WARN  —— 仅提醒, 不阻断(与 V176 上线时的行为一致, 因此历史数据默认落到这一档);
--   3) BLOCK —— 阻断: 销售订单保存时返回 422, 导入采购来源时在前端直接拦住。
-- 说明:
--   1) 两列可空: 清单为空(=不限制)时置空; 清单非空但未指定力度时按 WARN 处理,
--      保证 V176 已配置的项目升级后行为不变, 不需要数据回填;
--   2) 力度是配置项而不是代码分支: 业务改力度只改数据, 不需要发版;
--   3) BLOCK 属于「业务约定」而非「系统硬约束」, 提示文案保留「或同等档次」的例外通道
--      (招标投标法实施条例禁止限定特定品牌, 见 docs/specs 中的设计说明)。

ALTER TABLE public.md_project
    ADD COLUMN allowed_brand_severity character varying(16),
    ADD COLUMN allowed_material_severity character varying(16),
    ADD CONSTRAINT chk_project_allowed_brand_severity
        CHECK (allowed_brand_severity IS NULL OR allowed_brand_severity IN ('OFF', 'WARN', 'BLOCK')),
    ADD CONSTRAINT chk_project_allowed_material_severity
        CHECK (allowed_material_severity IS NULL OR allowed_material_severity IN ('OFF', 'WARN', 'BLOCK'));

COMMENT ON COLUMN public.md_project.allowed_brand_severity IS
    '允许品牌不匹配时的处理方式: OFF关闭校验/WARN仅提醒/BLOCK阻断保存; 空表示清单为空或按WARN处理';
COMMENT ON COLUMN public.md_project.allowed_material_severity IS
    '允许材质不匹配时的处理方式: OFF关闭校验/WARN仅提醒/BLOCK阻断保存; 空表示清单为空或按WARN处理';
