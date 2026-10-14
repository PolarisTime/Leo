-- mk_quote_item_purchase_order 的表/列注释补齐: 写明 V181 回填"复用 id 空间"的前提,
-- 以及此后新插入的关联行"一律独立发号"的约定。
--
-- 背景(V181): 回填现有单值关联时直接把 mk_quote_item.id 当作本表主键(见 V181 第 37-52 行),
-- 前提是"全系统 id 由同一个雪花生成器产生、生成器不会重发同一个值"(默认 machine-id=0,
-- 生成器只检测同进程内的时钟回拨), 因此 item.id 不会与后续新插入的关联行主键冲突。
-- 该前提是隐式依赖而非数据库约束, 一旦被打破(例如换机器号/多实例回拨/手工导入 id),
-- 新插入的关联行可能撞上回填行的主键。这里只补充说明, 不修改 V181。

COMMENT ON TABLE public.mk_quote_item_purchase_order IS
    '报价单行×关联的采购订单明细行(含本次开出吨位); 旧三列 mk_quote_item.purchase_order_id/purchase_order_no/purchase_order_item_id 始终镜像第一条关联。V181 回填行的 id 直接复用 mk_quote_item.id, 依赖"同一个雪花生成器不重发 id"这一前提(生成器默认 machine-id=0, 只检测同进程回拨); 此后新插入的关联行一律走同一个生成器独立发号, 不得再复用任何既有 id。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.id IS
    '主键: 新插入一律由雪花生成器独立发号(nextId), 禁止复用其它表/其它行的 id; 仅 V181 回填行复用了 mk_quote_item.id。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.item_id IS
    '所属报价单商品行(mk_quote_item.id); 与 purchase_order_item_id 共同唯一。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.purchase_order_id IS
    '关联采购订单标识(计划态引用, 刻意不加外键: 订单删除后需保留历史关联与订单号快照)。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.purchase_order_no IS
    '关联采购订单号快照(保存时按 purchase_order_id 解析写入), 保证订单号变更/删除后仍可读。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.purchase_order_item_id IS
    '关联采购订单明细行标识(按规格扣减已开吨位的真源); 必须真实存在且属于 purchase_order_id 指向的订单。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.allocated_ton IS
    '本次在该订单明细行开出的吨位(默认按关联顺序填满本行报单吨位); 同一行各条之和必须等于 mk_quote_item.ton。';

COMMENT ON COLUMN public.mk_quote_item_purchase_order.sort_no IS
    '关联顺序(自动分配与展示顺序, 从 0 开始); 旧三列镜像 sort_no 最小的一条。';
