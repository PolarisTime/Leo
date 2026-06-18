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

type ProjectService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type projectRecord struct {
	ID              int64
	ProjectCode     string
	ProjectName     string
	ProjectNameAbbr sql.NullString
	ProjectAddress  sql.NullString
	ProjectManager  sql.NullString
	CustomerCode    string
	Status          string
	Remark          sql.NullString
	CreatedBy       int64
}

type projectReferenceCheck struct {
	table          string
	column         string
	value          any
	activeOnly     bool
	extraCondition string
	extraArgs      []any
}

func NewProjectService(db *pgxpool.Pool, machineID int64) ProjectService {
	return ProjectService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s ProjectService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[ProjectResponse], error) {
	if s.db == nil {
		return PageResponse[ProjectResponse]{}, errors.New("database client is not configured")
	}
	where, args := projectFilters(keyword, status)
	where, args = applyDataScopeFilter(ctx, where, args, "created_by")
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM md_project WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[ProjectResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"id":              "id",
		"projectCode":     "project_code",
		"projectName":     "project_name",
		"projectNameAbbr": "project_name_abbr",
		"customerCode":    "customer_code",
		"projectManager":  "project_manager",
		"status":          "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, project_code, project_name, project_name_abbr,
		       project_address, project_manager, customer_code, status,
		       remark, created_by
		  FROM md_project
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[ProjectResponse]{}, err
	}
	defer rows.Close()

	content := []ProjectResponse{}
	for rows.Next() {
		record, err := scanProject(rows)
		if err != nil {
			return PageResponse[ProjectResponse]{}, err
		}
		content = append(content, projectResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[ProjectResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s ProjectService) Detail(ctx context.Context, id int64) (ProjectResponse, error) {
	if s.db == nil {
		return ProjectResponse{}, errors.New("database client is not configured")
	}
	record, err := s.detail(ctx, id)
	if err != nil {
		return ProjectResponse{}, err
	}
	return projectResponse(record), nil
}

func (s ProjectService) Create(ctx context.Context, request ProjectRequest) (ProjectResponse, error) {
	if s.db == nil {
		return ProjectResponse{}, errors.New("database client is not configured")
	}
	if err := validateProjectRequest(request); err != nil {
		return ProjectResponse{}, err
	}
	if err := s.ensureProjectCodeAvailable(ctx, request.ProjectCode, 0); err != nil {
		return ProjectResponse{}, err
	}
	id := s.idGenerator.Next()
	createdBy := auditUserID(ctx)
	_, err := s.db.Exec(ctx, `
		INSERT INTO md_project (
			id, project_code, project_name, project_name_abbr, project_address,
			project_manager, customer_code, status, remark, created_by, created_name,
			updated_by, updated_name, updated_at, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 'system', $10, 'system', CURRENT_TIMESTAMP, false)
	`, id, request.ProjectCode, request.ProjectName, nullableTextParam(request.ProjectNameAbbr),
		nullableTextParam(request.ProjectAddress), nullableTextParam(request.ProjectManager),
		request.CustomerCode, request.Status, nullableTextParam(request.Remark), createdBy)
	if err != nil {
		if isUniqueViolation(err) {
			return ProjectResponse{}, NewAuthError(AuthErrorBusiness, "项目编码已存在")
		}
		return ProjectResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s ProjectService) Update(ctx context.Context, id int64, request ProjectRequest) (ProjectResponse, error) {
	if s.db == nil {
		return ProjectResponse{}, errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return ProjectResponse{}, err
	}
	if err := validateProjectRequest(request); err != nil {
		return ProjectResponse{}, err
	}
	if current.ProjectCode != request.ProjectCode {
		if err := s.ensureProjectCodeAvailable(ctx, request.ProjectCode, id); err != nil {
			return ProjectResponse{}, err
		}
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_project
		   SET project_code = $2,
		       project_name = $3,
		       project_name_abbr = $4,
		       project_address = $5,
		       project_manager = $6,
		       customer_code = $7,
		       status = $8,
		       remark = $9,
		       updated_by = $10,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, request.ProjectCode, request.ProjectName, nullableTextParam(request.ProjectNameAbbr),
		nullableTextParam(request.ProjectAddress), nullableTextParam(request.ProjectManager),
		request.CustomerCode, request.Status, nullableTextParam(request.Remark), auditUserID(ctx))
	if err != nil {
		if isUniqueViolation(err) {
			return ProjectResponse{}, NewAuthError(AuthErrorBusiness, "项目编码已存在")
		}
		return ProjectResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return ProjectResponse{}, NewAuthError(AuthErrorNotFound, "项目不存在")
	}
	return s.Detail(ctx, id)
}

func (s ProjectService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return err
	}
	if err := s.assertProjectUnused(ctx, current); err != nil {
		return err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_project
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
		return NewAuthError(AuthErrorNotFound, "项目不存在")
	}
	return nil
}

func (s ProjectService) detail(ctx context.Context, id int64) (projectRecord, error) {
	var record projectRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, project_code, project_name, project_name_abbr,
		       project_address, project_manager, customer_code, status,
		       remark, created_by
		  FROM md_project
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&record.ID,
		&record.ProjectCode,
		&record.ProjectName,
		&record.ProjectNameAbbr,
		&record.ProjectAddress,
		&record.ProjectManager,
		&record.CustomerCode,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return projectRecord{}, NewAuthError(AuthErrorNotFound, "项目不存在")
	}
	if err != nil {
		return projectRecord{}, err
	}
	if err := assertDataScopeAccess(ctx, record.CreatedBy); err != nil {
		return projectRecord{}, err
	}
	return record, nil
}

func (s ProjectService) ensureProjectCodeAvailable(ctx context.Context, projectCode string, excludeID int64) error {
	query := `
		SELECT EXISTS (
			SELECT 1
			  FROM md_project
			 WHERE project_code = $1
			   AND deleted_flag = false
	`
	args := []any{projectCode}
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
		return NewAuthError(AuthErrorBusiness, "项目编码已存在")
	}
	return nil
}

func (s ProjectService) assertProjectUnused(ctx context.Context, record projectRecord) error {
	for _, reference := range projectReferences(record) {
		if projectReferenceBlank(reference.value) {
			continue
		}
		query := fmt.Sprintf("SELECT count(*) FROM %s WHERE ", reference.table)
		if reference.activeOnly {
			query += "deleted_flag = false AND "
		}
		query += reference.column + " = $1"
		args := []any{reference.value}
		if strings.TrimSpace(reference.extraCondition) != "" {
			query += " AND " + reference.extraCondition
			args = append(args, reference.extraArgs...)
		}
		var count int64
		if err := s.db.QueryRow(ctx, query, args...).Scan(&count); err != nil {
			return err
		}
		if count > 0 {
			return NewAuthError(
				AuthErrorBusiness,
				fmt.Sprintf("该项目已被业务或主数据引用，不能删除（%s.%s 中有 %d 条记录）", reference.table, reference.column, count),
			)
		}
	}
	return nil
}

func projectReferences(record projectRecord) []projectReferenceCheck {
	return []projectReferenceCheck{
		activeProjectReference("so_sales_order", "project_id", record.ID),
		activeProjectReference("fm_receipt", "project_id", record.ID),
		activeProjectReference("st_customer_statement", "project_id", record.ID),
		{table: "st_customer_statement_item", column: "project_id", value: record.ID, extraCondition: "EXISTS (SELECT 1 FROM st_customer_statement parent WHERE parent.id = st_customer_statement_item.statement_id AND parent.deleted_flag = false)"},
		activeProjectReference("fm_ledger_adjustment", "project_id", record.ID),
		activeProjectReference("md_customer", "project_name", record.ProjectName),
		activeProjectReferenceWith("so_sales_order", "project_name", record.ProjectName, "project_id IS NULL"),
		activeProjectReference("so_sales_outbound", "project_name", record.ProjectName),
		activeProjectReference("lg_freight_bill", "project_name", record.ProjectName),
		{table: "lg_freight_bill_item", column: "project_name", value: record.ProjectName, extraCondition: "EXISTS (SELECT 1 FROM lg_freight_bill parent WHERE parent.id = lg_freight_bill_item.bill_id AND parent.deleted_flag = false)"},
		activeProjectReference("ct_sales_contract", "project_name", record.ProjectName),
		activeProjectReferenceWith("st_customer_statement", "project_name", record.ProjectName, "project_id IS NULL"),
		{table: "st_freight_statement_item", column: "project_name", value: record.ProjectName, extraCondition: "EXISTS (SELECT 1 FROM st_freight_statement parent WHERE parent.id = st_freight_statement_item.statement_id AND parent.deleted_flag = false)"},
		activeProjectReferenceWith("fm_receipt", "project_name", record.ProjectName, "project_id IS NULL"),
		activeProjectReference("fm_invoice_issue", "project_name", record.ProjectName),
		activeProjectReferenceWith("fm_ledger_adjustment", "project_name", record.ProjectName, "project_id IS NULL"),
	}
}

func activeProjectReference(table string, column string, value any) projectReferenceCheck {
	return projectReferenceCheck{table: table, column: column, value: value, activeOnly: true}
}

func activeProjectReferenceWith(table string, column string, value any, extraCondition string, extraArgs ...any) projectReferenceCheck {
	return projectReferenceCheck{
		table:          table,
		column:         column,
		value:          value,
		activeOnly:     true,
		extraCondition: extraCondition,
		extraArgs:      extraArgs,
	}
}

func projectReferenceBlank(value any) bool {
	if value == nil {
		return true
	}
	text, ok := value.(string)
	return ok && strings.TrimSpace(text) == ""
}

func projectFilters(keyword string, status string) (string, []any) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(keyword)+"%")
		where += " AND (project_code LIKE $" + strconvArg(len(args)) +
			" OR project_name LIKE $" + strconvArg(len(args)) +
			" OR project_name_abbr LIKE $" + strconvArg(len(args)) +
			" OR customer_code LIKE $" + strconvArg(len(args)) +
			" OR project_manager LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		args = append(args, strings.TrimSpace(status))
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args
}

