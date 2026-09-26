package com.leo.erp.master.material.domain;

/**
 * 商品类型与其对应的固定类别取值(后端协议值, 与前端 store 一致)。
 */
public final class MaterialTypes {

    private MaterialTypes() {
    }

    /** 实体商品类型。 */
    public static final String PHYSICAL = "实体商品";
    /** 附加费用类型; 其类别固定为 {@link #EXPENSE_CATEGORY}。 */
    public static final String EXPENSE = "附加费用";
    /** 附加费用对应的固定类别。 */
    public static final String EXPENSE_CATEGORY = "附加费用";

    public static boolean isExpense(String materialType) {
        return EXPENSE.equals(materialType);
    }
}
