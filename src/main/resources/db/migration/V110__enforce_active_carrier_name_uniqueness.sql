-- 运单/货运对账单表单以名称标识物流商，同名物流商会导致编码与默认结算公司静默错绑。
-- 先对存量未删除数据去重（保留 id 最小的一条，其余追加唯一编码后缀），再建 active 局部唯一索引。

UPDATE public.md_carrier c
SET carrier_name = left(c.carrier_name, 128 - char_length(c.carrier_code) - 1) || '-' || c.carrier_code,
    updated_at = CURRENT_TIMESTAMP
WHERE c.deleted_flag = false
  AND EXISTS (
      SELECT 1
      FROM public.md_carrier d
      WHERE d.deleted_flag = false
        AND d.carrier_name = c.carrier_name
        AND d.id < c.id
  );

CREATE UNIQUE INDEX uk_md_carrier_carrier_name_active
    ON public.md_carrier (carrier_name)
    WHERE deleted_flag = false;
