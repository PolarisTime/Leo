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

type CustomerService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type customerRecord struct {
	ID              int64
	CustomerCode    string
	CustomerName    string
	ContactName     sql.NullString
	ContactPhone    sql.NullString
	City            sql.NullString
	SettlementMode  sql.NullString
	ProjectName     string
	ProjectNameAbbr sql.NullString
	ProjectAddress  sql.NullString
	Status          string
	Remark          sql.NullString
	CreatedBy       int64
}

type customerReferenceCheck struct {
	table          string
	column         string
	value          string
	activeOnly     bool
	extraCondition string
	extraArgs      []any
}

func NewCustomerService(db *pgxpool.Pool, machineID int64) CustomerService {
	return CustomerService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s CustomerService) Options(ctx context.Context) ([]CustomerOptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, customer_code, customer_name, project_name, project_name_abbr
		  FROM md_customer
		 WHERE deleted_flag = false
		   AND status = '正常'
		 ORDER BY customer_code ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := []CustomerOptionResponse{}
	for rows.Next() {
		var record customerRecord
		if err := rows.Scan(
			&record.ID,
			&record.CustomerCode,
			&record.CustomerName,
			&record.ProjectName,
			&record.ProjectNameAbbr,
		); err != nil {
			return nil, err
		}
		result = append(result, customerOptionResponse(record))
	}
	return result, rows.Err()
}

