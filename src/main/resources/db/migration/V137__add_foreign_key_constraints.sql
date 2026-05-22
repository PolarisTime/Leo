-- V137: Add FOREIGN KEY constraints for existing _id reference columns
-- All referenced tables and columns already exist; _id fields are nullable

-- sys_user.department_id → sys_department.id
ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS fk_sys_user_department;
ALTER TABLE sys_user ADD CONSTRAINT fk_sys_user_department
    FOREIGN KEY (department_id) REFERENCES sys_department (id) ON DELETE SET NULL;

-- so_sales_order.project_id → md_project.id
ALTER TABLE so_sales_order DROP CONSTRAINT IF EXISTS fk_so_sales_order_project;
ALTER TABLE so_sales_order ADD CONSTRAINT fk_so_sales_order_project
    FOREIGN KEY (project_id) REFERENCES md_project (id) ON DELETE SET NULL;

-- fm_receipt.project_id → md_project.id
ALTER TABLE fm_receipt DROP CONSTRAINT IF EXISTS fk_fm_receipt_project;
ALTER TABLE fm_receipt ADD CONSTRAINT fk_fm_receipt_project
    FOREIGN KEY (project_id) REFERENCES md_project (id) ON DELETE SET NULL;

-- st_customer_statement.project_id → md_project.id
ALTER TABLE st_customer_statement DROP CONSTRAINT IF EXISTS fk_st_customer_stmt_project;
ALTER TABLE st_customer_statement ADD CONSTRAINT fk_st_customer_stmt_project
    FOREIGN KEY (project_id) REFERENCES md_project (id) ON DELETE SET NULL;
