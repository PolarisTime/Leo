package platform

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type WarehouseService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type warehouseRecord struct {
	ID            int64
	WarehouseCode string
	WarehouseName string
	WarehouseType string
	ContactName   sql.NullString
	ContactPhone  sql.NullString
	Address       sql.NullString
	Status        string
	Remark        sql.NullString
	CreatedBy     int64
}

type warehouseReferenceCheck struct {
	table          string
	column         string
	value          string
	activeOnly     bool
	extraCondition string
}

func NewWarehouseService(db *pgxpool.Pool, machineID int64) WarehouseService {
	return WarehouseService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s WarehouseService) Options(ctx context.Context) ([]OptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT warehouse_name
		  FROM md_warehouse
		 WHERE deleted_flag = false
		 ORDER BY warehouse_name ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []OptionResponse{}
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			return nil, err
		}
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		result = append(result, OptionResponse{Label: name, Value: name})
	}
	return result, rows.Err()
}

func (s WarehouseService) Page(ctx context.Context, query PageQuery, keyword string, warehouseType string, status string) (PageResponse[WarehouseResponse], error) {
	if s.db == nil {
		return PageResponse[WarehouseResponse]{}, errors.New("database client is not configured")
	}
	where, args := warehouseFilters(keyword, warehouseType, status)
	where, args = applyDataScopeFilter(ctx, where, args, "created_by")
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM md_warehouse WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[WarehouseResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"id":            "id",
		"warehouseCode": "warehouse_code",
		"warehouseName": "warehouse_name",
		"warehouseType": "warehouse_type",
		"contactName":   "contact_name",
		"contactPhone":  "contact_phone",
		"address":       "address",
		"status":        "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, warehouse_code, warehouse_name, warehouse_type,
		       contact_name, contact_phone, address, status, remark, created_by
		  FROM md_warehouse
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[WarehouseResponse]{}, err
	}
	defer rows.Close()
	content := []WarehouseResponse{}
	for rows.Next() {
		record, err := scanWarehouse(rows)
		if err != nil {
			return PageResponse[WarehouseResponse]{}, err
		}
		content = append(content, warehouseResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[WarehouseResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s WarehouseService) Detail(ctx context.Context, id int64) (WarehouseResponse, error) {
	if s.db == nil {
		return WarehouseResponse{}, errors.New("database client is not configured")
	}
	record, err := s.detail(ctx, id)
	if err != nil {
		return WarehouseResponse{}, err
	}
	return warehouseResponse(record), nil
}

func (s WarehouseService) Create(ctx context.Context, request WarehouseRequest) (WarehouseResponse, error) {
	if s.db == nil {
		return WarehouseResponse{}, errors.New("database client is not configured")
	}
	if err := validateWarehouseRequest(request); err != nil {
		return WarehouseResponse{}, err
	}
	if err := s.ensureWarehouseCodeAvailable(ctx, request.WarehouseCode, 0); err != nil {
		return WarehouseResponse{}, err
	}
	id := s.idGenerator.Next()
	createdBy := auditUserID(ctx)
	_, err := s.db.Exec(ctx, `
		INSERT INTO md_warehouse (
			id, warehouse_code, warehouse_name, warehouse_type, contact_name,
			contact_phone, address, status, remark, created_by, created_name,
			updated_by, updated_name, updated_at, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 'system', $10, 'system', CURRENT_TIMESTAMP, false)
	`, id, request.WarehouseCode, request.WarehouseName, request.WarehouseType,
		nullableTextParam(request.ContactName), nullableTextParam(request.ContactPhone),
		nullableTextParam(request.Address), request.Status, nullableTextParam(request.Remark), createdBy)
	if err != nil {
		if isUniqueViolation(err) {
			return WarehouseResponse{}, NewAuthError(AuthErrorBusiness, "仓库编码已存在")
		}
		return WarehouseResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s WarehouseService) Update(ctx context.Context, id int64, request WarehouseRequest) (WarehouseResponse, error) {
	if s.db == nil {
		return WarehouseResponse{}, errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return WarehouseResponse{}, err
	}
	if err := validateWarehouseRequest(request); err != nil {
		return WarehouseResponse{}, err
	}
	if current.WarehouseCode != request.WarehouseCode {
		if err := s.ensureWarehouseCodeAvailable(ctx, request.WarehouseCode, id); err != nil {
			return WarehouseResponse{}, err
		}
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_warehouse
		   SET warehouse_code = $2,
		       warehouse_name = $3,
		       warehouse_type = $4,
		       contact_name = $5,
		       contact_phone = $6,
		       address = $7,
		       status = $8,
		       remark = $9,
		       updated_by = $10,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, request.WarehouseCode, request.WarehouseName, request.WarehouseType,
		nullableTextParam(request.ContactName), nullableTextParam(request.ContactPhone),
		nullableTextParam(request.Address), request.Status, nullableTextParam(request.Remark), auditUserID(ctx))
	if err != nil {
		if isUniqueViolation(err) {
			return WarehouseResponse{}, NewAuthError(AuthErrorBusiness, "仓库编码已存在")
		}
		return WarehouseResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return WarehouseResponse{}, NewAuthError(AuthErrorNotFound, "仓库不存在")
	}
	return s.Detail(ctx, id)
}

func (s WarehouseService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return err
	}
	if err := s.assertWarehouseUnused(ctx, current); err != nil {
		return err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_warehouse
		   SET deleted_flag = true,
		       updated_by = $2,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, auditUserID(ctx))
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "仓库不存在")
	}
	return nil
}

func (s WarehouseService) detail(ctx context.Context, id int64) (warehouseRecord, error) {
	var record warehouseRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, warehouse_code, warehouse_name, warehouse_type,
		       contact_name, contact_phone, address, status, remark, created_by
		  FROM md_warehouse
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&record.ID,
		&record.WarehouseCode,
		&record.WarehouseName,
		&record.WarehouseType,
		&record.ContactName,
		&record.ContactPhone,
		&record.Address,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return warehouseRecord{}, NewAuthError(AuthErrorNotFound, "仓库不存在")
	}
	if err != nil {
		return warehouseRecord{}, err
	}
	if err := assertDataScopeAccess(ctx, record.CreatedBy); err != nil {
		return warehouseRecord{}, err
	}
	return record, nil
}

