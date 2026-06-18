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

type SupplierService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type supplierRecord struct {
	ID           int64
	SupplierCode string
	SupplierName string
	ContactName  sql.NullString
	ContactPhone sql.NullString
	City         sql.NullString
	Status       string
	Remark       sql.NullString
	CreatedBy    int64
}

type supplierReferenceCheck struct {
	table          string
	column         string
	value          string
	extraCondition string
	extraArgs      []any
}

func NewSupplierService(db *pgxpool.Pool, machineID int64) SupplierService {
	return SupplierService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s SupplierService) Options(ctx context.Context) ([]SupplierOptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, supplier_name
		  FROM md_supplier
		 WHERE deleted_flag = false
		   AND status = '正常'
		 ORDER BY supplier_code ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := []SupplierOptionResponse{}
	for rows.Next() {
		var id int64
		var supplierName string
		if err := rows.Scan(&id, &supplierName); err != nil {
			return nil, err
		}
		result = append(result, SupplierOptionResponse{
			ID:    id,
			Label: supplierName,
			Value: supplierName,
		})
	}
	return result, rows.Err()
}

func (s SupplierService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[SupplierResponse], error) {
	if s.db == nil {
		return PageResponse[SupplierResponse]{}, errors.New("database client is not configured")
	}
	where, args := supplierFilters(keyword, status)
	where, args = applyDataScopeFilter(ctx, where, args, "created_by")
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM md_supplier WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[SupplierResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"id":           "id",
		"supplierCode": "supplier_code",
		"supplierName": "supplier_name",
		"contactName":  "contact_name",
		"contactPhone": "contact_phone",
		"city":         "city",
		"status":       "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, supplier_code, supplier_name, contact_name,
		       contact_phone, city, status, remark, created_by
		  FROM md_supplier
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[SupplierResponse]{}, err
	}
	defer rows.Close()

	content := []SupplierResponse{}
	for rows.Next() {
		record, err := scanSupplier(rows)
		if err != nil {
			return PageResponse[SupplierResponse]{}, err
		}
		content = append(content, supplierResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[SupplierResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s SupplierService) Detail(ctx context.Context, id int64) (SupplierResponse, error) {
	if s.db == nil {
		return SupplierResponse{}, errors.New("database client is not configured")
	}
	record, err := s.detail(ctx, id)
	if err != nil {
		return SupplierResponse{}, err
	}
	return supplierResponse(record), nil
}

func (s SupplierService) Create(ctx context.Context, request SupplierRequest) (SupplierResponse, error) {
	if s.db == nil {
		return SupplierResponse{}, errors.New("database client is not configured")
	}
	if err := validateSupplierRequest(request); err != nil {
		return SupplierResponse{}, err
	}
	if err := s.ensureSupplierCodeAvailable(ctx, request.SupplierCode, 0); err != nil {
		return SupplierResponse{}, err
	}
	id := s.idGenerator.Next()
	createdBy := auditUserID(ctx)
	_, err := s.db.Exec(ctx, `
		INSERT INTO md_supplier (
			id, supplier_code, supplier_name, contact_name, contact_phone,
			city, status, remark, created_by, created_name,
			updated_by, updated_name, updated_at, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 'system', $9, 'system', CURRENT_TIMESTAMP, false)
	`, id, request.SupplierCode, request.SupplierName, nullableTextParam(request.ContactName),
		nullableTextParam(request.ContactPhone), nullableTextParam(request.City),
		request.Status, nullableTextParam(request.Remark), createdBy)
	if err != nil {
		if isUniqueViolation(err) {
			return SupplierResponse{}, NewAuthError(AuthErrorBusiness, "供应商编码已存在")
		}
		return SupplierResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s SupplierService) Update(ctx context.Context, id int64, request SupplierRequest) (SupplierResponse, error) {
	if s.db == nil {
		return SupplierResponse{}, errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return SupplierResponse{}, err
	}
	if err := validateSupplierRequest(request); err != nil {
		return SupplierResponse{}, err
	}
	if current.SupplierCode != request.SupplierCode {
		if err := s.ensureSupplierCodeAvailable(ctx, request.SupplierCode, id); err != nil {
			return SupplierResponse{}, err
		}
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_supplier
		   SET supplier_code = $2,
		       supplier_name = $3,
		       contact_name = $4,
		       contact_phone = $5,
		       city = $6,
		       status = $7,
		       remark = $8,
		       updated_by = $9,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, request.SupplierCode, request.SupplierName, nullableTextParam(request.ContactName),
		nullableTextParam(request.ContactPhone), nullableTextParam(request.City),
		request.Status, nullableTextParam(request.Remark), auditUserID(ctx))
	if err != nil {
		if isUniqueViolation(err) {
			return SupplierResponse{}, NewAuthError(AuthErrorBusiness, "供应商编码已存在")
		}
		return SupplierResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return SupplierResponse{}, NewAuthError(AuthErrorNotFound, "供应商不存在")
	}
	return s.Detail(ctx, id)
}

func (s SupplierService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return err
	}
	if err := s.assertSupplierUnused(ctx, current); err != nil {
		return err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_supplier
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
		return NewAuthError(AuthErrorNotFound, "供应商不存在")
	}
	return nil
}

func (s SupplierService) detail(ctx context.Context, id int64) (supplierRecord, error) {
	var record supplierRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, supplier_code, supplier_name, contact_name,
		       contact_phone, city, status, remark, created_by
		  FROM md_supplier
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&record.ID,
		&record.SupplierCode,
		&record.SupplierName,
		&record.ContactName,
		&record.ContactPhone,
		&record.City,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return supplierRecord{}, NewAuthError(AuthErrorNotFound, "供应商不存在")
	}
	if err != nil {
		return supplierRecord{}, err
	}
	if err := assertDataScopeAccess(ctx, record.CreatedBy); err != nil {
		return supplierRecord{}, err
	}
	return record, nil
}

func (s SupplierService) ensureSupplierCodeAvailable(ctx context.Context, supplierCode string, excludeID int64) error {
	query := `
		SELECT EXISTS (
			SELECT 1
			  FROM md_supplier
			 WHERE supplier_code = $1
			   AND deleted_flag = false
	`
	args := []any{supplierCode}
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
		return NewAuthError(AuthErrorBusiness, "供应商编码已存在")
	}
	return nil
}

func (s SupplierService) assertSupplierUnused(ctx context.Context, record supplierRecord) error {
	for _, reference := range supplierReferences(record) {
		if strings.TrimSpace(reference.value) == "" {
			continue
		}
		query := fmt.Sprintf(
			"SELECT count(*) FROM %s WHERE deleted_flag = false AND %s = $1",
			reference.table,
			reference.column,
		)
		args := []any{reference.value}
		if strings.TrimSpace(reference.extraCondition) != "" {
			query += " AND " + reference.extraCondition
			args = append(args, reference.extraArgs...)
		}
		var count int64
		if err := s.db.QueryRow(ctx, query, args...).Scan(&count); err != nil {
			if isUndefinedTable(err) || isUndefinedColumn(err) {
				continue
			}
			return err
		}
		if count > 0 {
			return NewAuthError(
				AuthErrorBusiness,
				fmt.Sprintf("该供应商已被业务或主数据引用，不能删除（%s.%s 中有 %d 条记录）", reference.table, reference.column, count),
			)
		}
	}
	return nil
}

func supplierReferences(record supplierRecord) []supplierReferenceCheck {
	return []supplierReferenceCheck{
		{table: "st_supplier_statement", column: "supplier_code", value: record.SupplierCode},
		{table: "fm_payment", column: "counterparty_code", value: record.SupplierCode, extraCondition: "business_type IN ($2, $3)", extraArgs: []any{"供应商", "供应商付款"}},
		{table: "fm_ledger_adjustment", column: "counterparty_code", value: record.SupplierCode, extraCondition: "counterparty_type = $2", extraArgs: []any{"供应商"}},
		{table: "po_purchase_order", column: "supplier_name", value: record.SupplierName},
		{table: "po_purchase_inbound", column: "supplier_name", value: record.SupplierName},
		{table: "ct_purchase_contract", column: "supplier_name", value: record.SupplierName},
		{table: "st_supplier_statement", column: "supplier_name", value: record.SupplierName, extraCondition: "(supplier_code IS NULL OR BTRIM(supplier_code) = '')"},
		{table: "fm_invoice_receipt", column: "supplier_name", value: record.SupplierName},
		{table: "fm_payment", column: "counterparty_name", value: record.SupplierName, extraCondition: "business_type IN ($2, $3) AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')", extraArgs: []any{"供应商", "供应商付款"}},
		{table: "fm_ledger_adjustment", column: "counterparty_name", value: record.SupplierName, extraCondition: "counterparty_type = $2 AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')", extraArgs: []any{"供应商"}},
	}
}

func supplierFilters(keyword string, status string) (string, []any) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(keyword)+"%")
		where += " AND (supplier_code LIKE $" + strconvArg(len(args)) + " OR supplier_name LIKE $" + strconvArg(len(args)) + " OR contact_name LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		args = append(args, strings.TrimSpace(status))
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args
}

