CREATE SEQUENCE public.jv_global_id_pk_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE public.jv_commit_pk_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE public.jv_snapshot_pk_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE public.jv_global_id (
    global_id_pk bigint NOT NULL,
    local_id character varying(191),
    fragment character varying(200),
    type_name character varying(200),
    owner_id_fk bigint,
    CONSTRAINT jv_global_id_pk PRIMARY KEY (global_id_pk),
    CONSTRAINT jv_global_id_owner_id_fk FOREIGN KEY (owner_id_fk)
        REFERENCES public.jv_global_id (global_id_pk)
);

CREATE INDEX jv_global_id_local_id_idx ON public.jv_global_id (local_id);
CREATE INDEX jv_global_id_owner_id_fk_idx ON public.jv_global_id (owner_id_fk);

CREATE TABLE public.jv_commit (
    commit_pk bigint NOT NULL,
    author character varying(200),
    commit_date timestamp without time zone,
    commit_date_instant character varying(30),
    commit_id numeric(22, 2),
    CONSTRAINT jv_commit_pk PRIMARY KEY (commit_pk)
);

CREATE INDEX jv_commit_commit_id_idx ON public.jv_commit (commit_id);

CREATE TABLE public.jv_commit_property (
    commit_fk bigint NOT NULL,
    property_name character varying(191) NOT NULL,
    property_value character varying(600),
    CONSTRAINT jv_commit_property_pk PRIMARY KEY (commit_fk, property_name),
    CONSTRAINT jv_commit_property_commit_fk FOREIGN KEY (commit_fk)
        REFERENCES public.jv_commit (commit_pk)
);

CREATE INDEX jv_commit_property_commit_fk_idx ON public.jv_commit_property (commit_fk);
CREATE INDEX jv_commit_property_property_name_property_value_idx
    ON public.jv_commit_property (property_name, property_value);

CREATE TABLE public.jv_snapshot (
    snapshot_pk bigint NOT NULL,
    type character varying(200),
    version bigint,
    state text,
    changed_properties text,
    managed_type character varying(200),
    global_id_fk bigint,
    commit_fk bigint,
    CONSTRAINT jv_snapshot_pk PRIMARY KEY (snapshot_pk),
    CONSTRAINT jv_snapshot_global_id_fk FOREIGN KEY (global_id_fk)
        REFERENCES public.jv_global_id (global_id_pk),
    CONSTRAINT jv_snapshot_commit_fk FOREIGN KEY (commit_fk)
        REFERENCES public.jv_commit (commit_pk)
);

CREATE INDEX jv_snapshot_global_id_fk_idx ON public.jv_snapshot (global_id_fk);
CREATE INDEX jv_snapshot_commit_fk_idx ON public.jv_snapshot (commit_fk);

CREATE TABLE public.jv_business_event (
    event_id uuid PRIMARY KEY,
    aggregate_type character varying(64) NOT NULL,
    aggregate_id bigint NOT NULL,
    action_type character varying(32) NOT NULL,
    processed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE public.jv_business_event IS 'JaVers可靠事件消费幂等认领表';
