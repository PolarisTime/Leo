-- V199: 操作日志记录详细度增强
--
-- 背景: 现有 sys_operation_log 只记录 12 个业务字段, 失败原因/耗时/请求上下文均不可查询
-- (实测 4 万条中 76% 无单据号, 93% 无 traceId), 且无法区分会话类与业务类操作。
-- 本迁移补齐「HTTP 状态、耗时、UA、结构化错误、脱敏请求体、操作类别」六类信息, 并为
-- 类别筛选、慢操作筛选、单据号+时间查询建立索引。
--
-- 语义约定:
--   * status_code       HTTP 响应状态码 (100..599); 历史数据为空。
--   * duration_ms       拦截器 preHandle 到 afterCompletion 的毫秒耗时 (>= 0); 历史数据为空,
--                       空值不参与慢操作筛选与平均耗时统计。
--   * user_agent        请求 User-Agent 头, 截断 512 字符。
--   * error_code        失败时的结构化错误码: BusinessException 取业务码 (如 4000),
--                       其它异常取异常类简名; 成功为空。
--   * error_message     失败时的用户可读错误信息 (最长 4000 字符, 列类型 text 不再截到 255)。
--   * request_body      脱敏后的请求体 JSON (password/token/accessToken/refreshToken/secret/
--                       authorization 等键的值替换为 "***"); 仅非 GET/HEAD 且总体不超过 8KB,
--                       非 JSON 请求体存 {"raw": "截断文本"}; 历史数据不回填。
--   * operation_category 操作类别: SESSION(会话类 /auth/**)、SYSTEM(系统类 /system/**、actuator)、
--                       BUSINESS(业务类, 默认值)。历史 4 万条数据统一归为 BUSINESS。

ALTER TABLE public.sys_operation_log
    ADD COLUMN status_code integer,
    ADD COLUMN duration_ms integer,
    ADD COLUMN user_agent character varying(512),
    ADD COLUMN error_code character varying(64),
    ADD COLUMN error_message text,
    ADD COLUMN request_body jsonb,
    ADD COLUMN operation_category character varying(16) NOT NULL DEFAULT 'BUSINESS';

ALTER TABLE public.sys_operation_log
    ADD CONSTRAINT chk_sys_operation_log_operation_category
        CHECK (operation_category IN ('SESSION', 'BUSINESS', 'SYSTEM')),
    ADD CONSTRAINT chk_sys_operation_log_duration_ms
        CHECK (duration_ms IS NULL OR duration_ms >= 0),
    ADD CONSTRAINT chk_sys_operation_log_status_code
        CHECK (status_code IS NULL OR (status_code >= 100 AND status_code <= 599));

COMMENT ON COLUMN public.sys_operation_log.status_code IS 'HTTP 响应状态码(100..599); 历史数据为空';
COMMENT ON COLUMN public.sys_operation_log.duration_ms IS '请求处理耗时(毫秒, >=0); 历史数据为空, 空值不参与慢操作筛选';
COMMENT ON COLUMN public.sys_operation_log.user_agent IS '请求 User-Agent 头, 截断 512 字符';
COMMENT ON COLUMN public.sys_operation_log.error_code IS '失败时的结构化错误码: BusinessException 业务码或异常类简名; 成功为空';
COMMENT ON COLUMN public.sys_operation_log.error_message IS '失败时的用户可读错误信息, 最长 4000 字符; 成功为空';
COMMENT ON COLUMN public.sys_operation_log.request_body IS '脱敏后的请求体 JSON(总体 <=8KB, 非 JSON 存 {"raw": "..."}); GET/HEAD 不写; 历史数据不回填';
COMMENT ON COLUMN public.sys_operation_log.operation_category IS '操作类别: SESSION 会话类, BUSINESS 业务类(默认), SYSTEM 系统类';

CREATE INDEX idx_sys_operation_log_category
    ON public.sys_operation_log (operation_category);
COMMENT ON INDEX public.idx_sys_operation_log_category IS '操作日志类别筛选(SESSION/BUSINESS/SYSTEM)';

CREATE INDEX idx_sys_operation_log_duration_ms
    ON public.sys_operation_log (duration_ms) WHERE duration_ms IS NOT NULL;
COMMENT ON INDEX public.idx_sys_operation_log_duration_ms IS '慢操作筛选 minDurationMs; 仅索引有耗时的行';

CREATE INDEX idx_sys_operation_log_business_time
    ON public.sys_operation_log (business_no, operation_time DESC) WHERE business_no IS NOT NULL;
COMMENT ON INDEX public.idx_sys_operation_log_business_time IS '按单据号查该单据操作时间线';