func (s CustomerService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[CustomerResponse], error) {
	if s.db == nil {
		return PageResponse[CustomerResponse]{}, errors.New("database client is not configured")
	}
	where, args := customerFilters(keyword, status)
	where, args = applyDataScopeFilter(ctx, where, args, "created_by")
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM md_customer WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[CustomerResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"id":             "id",
		"customerCode":   "customer_code",
		"customerName":   "customer_name",
		"contactName":    "contact_name",
		"contactPhone":   "contact_phone",
		"city":           "city",
		"settlementMode": "settlement_mode",
		"projectName":    "project_name",
		"status":         "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, customer_code, customer_name, contact_name,
		       contact_phone, city, settlement_mode, project_name,
		       project_name_abbr, project_address, status, remark, created_by
		  FROM md_customer
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[CustomerResponse]{}, err
	}
	defer rows.Close()

	content := []CustomerResponse{}
	for rows.Next() {
		record, err := scanCustomer(rows)
		if err != nil {
			return PageResponse[CustomerResponse]{}, err
		}
		content = append(content, customerResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[CustomerResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s CustomerService) Detail(ctx context.Context, id int64) (CustomerResponse, error) {
	if s.db == nil {
		return CustomerResponse{}, errors.New("database client is not configured")
	}
	record, err := s.detail(ctx, id)
	if err != nil {
		return CustomerResponse{}, err
	}
	return customerResponse(record), nil
}

func (s CustomerService) Create(ctx context.Context, request CustomerRequest) (CustomerResponse, error) {
	if s.db == nil {
		return CustomerResponse{}, errors.New("database client is not configured")
	}
	if err := validateCustomerRequest(request); err != nil {
		return CustomerResponse{}, err
	}
	if err := s.ensureCustomerCodeAvailable(ctx, request.CustomerCode, 0); err != nil {
		return CustomerResponse{}, err
	}
	id := s.idGenerator.Next()
	createdBy := auditUserID(ctx)
	_, err := s.db.Exec(ctx, `
		INSERT INTO md_customer (
			id, customer_code, customer_name, contact_name, contact_phone,
			city, settlement_mode, project_name, project_name_abbr, project_address,
			status, remark, created_by, created_name,
			updated_by, updated_name, updated_at, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, 'system', $13, 'system', CURRENT_TIMESTAMP, false)
	`, id, request.CustomerCode, request.CustomerName, nullableTextParam(request.ContactName),
		nullableTextParam(request.ContactPhone), nullableTextParam(request.City),
		nullableTextParam(request.SettlementMode), request.ProjectName,
		nullableTextParam(request.ProjectNameAbbr), nullableTextParam(request.ProjectAddress),
		request.Status, nullableTextParam(request.Remark), createdBy)
	if err != nil {
		if isUniqueViolation(err) {
			return CustomerResponse{}, NewAuthError(AuthErrorBusiness, "客户编码已存在")
		}
		return CustomerResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s CustomerService) Update(ctx context.Context, id int64, request CustomerRequest) (CustomerResponse, error) {
	if s.db == nil {
		return CustomerResponse{}, errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return CustomerResponse{}, err
	}
	if err := validateCustomerRequest(request); err != nil {
		return CustomerResponse{}, err
	}
	if current.CustomerCode != request.CustomerCode {
		if err := s.ensureCustomerCodeAvailable(ctx, request.CustomerCode, id); err != nil {
			return CustomerResponse{}, err
		}
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_customer
		   SET customer_code = $2,
		       customer_name = $3,
		       contact_name = $4,
		       contact_phone = $5,
		       city = $6,
		       settlement_mode = $7,
		       project_name = $8,
		       project_name_abbr = $9,
		       project_address = $10,
		       status = $11,
		       remark = $12,
		       updated_by = $13,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, request.CustomerCode, request.CustomerName, nullableTextParam(request.ContactName),
		nullableTextParam(request.ContactPhone), nullableTextParam(request.City),
		nullableTextParam(request.SettlementMode), request.ProjectName,
		nullableTextParam(request.ProjectNameAbbr), nullableTextParam(request.ProjectAddress),
		request.Status, nullableTextParam(request.Remark), auditUserID(ctx))
	if err != nil {
		if isUniqueViolation(err) {
			return CustomerResponse{}, NewAuthError(AuthErrorBusiness, "客户编码已存在")
		}
		return CustomerResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return CustomerResponse{}, NewAuthError(AuthErrorNotFound, "客户不存在")
	}
	return s.Detail(ctx, id)
}

func (s CustomerService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return err
	}
	if err := s.assertCustomerUnused(ctx, current); err != nil {
		return err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_customer
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
		return NewAuthError(AuthErrorNotFound, "客户不存在")
	}
	return nil
}

func (s CustomerService) detail(ctx context.Context, id int64) (customerRecord, error) {
	var record customerRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, customer_code, customer_name, contact_name,
		       contact_phone, city, settlement_mode, project_name,
		       project_name_abbr, project_address, status, remark, created_by
		  FROM md_customer
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&record.ID,
		&record.CustomerCode,
		&record.CustomerName,
		&record.ContactName,
		&record.ContactPhone,
		&record.City,
		&record.SettlementMode,
		&record.ProjectName,
		&record.ProjectNameAbbr,
		&record.ProjectAddress,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return customerRecord{}, NewAuthError(AuthErrorNotFound, "客户不存在")
	}
	if err != nil {
		return customerRecord{}, err
	}
	if err := assertDataScopeAccess(ctx, record.CreatedBy); err != nil {
		return customerRecord{}, err
	}
	return record, nil
}

func (s CustomerService) ensureCustomerCodeAvailable(ctx context.Context, customerCode string, excludeID int64) error {
	query := `
		SELECT EXISTS (
			SELECT 1
			  FROM md_customer
			 WHERE customer_code = $1
			   AND deleted_flag = false
	`
	args := []any{customerCode}
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
		return NewAuthError(AuthErrorBusiness, "客户编码已存在")
	}
	return nil
}

func (s CustomerService) assertCustomerUnused(ctx context.Context, record customerRecord) error {
	for _, reference := range customerReferences(record) {
		if strings.TrimSpace(reference.value) == "" {
			continue
		}
		query := fmt.Sprintf(
			"SELECT count(*) FROM %s WHERE %s = $1",
			reference.table,
			reference.column,
		)
		args := []any{reference.value}
		if reference.activeOnly {
			query += " AND deleted_flag = false"
		}
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
				fmt.Sprintf("该客户已被业务或主数据引用，不能删除（%s.%s 中有 %d 条记录）", reference.table, reference.column, count),
			)
		}
	}
	return nil
}

func customerReferences(record customerRecord) []customerReferenceCheck {
	return []customerReferenceCheck{
		activeCustomerReference("md_project", "customer_code", record.CustomerCode),
		activeCustomerReference("so_sales_order", "customer_code", record.CustomerCode),
		activeCustomerReference("fm_receipt", "customer_code", record.CustomerCode),
		activeCustomerReference("st_customer_statement", "customer_code", record.CustomerCode),
		{table: "st_customer_statement_item", column: "customer_code", value: record.CustomerCode, extraCondition: "EXISTS (SELECT 1 FROM st_customer_statement parent WHERE parent.id = st_customer_statement_item.statement_id AND parent.deleted_flag = false)"},
		activeCustomerReferenceWith("fm_ledger_adjustment", "counterparty_code", record.CustomerCode, "counterparty_type = $2", "客户"),
		activeCustomerReferenceWith("so_sales_order", "customer_name", record.CustomerName, "(customer_code IS NULL OR BTRIM(customer_code) = '')"),
		activeCustomerReference("so_sales_outbound", "customer_name", record.CustomerName),
		activeCustomerReference("lg_freight_bill", "customer_name", record.CustomerName),
		{table: "lg_freight_bill_item", column: "customer_name", value: record.CustomerName, extraCondition: "EXISTS (SELECT 1 FROM lg_freight_bill parent WHERE parent.id = lg_freight_bill_item.bill_id AND parent.deleted_flag = false)"},
		activeCustomerReference("ct_sales_contract", "customer_name", record.CustomerName),
		activeCustomerReferenceWith("st_customer_statement", "customer_name", record.CustomerName, "(customer_code IS NULL OR BTRIM(customer_code) = '')"),
		{table: "st_freight_statement_item", column: "customer_name", value: record.CustomerName, extraCondition: "EXISTS (SELECT 1 FROM st_freight_statement parent WHERE parent.id = st_freight_statement_item.statement_id AND parent.deleted_flag = false)"},
		activeCustomerReferenceWith("fm_receipt", "customer_name", record.CustomerName, "(customer_code IS NULL OR BTRIM(customer_code) = '')"),
		activeCustomerReference("fm_invoice_issue", "customer_name", record.CustomerName),
		activeCustomerReferenceWith("fm_ledger_adjustment", "counterparty_name", record.CustomerName, "counterparty_type = $2 AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')", "客户"),
	}
}