func validateSupplierRequest(request SupplierRequest) error {
	if strings.TrimSpace(request.SupplierCode) == "" {
		return NewAuthError(AuthErrorValidation, "供应商编码不能为空")
	}
	if strings.TrimSpace(request.SupplierName) == "" {
		return NewAuthError(AuthErrorValidation, "供应商名称不能为空")
	}
	if strings.TrimSpace(request.Status) == "" {
		return NewAuthError(AuthErrorValidation, "状态不能为空")
	}
	return nil
}

func nullableTextParam(value *string) sql.NullString {
	if value == nil {
		return sql.NullString{}
	}
	return sql.NullString{String: *value, Valid: true}
}

func supplierResponse(record supplierRecord) SupplierResponse {
	return SupplierResponse{
		ID:           record.ID,
		SupplierCode: record.SupplierCode,
		SupplierName: record.SupplierName,
		ContactName:  nullableStringPointer(record.ContactName),
		ContactPhone: nullableStringPointer(record.ContactPhone),
		City:         nullableStringPointer(record.City),
		Status:       record.Status,
		Remark:       nullableStringPointer(record.Remark),
	}
}

type supplierScanner interface {
	Scan(dest ...any) error
}

func scanSupplier(row supplierScanner) (supplierRecord, error) {
	var record supplierRecord
	err := row.Scan(
		&record.ID,
		&record.SupplierCode,
		&record.SupplierName,
		&record.ContactName,
		&record.ContactPhone,
		&record.City,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	return record, err
}
