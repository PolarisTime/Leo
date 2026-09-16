-- 报单比价: 金额敏感单据的悲观"签出"编辑锁(与 mk_quote_sheet 1:1)
-- 说明: 一张单据最多一条锁记录; owner 为签出用户, expires_at 到期后可被他人抢占

CREATE TABLE public.mk_quote_sheet_edit_lock (
    id bigint NOT NULL,
    sheet_id bigint NOT NULL,
    owner_id bigint NOT NULL,
    owner_name character varying(64) NOT NULL,
    acquired_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    CONSTRAINT mk_quote_sheet_edit_lock_pkey PRIMARY KEY (id),
    CONSTRAINT uk_quote_sheet_edit_lock_sheet UNIQUE (sheet_id),
    CONSTRAINT fk_quote_sheet_edit_lock_sheet FOREIGN KEY (sheet_id)
        REFERENCES public.mk_quote_sheet(id)
);

COMMENT ON TABLE public.mk_quote_sheet_edit_lock IS '比价报价单编辑签出锁(与 mk_quote_sheet 1:1, 到期可抢占)';
COMMENT ON COLUMN public.mk_quote_sheet_edit_lock.sheet_id IS '被签出的报价单ID';
COMMENT ON COLUMN public.mk_quote_sheet_edit_lock.owner_id IS '签出用户ID';
COMMENT ON COLUMN public.mk_quote_sheet_edit_lock.owner_name IS '签出用户名称快照';
COMMENT ON COLUMN public.mk_quote_sheet_edit_lock.expires_at IS '锁到期时间, 到期后可被他人抢占';

CREATE INDEX idx_quote_sheet_edit_lock_expires ON public.mk_quote_sheet_edit_lock (expires_at);
