ALTER TABLE public.sys_operation_log
    ADD COLUMN event_id uuid,
    ADD COLUMN trace_id character varying(64),
    ADD COLUMN aggregate_type character varying(64),
    ADD COLUMN event_version integer;

CREATE UNIQUE INDEX uk_sys_operation_log_event_id
    ON public.sys_operation_log (event_id)
    WHERE event_id IS NOT NULL;

CREATE INDEX idx_sys_operation_log_trace_id
    ON public.sys_operation_log (trace_id)
    WHERE trace_id IS NOT NULL;

CREATE INDEX idx_sys_operation_log_aggregate
    ON public.sys_operation_log (aggregate_type, record_id, operation_time DESC)
    WHERE aggregate_type IS NOT NULL AND record_id IS NOT NULL;

COMMENT ON COLUMN public.sys_operation_log.event_id IS '可靠领域事件唯一标识，用于审计消费幂等';
COMMENT ON COLUMN public.sys_operation_log.trace_id IS '关联技术日志与分布式链路的Trace ID';
COMMENT ON COLUMN public.sys_operation_log.aggregate_type IS '业务聚合类型';
COMMENT ON COLUMN public.sys_operation_log.event_version IS '领域事件载荷版本';

CREATE TABLE public.event_publication (
    id uuid PRIMARY KEY,
    publication_date timestamp with time zone NOT NULL,
    listener_id character varying(512) NOT NULL,
    serialized_event text NOT NULL,
    event_type character varying(512) NOT NULL,
    completion_date timestamp with time zone
);

CREATE INDEX idx_event_publication_incomplete
    ON public.event_publication (publication_date, id)
    WHERE completion_date IS NULL;

COMMENT ON TABLE public.event_publication IS 'Spring Modulith可靠事件发布注册表';
