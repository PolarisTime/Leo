ALTER TABLE public.md_carrier DROP CONSTRAINT md_carrier_carrier_code_key;
CREATE UNIQUE INDEX uk_md_carrier_carrier_code_active
    ON public.md_carrier (carrier_code)
    WHERE deleted_flag = false;

ALTER TABLE public.md_customer DROP CONSTRAINT md_customer_customer_code_key;
CREATE UNIQUE INDEX uk_md_customer_customer_code_active
    ON public.md_customer (customer_code)
    WHERE deleted_flag = false;

ALTER TABLE public.md_material DROP CONSTRAINT md_material_material_code_key;
CREATE UNIQUE INDEX uk_md_material_material_code_active
    ON public.md_material (material_code)
    WHERE deleted_flag = false;

ALTER TABLE public.md_project DROP CONSTRAINT md_project_project_code_key;
CREATE UNIQUE INDEX uk_md_project_project_code_active
    ON public.md_project (project_code)
    WHERE deleted_flag = false;

ALTER TABLE public.md_supplier DROP CONSTRAINT md_supplier_supplier_code_key;
CREATE UNIQUE INDEX uk_md_supplier_supplier_code_active
    ON public.md_supplier (supplier_code)
    WHERE deleted_flag = false;

ALTER TABLE public.md_warehouse DROP CONSTRAINT md_warehouse_warehouse_code_key;
CREATE UNIQUE INDEX uk_md_warehouse_warehouse_code_active
    ON public.md_warehouse (warehouse_code)
    WHERE deleted_flag = false;