func validateProjectRequest(request ProjectRequest) error {
	if strings.TrimSpace(request.ProjectCode) == "" {
		return NewAuthError(AuthErrorValidation, "项目编码不能为空")
	}
	if strings.TrimSpace(request.ProjectName) == "" {
		return NewAuthError(AuthErrorValidation, "项目名称不能为空")
	}
	if strings.TrimSpace(request.CustomerCode) == "" {
		return NewAuthError(AuthErrorValidation, "客户编码不能为空")
	}
	if strings.TrimSpace(request.Status) == "" {
		return NewAuthError(AuthErrorValidation, "状态不能为空")
	}
	return nil
}

func projectResponse(record projectRecord) ProjectResponse {
	return ProjectResponse{
		ID:              record.ID,
		ProjectCode:     record.ProjectCode,
		ProjectName:     record.ProjectName,
		ProjectNameAbbr: nullableStringPointer(record.ProjectNameAbbr),
		ProjectAddress:  nullableStringPointer(record.ProjectAddress),
		ProjectManager:  nullableStringPointer(record.ProjectManager),
		CustomerCode:    record.CustomerCode,
		Status:          record.Status,
		Remark:          nullableStringPointer(record.Remark),
	}
}

type projectScanner interface {
	Scan(dest ...any) error
}

func scanProject(row projectScanner) (projectRecord, error) {
	var record projectRecord
	err := row.Scan(
		&record.ID,
		&record.ProjectCode,
		&record.ProjectName,
		&record.ProjectNameAbbr,
		&record.ProjectAddress,
		&record.ProjectManager,
		&record.CustomerCode,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	return record, err
}
