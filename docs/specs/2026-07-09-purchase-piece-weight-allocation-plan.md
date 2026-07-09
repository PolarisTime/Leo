# 采购逐件重量分配写入计划

## 背景

采购订单中盘螺等按件管理的材料存在“`N` 件 / `M` 吨”的业务场景。当前系统已经使用
`po_purchase_order_item_piece_weight` 作为逐件重量账本，销售订单从该账本锁定具体件号并把锁定件重量合计回写到销售明细。

现有生产数据校验结果表明，数据库 8 位精确值层面满足：

```text
采购明细重量 = 已分配逐件重量 + 未分配逐件重量
销售明细重量 = 该销售明细锁定的逐件重量合计
```

但页面和打印通常按 3 位小数展示逐件重量。若逐件账本保存 8 位小数，用户按 3 位展示值逐行相加时可能看到多或少。例如：

- `11` 件 / `21.536` 吨，精确每件约 `1.95781818`，展示为 `1.958` 后合计 `21.538`。
- `7` 件 / `13.702` 吨，精确每件约 `1.95742857`，展示为 `1.957` 后合计 `13.699`。

因此需要把尾差处理前移到逐件账本写入阶段，保证后续无论拆给几个客户，按数据库值和按 3 位展示值汇总都不多不少。

## 目标

1. 采购明细写入逐件账本时，逐件重量按 3 位小数生成。
2. 同一采购明细内，所有逐件重量合计必须等于采购明细重量 `M`。
3. 销售订单拆分给任意 `X` 个客户时，每张销售明细重量必须等于其锁定的逐件重量合计。
4. 任意时刻都满足：

```text
客户1已分配重量 + 客户2已分配重量 + ... + 客户X已分配重量 + 未分配重量 = M
```

5. 保持现有“逐件账本 + 销售明细锁定件号”的模型，不引入新的分配表或复杂抽象。

## 非目标

- 不按“先出大的”或“先出小的”定义业务语义；只要求总量守恒。
- 不在页面或打印层临时修正尾差。
- 不通过销售订单明细重新平均计算重量。
- 不直接修改生产库历史数据；历史数据修复如有需要，应单独走 Flyway 或受控数据修复流程。

## 核心不变量

### 采购逐件账本不变量

对每条采购订单明细：

```text
purchase_item.quantity = N
purchase_item.weight_ton = M
piece_count = N
sum(piece.weight_ton) = M
piece.weight_ton = round(piece.weight_ton, 3)
```

### 销售分配不变量

对每条销售订单明细：

```text
sales_item.quantity = bound_piece_count
sales_item.weight_ton = sum(bound_piece.weight_ton)
```

### 整体守恒不变量

对每条采购订单明细：

```text
sum(allocated_piece.weight_ton) + sum(unallocated_piece.weight_ton) = purchase_item.weight_ton
```

这些不变量应通过单元测试、集成测试和生产只读 SQL 校验共同保障。

## 写入算法

逐件账本生成时使用 3 位重量单位，即 `0.001` 吨。

输入：

```text
N = 件数
M = 明细重量，单位吨
UNIT = 0.001
total_units = M * 1000
```

要求：`M` 必须可被 3 位小数表示。若未来允许采购明细保存超过 3 位小数，则逐件保留 3 位与合计等于原始 8 位重量不可同时满足，需要先明确业务舍入口径。

算法：

```text
base_units = total_units / N
residual_units = total_units % N

前 residual_units 件重量 = (base_units + 1) / 1000
剩余件重量 = base_units / 1000
```

示例：

```text
21.536 吨 / 11 件
total_units = 21536
base_units = 1957
residual_units = 9

9 件 * 1.958 + 2 件 * 1.957 = 21.536
```

```text
13.702 吨 / 7 件
total_units = 13702
base_units = 1957
residual_units = 3

3 件 * 1.958 + 4 件 * 1.957 = 13.702
```

## 技术依据与实现细节

### 小数处理原则

本方案禁止使用 `double`、`float` 或字符串截断处理重量。实现必须使用 `BigDecimal`，并尽早转成整数最小单位计算：

```text
0.001 吨 = 1 个最小重量单位
```

原因：

- Java `BigDecimal` 是任意精度十进制数，适合业务重量、金额这类需要可控舍入的场景。
- `BigDecimal.movePointRight(3)` 可把吨数转换为 0.001 吨整数单位。
- `BigDecimal.toBigIntegerExact()` 可在存在非 3 位小数时直接抛错，避免静默丢精度。
- PostgreSQL `numeric` 是精确数值类型，适合保存需要精确计算的业务数量。