func activeCustomerReference(table string, column string, value string) customerReferenceCheck {
	return customerReferenceCheck{table: table, column: column, value: value, activeOnly: true}
}

func activeCustomerReferenceWith(table string, column string, value string, extraCondition string, extraArgs ...any) customerReferenceCheck {
	return customerReferenceCheck{
		table:          table,
		column:         column,
		value:          value,
		activeOnly:     true,
		extraCondition: extraCondition,
		extraArgs:      extraArgs,
	}
}

func customerFilters(keyword string, status string) (string, []any) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(keyword)+"%")
		where += " AND (customer_code LIKE $" + strconvArg(len(args)) +
			" OR customer_name LIKE $" + strconvArg(len(args)) +
			" OR project_name LIKE $" + strconvArg(len(args)) +
			" OR contact_name LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		args = append(args, strings.TrimSpace(status))
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args
}

func validateCustomerRequest(request CustomerRequest) error {
	if strings.TrimSpace(request.CustomerCode) == "" {
		return NewAuthError(AuthErrorValidation, "客户编码不能为空")
	}
	if strings.TrimSpace(request.CustomerName) == "" {
		return NewAuthError(AuthErrorValidation, "客户名称不能为空")
	}
	if strings.TrimSpace(request.ProjectName) == "" {
		return NewAuthError(AuthErrorValidation, "项目名称不能为空")
	}
	if strings.TrimSpace(request.Status) == "" {
		return NewAuthError(AuthErrorValidation, "状态不能为空")
	}
	return nil
}

func customerResponse(record customerRecord) CustomerResponse {
	return CustomerResponse{
		ID:              record.ID,
		CustomerCode:    record.CustomerCode,
		CustomerName:    record.CustomerName,
		ContactName:     nullableStringPointer(record.ContactName),
		ContactPhone:    nullableStringPointer(record.ContactPhone),
		City:            nullableStringPointer(record.City),
		SettlementMode:  nullableStringPointer(record.SettlementMode),
		ProjectName:     record.ProjectName,
		ProjectNameAbbr: nullableStringPointer(record.ProjectNameAbbr),
		ProjectAddress:  nullableStringPointer(record.ProjectAddress),
		Status:          record.Status,
		Remark:          nullableStringPointer(record.Remark),
	}
}

func customerOptionResponse(record customerRecord) CustomerOptionResponse {
	return CustomerOptionResponse{
		ID:              record.ID,
		Label:           customerOptionLabel(record),
		Value:           record.CustomerName,
		CustomerCode:    record.CustomerCode,
		CustomerName:    record.CustomerName,
		ProjectName:     record.ProjectName,
		ProjectNameAbbr: nullableStringPointer(record.ProjectNameAbbr),
	}
}

func customerOptionLabel(record customerRecord) string {
	projectName := strings.TrimSpace(record.ProjectName)
	if projectName == "" || projectName == record.CustomerName {
		return record.CustomerName
	}
	return record.CustomerName + " / " + projectName
}

type customerScanner interface {
	Scan(dest ...any) error
}

func scanCustomer(row customerScanner) (customerRecord, error) {
	var record customerRecord
	err := row.Scan(
		&record.ID,
		&record.CustomerCode,
		&record.CustomerName,
		&record.ContactName,
		&record.ContactPhone,
		&record.City,
		&record.SettlementMode,
		&record.ProjectName,
		&record.ProjectNameAbbr,
		&record.ProjectAddress,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	return record, err
}