func (s WarehouseService) ensureWarehouseCodeAvailable(ctx context.Context, warehouseCode string, excludeID int64) error {
	query := `
		SELECT EXISTS (
			SELECT 1
			  FROM md_warehouse
			 WHERE warehouse_code = $1
			   AND deleted_flag = false
	`
	args := []any{warehouseCode}
	if excludeID > 0 {
		query += " AND id <> $2"
		args = append(args, excludeID)
	}
	query += ")"
	var exists bool
	if err := s.db.QueryRow(ctx, query, args...).Scan(&exists); err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "仓库编码已存在")
	}
	return nil
}

func (s WarehouseService) assertWarehouseUnused(ctx context.Context, record warehouseRecord) error {
	for _, reference := range warehouseReferences(record) {
		if strings.TrimSpace(reference.value) == "" {
			continue
		}
		query := fmt.Sprintf("SELECT count(*) FROM %s WHERE %s = $1", reference.table, reference.column)
		if reference.activeOnly {
			query += " AND deleted_flag = false"
		}
		if strings.TrimSpace(reference.extraCondition) != "" {
			query += " AND " + reference.extraCondition
		}
		var count int64
		if err := s.db.QueryRow(ctx, query, reference.value).Scan(&count); err != nil {
			if isUndefinedTable(err) || isUndefinedColumn(err) {
				continue
			}
			return err
		}
		if count > 0 {
			return NewAuthError(
				AuthErrorBusiness,
				fmt.Sprintf("该仓库已被业务或主数据引用，不能删除（%s.%s 中有 %d 条记录）", reference.table, reference.column, count),
			)
		}
	}
	return nil
}