推荐实现伪代码：

```java
private List<BigDecimal> splitWeightByDisplayUnit(BigDecimal sourceWeightTon, int quantity) {
    if (quantity <= 0) {
        return List.of();
    }

    BigDecimal normalizedWeight = TradeItemCalculator.safeBigDecimal(sourceWeightTon)
            .setScale(PrecisionConstants.DISPLAY_WEIGHT_SCALE, RoundingMode.UNNECESSARY);
    BigInteger totalUnits = normalizedWeight
            .movePointRight(PrecisionConstants.DISPLAY_WEIGHT_SCALE)
            .toBigIntegerExact();

    BigInteger[] quotientAndRemainder = totalUnits.divideAndRemainder(BigInteger.valueOf(quantity));
    BigInteger baseUnits = quotientAndRemainder[0];
    int residualCount = quotientAndRemainder[1].intValueExact();

    List<BigDecimal> weights = new ArrayList<>(quantity);
    for (int i = 0; i < quantity; i++) {
        BigInteger pieceUnits = i < residualCount ? baseUnits.add(BigInteger.ONE) : baseUnits;
        weights.add(new BigDecimal(pieceUnits, PrecisionConstants.DISPLAY_WEIGHT_SCALE));
    }
    return weights;
}
```

关键点：

- 使用 `RoundingMode.UNNECESSARY`，让非 3 位重量在写入阶段暴露为异常，而不是自动四舍五入。
- 使用整数单位 `BigInteger` 做 `divideAndRemainder`，避免小数除法产生循环小数。
- 使用 `new BigDecimal(pieceUnits, DISPLAY_WEIGHT_SCALE)` 从整数单位还原重量，确保每件都是 3 位小数。
- 生成后必须汇总校验：`weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(normalizedWeight) == 0`。

如果业务决定允许输入超过 3 位小数，则不能再使用 `UNNECESSARY`，必须先明确统一舍入点，例如采购明细入库时就把 `M` 舍入到 3 位；否则无法同时满足“逐件 3 位”和“合计等于原始 8 位 `M`”。

### 数据库字段策略

现有字段可继续使用：

```sql
po_purchase_order_item_piece_weight.weight_ton numeric(18, 8)
```

不建议把字段改为 `numeric(18, 3)`：

- 当前表和上下游实体已经按 8 位内部重量精度建模。
- PostgreSQL `numeric(precision, scale)` 会按声明 scale 处理输入值；把字段缩到 3 位属于 schema 行为变更，会影响历史数据和所有调用方。
- 保持 `numeric(18,8)`，只在业务写入时生成 3 位值，能最小化迁移风险。
- 因字段声明为 `numeric(18,8)`，查询结果可能显示为 `1.95800000`；验收口径不是 BigDecimal scale 小于等于 3，而是数值满足 `weight_ton = round(weight_ton, 3)`。

若后续要用数据库约束强化 3 位写入，可另起迁移评审，例如增加检查约束：

```sql
ALTER TABLE po_purchase_order_item_piece_weight
    ADD CONSTRAINT ck_po_item_piece_weight_scale_3
    CHECK (weight_ton = round(weight_ton, 3));
```

该约束会影响历史 8 位逐件数据，不能随本次代码改动直接加入；必须先完成历史数据评估和修复。

### 并发分配策略

