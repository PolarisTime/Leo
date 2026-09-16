-- 比价报价单: 单据级"锁定报单规格和数量"开关 + 明细行计量方式(吨位/件数)
-- 说明:
--   1) spec_quantity_locked 与既有 locked(锁定参照) 是相互独立的两个锁;
--   2) 明细行按行独立选择 quantity_mode: TON(报单吨位, 默认) / PIECES(报单件数);
--   3) pieces 仅 PIECES 模式使用, piece_weight_ton 为该行件重快照;
--   4) ton 始终是权威重量: PIECES 模式由客户端按 pieces × piece_weight_ton 计算后写入。
-- 历史行通过列默认值保持可读(TON + spec_quantity_locked=false)。

ALTER TABLE public.mk_quote_sheet
    ADD COLUMN spec_quantity_locked boolean DEFAULT false NOT NULL;

COMMENT ON COLUMN public.mk_quote_sheet.spec_quantity_locked IS '锁定报单规格和数量(与 locked 锁定参照相互独立)';

ALTER TABLE public.mk_quote_item
    ADD COLUMN quantity_mode character varying(16) DEFAULT 'TON'::character varying NOT NULL,
    ADD COLUMN pieces integer,
    ADD COLUMN piece_weight_ton numeric(18,8);

COMMENT ON COLUMN public.mk_quote_item.quantity_mode IS '行计量方式: TON(报单吨位)/PIECES(报单件数)';
COMMENT ON COLUMN public.mk_quote_item.pieces IS '件数, 仅 PIECES 模式使用';
COMMENT ON COLUMN public.mk_quote_item.piece_weight_ton IS '该行件重快照(吨/件), 用于件数↔吨位换算与展示';
COMMENT ON COLUMN public.mk_quote_item.ton IS '权威重量(吨): TON=用户输入; PIECES=客户端按 pieces×piece_weight_ton 计算后写入';