func warehouseReferences(record warehouseRecord) []warehouseReferenceCheck {
	return []warehouseReferenceCheck{
		activeWarehouseReference("po_purchase_inbound", "warehouse_name", record.WarehouseName),
		{table: "po_purchase_inbound_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM po_purchase_inbound parent WHERE parent.id = po_purchase_inbound_item.inbound_id AND parent.deleted_flag = false)"},
		{table: "po_purchase_order_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM po_purchase_order parent WHERE parent.id = po_purchase_order_item.order_id AND parent.deleted_flag = false)"},
		{table: "so_sales_order_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM so_sales_order parent WHERE parent.id = so_sales_order_item.order_id AND parent.deleted_flag = false)"},
		activeWarehouseReference("so_sales_outbound", "warehouse_name", record.WarehouseName),
		{table: "so_sales_outbound_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM so_sales_outbound parent WHERE parent.id = so_sales_outbound_item.outbound_id AND parent.deleted_flag = false)"},
		{table: "lg_freight_bill_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM lg_freight_bill parent WHERE parent.id = lg_freight_bill_item.bill_id AND parent.deleted_flag = false)"},
		{table: "st_freight_statement_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM st_freight_statement parent WHERE parent.id = st_freight_statement_item.statement_id AND parent.deleted_flag = false)"},
		{table: "fm_invoice_receipt_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM fm_invoice_receipt parent WHERE parent.id = fm_invoice_receipt_item.receipt_id AND parent.deleted_flag = false)"},
		{table: "fm_invoice_issue_item", column: "warehouse_name", value: record.WarehouseName, extraCondition: "EXISTS (SELECT 1 FROM fm_invoice_issue parent WHERE parent.id = fm_invoice_issue_item.issue_id AND parent.deleted_flag = false)"},
	}
}

func activeWarehouseReference(table string, column string, value string) warehouseReferenceCheck {
	return warehouseReferenceCheck{table: table, column: column, value: value, activeOnly: true}
}

func warehouseFilters(keyword string, warehouseType string, status string) (string, []any) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(keyword)+"%")
		where += " AND (warehouse_code LIKE $" + strconvArg(len(args)) +
			" OR warehouse_name LIKE $" + strconvArg(len(args)) +
			" OR contact_name LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(warehouseType) != "" {
		args = append(args, strings.TrimSpace(warehouseType))
		where += " AND warehouse_type = $" + strconvArg(len(args))
	}
	if strings.TrimSpace(status) != "" {
		args = append(args, strings.TrimSpace(status))
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args
}

func validateWarehouseRequest(request WarehouseRequest) error {
	if strings.TrimSpace(request.WarehouseCode) == "" {
		return NewAuthError(AuthErrorValidation, "仓库编码不能为空")
	}
	if strings.TrimSpace(request.WarehouseName) == "" {
		return NewAuthError(AuthErrorValidation, "仓库名称不能为空")
	}
	if strings.TrimSpace(request.WarehouseType) == "" {
		return NewAuthError(AuthErrorValidation, "仓库类型不能为空")
	}
	if strings.TrimSpace(request.Status) == "" {
		return NewAuthError(AuthErrorValidation, "状态不能为空")
	}
	return nil
}

func warehouseResponse(record warehouseRecord) WarehouseResponse {
	return WarehouseResponse{
		ID:            record.ID,
		WarehouseCode: record.WarehouseCode,
		WarehouseName: record.WarehouseName,
		WarehouseType: record.WarehouseType,
		ContactName:   nullableStringPointer(record.ContactName),
		ContactPhone:  nullableStringPointer(record.ContactPhone),
		Address:       nullableStringPointer(record.Address),
		Status:        record.Status,
		Remark:        nullableStringPointer(record.Remark),
	}
}

type warehouseScanner interface {
	Scan(dest ...any) error
}

func scanWarehouse(row warehouseScanner) (warehouseRecord, error) {
	var record warehouseRecord
	err := row.Scan(
		&record.ID,
		&record.WarehouseCode,
		&record.WarehouseName,
		&record.WarehouseType,
		&record.ContactName,
		&record.ContactPhone,
		&record.Address,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	return record, err
}