销售分配必须继续依赖 `findAvailableByPurchaseOrderItemIdForUpdate(...)`：

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select piece
        from PurchaseOrderItemPieceWeight piece
        where piece.purchaseOrderItemId = :purchaseOrderItemId
          and piece.salesOrderItemId is null
        order by piece.pieceNo asc
        """)
List<PurchaseOrderItemPieceWeight> findAvailableByPurchaseOrderItemIdForUpdate(...);
```

保留悲观写锁的原因：

- 同一采购明细可能被两个销售订单同时分配。
- 可用件查询和 `salesOrderItemId` 回写必须在同一事务内串行化。
- 若取消锁，两个事务可能读到同一批未分配件，导致同一件被重复分配或后提交覆盖先提交。

### 事务边界

`allocateForSalesOrderItem(...)` 必须保持 `@Transactional`：

1. 查询可用件并加锁。
2. 校验可用件数。
3. 写入 `salesOrderItemId`。
4. 返回锁定件重量合计。

销售订单服务只消费返回值并写入销售明细重量，不再进行二次平均计算。

### 重平衡细节

采购入库实际重量变化后，`rebalanceForPurchaseOrderItems(...)` 需要继续遵守：

```text
锁定销售件重量不变
剩余目标重量 = 新采购目标重量 - 锁定销售件重量
未锁定件按 3 位单位重新分摊剩余目标重量
```

如果锁定销售件重量已经大于新的采购目标重量，继续抛业务异常，不自动改写已锁定销售件。

## 代码改造点

### `PurchaseOrderItemPieceWeightService`

改造 `buildPieceWeightsForSlots(...)`：

- 使用 `PrecisionConstants.DISPLAY_WEIGHT_SCALE` 对逐件账本进行 3 位单位分配。
- 不再使用 8 位平均重量作为逐件写入值。
- 保留现有 `PieceSlot` 机制，兼容重平衡时复用已有件号和销售明细绑定。
- 生成后可在方法内做一次防御式校验：生成件数等于 slot 数量，生成重量合计等于目标重量。

### `allocateForSalesOrderItem(...)`

保持当前模型：

- 加悲观锁查询可用件。
- 按件号顺序锁定指定数量的件。
- 返回锁定件重量合计。

该方法不应重新计算平均重量。销售明细重量必须来自逐件账本求和。

### `SalesOrderPurchaseAllocationService`

保持当前写入方式：

- 调用 `allocateForSalesOrderItem(...)`。
- 用返回值覆盖 `SalesOrderItem.weightTon`。
- 订单总重量由销售明细重量累加。

不应在销售服务中重复处理尾差。

## 历史数据处理

本计划优先保证新写入数据正确。历史数据是否修复分两类：

1. 数据库精确值已守恒，仅 3 位展示不守恒：
   - 不需要立即修复生产数据。
   - 可在后续低风险窗口提供受控修复脚本，把未结算、未锁定或业务允许重算的逐件数据改为 3 位账本。

2. 数据库精确值不守恒：
   - 必须单独排查。
   - 修复必须走 Flyway 或明确审批的数据修复脚本。
   - 禁止绕过 Flyway 直接持久化修改生产库。

已有销售出库、对账、开票、收款等下游单据的采购明细，不应自动重写已锁定逐件重量。

## 测试计划

### 单元测试

在 `PurchaseOrderItemPieceWeightServiceTest` 增加覆盖：

1. `11` 件 / `21.536` 吨生成：
   - 9 件 `1.958`
   - 2 件 `1.957`
   - 合计 `21.536`
   - 所有逐件重量满足 `weight = round(weight, 3)`

2. `7` 件 / `13.702` 吨生成：
   - 3 件 `1.958`
   - 4 件 `1.957`
   - 合计 `13.702`

3. 多客户拆分：
   - 同一采购明细 `11` 件 / `21.536` 吨。
   - 依次分配给 3 张销售明细，例如 `3 + 4 + 4`。
   - 每次返回重量等于本次锁定件合计。
   - 三次返回重量合计等于 `21.536`。
   - 剩余重量为 `0`。

4. 部分销售：
   - 分配 `3` 件后，已分配重量 + 未分配重量 = `M`。
   - 后续继续分配剩余件，总合计仍等于 `M`。

5. 重平衡：
   - 已锁定销售件保持不变。
   - 未锁定件按 3 位重新分摊剩余重量。
   - 锁定重量 + 重平衡后未锁定重量 = 目标重量。

### 服务层测试

在 `SalesOrderPurchaseAllocationServiceTest` 或现有销售订单服务测试中补充：

- 销售订单来源采购明细时，销售明细 `weightTon` 使用 `allocateForSalesOrderItem(...)` 返回值，而不是请求中的 `pieceWeightTon * quantity`。
- 一张采购明细拆成多张销售单后，销售明细重量合计等于采购明细重量。

### SQL 只读验收

上线后可用只读 SQL 校验：

```sql
WITH piece_summary AS (
    SELECT poi.id AS purchase_order_item_id,
           poi.quantity,
           poi.weight_ton AS purchase_weight_ton,
           COUNT(pw.id) AS piece_count,
           COALESCE(SUM(pw.weight_ton), 0) AS piece_total_weight_ton,
           COALESCE(SUM(pw.weight_ton) FILTER (WHERE pw.sales_order_item_id IS NOT NULL), 0) AS allocated_weight_ton,
           COALESCE(SUM(pw.weight_ton) FILTER (WHERE pw.sales_order_item_id IS NULL), 0) AS remaining_weight_ton
      FROM po_purchase_order_item poi
      LEFT JOIN po_purchase_order_item_piece_weight pw ON pw.purchase_order_item_id = poi.id
     GROUP BY poi.id, poi.quantity, poi.weight_ton
)
SELECT *
  FROM piece_summary
 WHERE piece_count <> quantity
    OR piece_total_weight_ton <> purchase_weight_ton
    OR allocated_weight_ton + remaining_weight_ton <> purchase_weight_ton
    OR EXISTS (
        SELECT 1
          FROM po_purchase_order_item_piece_weight pw
         WHERE pw.purchase_order_item_id = piece_summary.purchase_order_item_id
           AND pw.weight_ton <> round(pw.weight_ton, 3)
    );
```

```sql
WITH sales_piece_summary AS (
    SELECT soi.id AS sales_order_item_id,
           soi.quantity,
           soi.weight_ton AS sales_item_weight_ton,
           COUNT(pw.id) AS bound_piece_count,
           COALESCE(SUM(pw.weight_ton), 0) AS bound_piece_weight_ton
      FROM so_sales_order_item soi
      LEFT JOIN po_purchase_order_item_piece_weight pw ON pw.sales_order_item_id = soi.id
     WHERE soi.source_purchase_order_item_id IS NOT NULL
     GROUP BY soi.id, soi.quantity, soi.weight_ton
)
SELECT *
  FROM sales_piece_summary
 WHERE bound_piece_count <> quantity
    OR bound_piece_weight_ton <> sales_item_weight_ton;
```

并发验收建议：

- 使用两个事务同时对同一采购明细分配销售订单。
- 验证第二个事务只能看到第一事务提交后剩余的未分配件。
- 验证没有重复 `sales_order_item_id` 覆盖和没有同一 `piece_no` 被两个销售明细同时占用。

## 风险与约束

- 若采购明细重量 `M` 不是 3 位小数，逐件 3 位无法精确合计到原始 `M`。需要业务确认入库和订单重量是否统一限制为 3 位。
- 已锁定销售件不能随意改重，否则会影响销售出库、对账、开票、收款链路。
- 重平衡时若锁定重量已经大于新的目标重量，必须继续拒绝自动重平衡，交由业务反审核或人工处理。
- 数据库字段可继续保留 `numeric(18,8)`，写入值使用 3 位即可；这避免迁移风险，也保留未来扩展空间。

## 实施步骤

1. 先写失败单元测试，覆盖 3 位逐件生成和多客户拆分总量守恒。
2. 修改 `PurchaseOrderItemPieceWeightService.buildPieceWeightsForSlots(...)`，按 3 位单位生成逐件重量。
3. 保持销售分配链路只从逐件账本求和，不增加额外计算分支。
4. 跑采购订单和销售订单相关测试。
5. 用只读 SQL 校验测试库或生产候选数据。
6. 如需修历史数据，另起数据修复计划，不和本次代码改动混在一起。

## 验证命令

```bash
cd /home/instance/Gemini/leo
mvn -Dtest=PurchaseOrderItemPieceWeightServiceTest test
mvn -Dtest=SalesOrderPurchaseAllocationServiceTest test
```

必要时再跑更大范围：

```bash
cd /home/instance/Gemini/leo
mvn test
```

## 参考资料

- Java `BigDecimal` 官方文档：`BigDecimal` 是任意精度十进制数，提供 scale 操作、`movePointRight`、`toBigIntegerExact` 等精确转换能力。<https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html>
- PostgreSQL `numeric` 官方文档：`numeric` 用于需要精确性的数值，支持声明 precision 和 scale。<https://www.postgresql.org/docs/current/datatype-numeric.html>
- Jakarta Persistence `LockModeType` 官方文档：`PESSIMISTIC_WRITE` 用于让并发更新同一实体数据的事务串行化。<https://jakarta.ee/specifications/persistence/4.0/apidocs/jakarta.persistence/jakarta/persistence/lockmodetype>
- Spring Data JPA Locking 官方文档：Repository 查询方法可使用 `@Lock` 指定锁模式。<https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html>
