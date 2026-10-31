package com.leo.erp.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 已过账销售退货只读查询端口。
 *
 * <p>库存期初回填需要按 (业务日期, id) keyset 分页扫描「已审核」的销售退货单并读取值快照。
 * 该能力此前由回填服务直接依赖退货实体与仓储实现，属于跨模块访问 sales 内部；
 * 现收敛为本端口，调用方只依赖 api 包。</p>
 *
 * <p>实现语义必须与本模块仓储查询逐字一致：{@code deleted_flag = false}、
 * 状态等于 {@code status}、按 (returnDate, id) 升序、最多返回 {@code limit} 条；
 * 明细必须在同一只读事务内初始化后再映射为快照，不得把实体或懒加载代理暴露给调用方。</p>
 */
public interface SalesReturnPostedQuery {

    /**
     * 按游标扫描一页已过账退货单。
     *
     * @param status    允许的退货单状态
     * @param afterDate 游标业务日期（含等于时用 {@code afterId} 继续比较）
     * @param afterId   游标单据 id
     * @param limit     单页最大条数
     * @return 按 (returnDate, id) 升序的单据快照；没有更多数据时返回空列表
     */
    List<PostedReturnSnapshot> findPostedAfter(String status,
                                               LocalDate afterDate,
                                               long afterId,
                                               int limit);

    /** 退货单头 + 明细快照。 */
    record PostedReturnSnapshot(
            Long id,
            String returnNo,
            LocalDate returnDate,
            Long warehouseId,
            String warehouseName,
            List<LineSnapshot> lines) {
        public PostedReturnSnapshot {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /** 退货明细快照；数量保持可空，由调用方按原口径归一。 */
    record LineSnapshot(
            Long id,
            Long materialId,
            String materialCode,
            Long warehouseId,
            String warehouseName,
            String batchNo,
            Integer quantity,
            String quantityUnit,
            BigDecimal unitPrice) {
    }
}
