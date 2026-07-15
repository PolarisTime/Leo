LOCK TABLE public.sys_operation_log IN ACCESS EXCLUSIVE MODE;

ALTER TABLE public.sys_operation_log RENAME TO sys_operation_log_unpartitioned;
ALTER TABLE public.sys_operation_log_unpartitioned
    DROP CONSTRAINT sys_operation_log_pkey,
    DROP CONSTRAINT sys_operation_log_log_no_key;

DROP INDEX public.idx_operation_log_brin_time;
DROP INDEX public.idx_sys_operation_log_business_no;
DROP INDEX public.idx_sys_operation_log_module;
DROP INDEX public.idx_sys_operation_log_operator;
DROP INDEX public.idx_sys_operation_log_time;
DROP INDEX public.idx_sys_operation_log_trace_id;
DROP INDEX public.idx_sys_operation_log_aggregate;
DROP INDEX public.uk_sys_operation_log_event_id;

CREATE TABLE public.sys_operation_log_identity (
    id bigint PRIMARY KEY,
    log_no character varying(64) NOT NULL UNIQUE,
    event_id uuid UNIQUE,
    operation_time timestamp without time zone NOT NULL
);

CREATE INDEX idx_sys_operation_log_identity_time
    ON public.sys_operation_log_identity (operation_time);

CREATE TABLE public.sys_operation_log (
    id bigint NOT NULL,
    log_no character varying(64) NOT NULL,
    operator_id bigint,
    operator_name character varying(64) NOT NULL,
    login_name character varying(64) NOT NULL,
    module_name character varying(64) NOT NULL,
    action_type character varying(32) NOT NULL,
    business_no character varying(64),
    request_method character varying(8) NOT NULL,
    request_path character varying(255) NOT NULL,
    client_ip character varying(64),
    result_status character varying(16) NOT NULL,
    operation_time timestamp without time zone NOT NULL,
    remark character varying(255),
    record_id bigint,
    module_key character varying(64),
    auth_type character varying(16),
    event_id uuid,
    trace_id character varying(64),
    aggregate_type character varying(64),
    event_version integer,
    CONSTRAINT sys_operation_log_pkey PRIMARY KEY (operation_time, id)
) PARTITION BY RANGE (operation_time);

DO $$
DECLARE
    partition_month date;
    final_month date;
    partition_name text;
BEGIN
    SELECT date_trunc('month', COALESCE(min(operation_time), CURRENT_TIMESTAMP))::date
      INTO partition_month
      FROM public.sys_operation_log_unpartitioned;
    final_month := (date_trunc('month', CURRENT_TIMESTAMP) + interval '10 years')::date;

    WHILE partition_month < final_month LOOP
        partition_name := format('sys_operation_log_y%sm%s',
                to_char(partition_month, 'YYYY'), to_char(partition_month, 'MM'));
        EXECUTE format(
                'CREATE TABLE public.%I PARTITION OF public.sys_operation_log FOR VALUES FROM (%L) TO (%L)',
                partition_name,
                partition_month,
                (partition_month + interval '1 month')::date
        );
        partition_month := (partition_month + interval '1 month')::date;
    END LOOP;
END
$$;

CREATE TABLE public.sys_operation_log_default
    PARTITION OF public.sys_operation_log DEFAULT;

CREATE OR REPLACE FUNCTION public.enforce_operation_log_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.sys_operation_log_identity (id, log_no, event_id, operation_time)
        VALUES (NEW.id, NEW.log_no, NEW.event_id, NEW.operation_time);
        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.id IS DISTINCT FROM NEW.id
                OR OLD.log_no IS DISTINCT FROM NEW.log_no
                OR OLD.event_id IS DISTINCT FROM NEW.event_id
                OR OLD.operation_time IS DISTINCT FROM NEW.operation_time THEN
            UPDATE public.sys_operation_log_identity
               SET id = NEW.id,
                   log_no = NEW.log_no,
                   event_id = NEW.event_id,
                   operation_time = NEW.operation_time
             WHERE id = OLD.id;
        END IF;
        RETURN NEW;
    END IF;

    DELETE FROM public.sys_operation_log_identity WHERE id = OLD.id;
    RETURN OLD;
END
$$;

CREATE TRIGGER trg_sys_operation_log_identity
BEFORE INSERT OR UPDATE OR DELETE ON public.sys_operation_log
FOR EACH ROW EXECUTE FUNCTION public.enforce_operation_log_identity();

INSERT INTO public.sys_operation_log
SELECT * FROM public.sys_operation_log_unpartitioned;

DO $$
DECLARE
    source_count bigint;
    target_count bigint;
    identity_count bigint;
BEGIN
    SELECT count(*) INTO source_count FROM public.sys_operation_log_unpartitioned;
    SELECT count(*) INTO target_count FROM public.sys_operation_log;
    SELECT count(*) INTO identity_count FROM public.sys_operation_log_identity;
    IF source_count <> target_count OR target_count <> identity_count THEN
        RAISE EXCEPTION '操作日志分区迁移数量不一致: source=%, target=%, identity=%',
                source_count, target_count, identity_count;
    END IF;
END
$$;

CREATE INDEX idx_sys_operation_log_id ON public.sys_operation_log (id);
CREATE INDEX idx_sys_operation_log_log_no ON public.sys_operation_log (log_no);
CREATE INDEX idx_sys_operation_log_event_id
    ON public.sys_operation_log (event_id) WHERE event_id IS NOT NULL;
CREATE INDEX idx_operation_log_brin_time
    ON public.sys_operation_log USING brin (operation_time) WITH (pages_per_range = 64);
CREATE INDEX idx_sys_operation_log_business_no ON public.sys_operation_log (business_no);
CREATE INDEX idx_sys_operation_log_module ON public.sys_operation_log (module_name);
CREATE INDEX idx_sys_operation_log_operator ON public.sys_operation_log (login_name);
CREATE INDEX idx_sys_operation_log_time ON public.sys_operation_log (operation_time);
CREATE INDEX idx_sys_operation_log_trace_id
    ON public.sys_operation_log (trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX idx_sys_operation_log_aggregate
    ON public.sys_operation_log (aggregate_type, record_id, operation_time DESC)
    WHERE aggregate_type IS NOT NULL AND record_id IS NOT NULL;

COMMENT ON TABLE public.sys_operation_log IS '按operation_time月度分区的业务操作日志';
COMMENT ON TABLE public.sys_operation_log_unpartitioned IS 'V81切换前操作日志，只读保留用于迁移核对';
COMMENT ON TABLE public.sys_operation_log_identity IS '分区表ID、日志号和事件ID的全局唯一注册表';
COMMENT ON COLUMN public.sys_operation_log.record_id IS '关联的业务记录ID';
COMMENT ON COLUMN public.sys_operation_log.module_key IS '关联的业务模块key';
COMMENT ON COLUMN public.sys_operation_log.event_id IS '可靠领域事件唯一标识，用于审计消费幂等';
COMMENT ON COLUMN public.sys_operation_log.trace_id IS '关联技术日志与分布式链路的Trace ID';
COMMENT ON COLUMN public.sys_operation_log.aggregate_type IS '业务聚合类型';
COMMENT ON COLUMN public.sys_operation_log.event_version IS '领域事件载荷版本';
