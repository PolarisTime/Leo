package platform

import (
	"context"
	"database/sql"
	"errors"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type DepartmentService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

func NewDepartmentService(db *pgxpool.Pool, machineID int64) DepartmentService {
	return DepartmentService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s DepartmentService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[DepartmentResponse], error) {
	if s.db == nil {
		return PageResponse[DepartmentResponse]{}, errors.New("database client is not configured")
	}
	where, args, err := departmentFilters(keyword, status)
	if err != nil {
		return PageResponse[DepartmentResponse]{}, err
	}
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM sys_department WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[DepartmentResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"departmentCode": "department_code",
		"departmentName": "department_name",
		"managerName":    "manager_name",
		"sortOrder":      "sort_order",
		"status":         "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, department_code, department_name, parent_id,
		       COALESCE(manager_name, ''), COALESCE(contact_phone, ''),
		       COALESCE(sort_order, 0), status, COALESCE(remark, '')
		  FROM sys_department
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[DepartmentResponse]{}, err
	}
	defer rows.Close()
	content := []DepartmentResponse{}
	for rows.Next() {
		row, err := scanDepartment(rows)
		if err != nil {
			return PageResponse[DepartmentResponse]{}, err
		}
		content = append(content, row)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[DepartmentResponse]{}, err
	}
	if err := s.fillDepartmentParentNames(ctx, content); err != nil {
		return PageResponse[DepartmentResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s DepartmentService) Options(ctx context.Context) ([]DepartmentOptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, department_code, department_name
		  FROM sys_department
		 WHERE deleted_flag = false
		   AND status = '正常'
		 ORDER BY sort_order ASC, id ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []DepartmentOptionResponse{}
	for rows.Next() {
		var row DepartmentOptionResponse
		if err := rows.Scan(&row.ID, &row.DepartmentCode, &row.DepartmentName); err != nil {
			return nil, err
		}
		result = append(result, row)
	}
	return result, rows.Err()
}

func (s DepartmentService) Detail(ctx context.Context, id int64) (DepartmentResponse, error) {
	if s.db == nil {
		return DepartmentResponse{}, errors.New("database client is not configured")
	}
	row, err := s.detail(ctx, id)
	if err != nil {
		return DepartmentResponse{}, err
	}
	content := []DepartmentResponse{row}
	if err := s.fillDepartmentParentNames(ctx, content); err != nil {
		return DepartmentResponse{}, err
	}
	return content[0], nil
}

func (s DepartmentService) Create(ctx context.Context, request DepartmentRequest) (DepartmentResponse, error) {
	if s.db == nil {
		return DepartmentResponse{}, errors.New("database client is not configured")
	}
	normalized, err := normalizeDepartmentRequest(request)
	if err != nil {
		return DepartmentResponse{}, err
	}
	if err := s.ensureDepartmentCodeAvailable(ctx, normalized.DepartmentCode, 0); err != nil {
		return DepartmentResponse{}, err
	}
	if err := s.validateParent(ctx, 0, normalized.ParentID); err != nil {
		return DepartmentResponse{}, err
	}
	id := s.idGenerator.Next()
	_, err = s.db.Exec(ctx, `
		INSERT INTO sys_department (
			id, department_code, department_name, parent_id, manager_name,
			contact_phone, sort_order, status, remark, created_by, created_name, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 0, 'system', false)
	`, id, normalized.DepartmentCode, normalized.DepartmentName, normalized.ParentID,
		optionalNullString(normalized.ManagerName), optionalNullString(normalized.ContactPhone),
		departmentSortOrder(normalized), normalized.Status, optionalNullString(normalized.Remark))
	if err != nil {
		if isUniqueViolation(err) {
			return DepartmentResponse{}, NewAuthError(AuthErrorBusiness, "部门编码已存在")
		}
		return DepartmentResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s DepartmentService) Update(ctx context.Context, id int64, request DepartmentRequest) (DepartmentResponse, error) {
	if s.db == nil {
		return DepartmentResponse{}, errors.New("database client is not configured")
	}
	if _, err := s.detail(ctx, id); err != nil {
		return DepartmentResponse{}, err
	}
	normalized, err := normalizeDepartmentRequest(request)
	if err != nil {
		return DepartmentResponse{}, err
	}
	if err := s.ensureDepartmentCodeAvailable(ctx, normalized.DepartmentCode, id); err != nil {
		return DepartmentResponse{}, err
	}
	if err := s.validateParent(ctx, id, normalized.ParentID); err != nil {
		return DepartmentResponse{}, err
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return DepartmentResponse{}, err
	}
	defer tx.Rollback(ctx)
	commandTag, err := tx.Exec(ctx, `
		UPDATE sys_department
		   SET department_code = $2,
		       department_name = $3,
		       parent_id = $4,
		       manager_name = $5,
		       contact_phone = $6,
		       sort_order = $7,
		       status = $8,
		       remark = $9,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, normalized.DepartmentCode, normalized.DepartmentName, normalized.ParentID,
		optionalNullString(normalized.ManagerName), optionalNullString(normalized.ContactPhone),
		departmentSortOrder(normalized), normalized.Status, optionalNullString(normalized.Remark))
	if err != nil {
		if isUniqueViolation(err) {
			return DepartmentResponse{}, NewAuthError(AuthErrorBusiness, "部门编码已存在")
		}
		return DepartmentResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return DepartmentResponse{}, NewAuthError(AuthErrorNotFound, "部门不存在")
	}
	if _, err := tx.Exec(ctx, `
		UPDATE sys_user
		   SET department_name = $2,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE department_id = $1
		   AND deleted_flag = false
	`, id, normalized.DepartmentName); err != nil {
		return DepartmentResponse{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return DepartmentResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s DepartmentService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	if _, err := s.detail(ctx, id); err != nil {
		return err
	}
	var childExists bool
	if err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM sys_department
			 WHERE parent_id = $1
			   AND deleted_flag = false
		)
	`, id).Scan(&childExists); err != nil {
		return err
	}
	if childExists {
		return NewAuthError(AuthErrorBusiness, "存在下级部门，不能删除")
	}
	var userCount int64
	if err := s.db.QueryRow(ctx, `
		SELECT count(*)
		  FROM sys_user
		 WHERE department_id = $1
		   AND deleted_flag = false
	`, id).Scan(&userCount); err != nil {
		return err
	}
	if userCount > 0 {
		return NewAuthError(AuthErrorBusiness, "部门已绑定用户，不能删除")
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE sys_department
		   SET deleted_flag = true,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id)
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "部门不存在")
	}
	return nil
}

func (s DepartmentService) detail(ctx context.Context, id int64) (DepartmentResponse, error) {
	row := s.db.QueryRow(ctx, `
		SELECT id, department_code, department_name, parent_id,
		       COALESCE(manager_name, ''), COALESCE(contact_phone, ''),
		       COALESCE(sort_order, 0), status, COALESCE(remark, '')
		  FROM sys_department
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id)
	result, err := scanDepartment(row)
	if errors.Is(err, pgx.ErrNoRows) {
		return DepartmentResponse{}, NewAuthError(AuthErrorNotFound, "部门不存在")
	}
	if err != nil {
		return DepartmentResponse{}, err
	}
	return result, nil
}

func (s DepartmentService) ensureDepartmentCodeAvailable(ctx context.Context, code string, excludeID int64) error {
	var exists bool
	if err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_department
			 WHERE department_code = $1
			   AND id <> $2
			   AND deleted_flag = false
		)
	`, code, excludeID).Scan(&exists); err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "部门编码已存在")
	}
	return nil
}

func (s DepartmentService) validateParent(ctx context.Context, id int64, parentID *int64) error {
	if parentID == nil {
		return nil
	}
	if id > 0 && *parentID == id {
		return NewAuthError(AuthErrorBusiness, "上级部门不能选择自身")
	}
	var exists bool
	if err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_department
			 WHERE id = $1
			   AND deleted_flag = false
		)
	`, *parentID).Scan(&exists); err != nil {
		return err
	}
	if !exists {
		return NewAuthError(AuthErrorValidation, "上级部门不存在")
	}
	return nil
}

func (s DepartmentService) fillDepartmentParentNames(ctx context.Context, rows []DepartmentResponse) error {
	parentIDs := []int64{}
	seen := map[int64]struct{}{}
	for _, row := range rows {
		if row.ParentID == nil {
			continue
		}
		if _, ok := seen[*row.ParentID]; ok {
			continue
		}
		seen[*row.ParentID] = struct{}{}
		parentIDs = append(parentIDs, *row.ParentID)
	}
	if len(parentIDs) == 0 {
		return nil
	}
	parentRows, err := s.db.Query(ctx, `
		SELECT id, department_name
		  FROM sys_department
		 WHERE id = ANY($1)
		   AND deleted_flag = false
	`, parentIDs)
	if err != nil {
		return err
	}
	defer parentRows.Close()
	names := map[int64]string{}
	for parentRows.Next() {
		var id int64
		var name string
		if err := parentRows.Scan(&id, &name); err != nil {
			return err
		}
		names[id] = name
	}
	if err := parentRows.Err(); err != nil {
		return err
	}
	for i := range rows {
		if rows[i].ParentID != nil {
			rows[i].ParentName = names[*rows[i].ParentID]
		}
	}
	return nil
}

type departmentScanner interface {
	Scan(dest ...any) error
}

func scanDepartment(row departmentScanner) (DepartmentResponse, error) {
	var result DepartmentResponse
	var parentID sql.NullInt64
	if err := row.Scan(
		&result.ID,
		&result.DepartmentCode,
		&result.DepartmentName,
		&parentID,
		&result.ManagerName,
		&result.ContactPhone,
		&result.SortOrder,
		&result.Status,
		&result.Remark,
	); err != nil {
		return DepartmentResponse{}, err
	}
	if parentID.Valid {
		value := parentID.Int64
		result.ParentID = &value
	}
	return result, nil
}

func departmentFilters(keyword string, status string) (string, []any, error) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, likeKeyword(keyword))
		where += " AND (lower(department_code) LIKE $" + strconvArg(len(args)) +
			" OR lower(department_name) LIKE $" + strconvArg(len(args)) +
			" OR lower(COALESCE(manager_name, '')) LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		normalized, err := normalizedStatus(status, "正常")
		if err != nil {
			return "", nil, NewAuthError(AuthErrorValidation, "部门状态不合法")
		}
		args = append(args, normalized)
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args, nil
}

func normalizeDepartmentRequest(request DepartmentRequest) (DepartmentRequest, error) {
	code := strings.TrimSpace(request.DepartmentCode)
	if code == "" {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "部门编码不能为空")
	}
	if len(code) > 64 {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "部门编码长度不能超过64")
	}
	name := strings.TrimSpace(request.DepartmentName)
	if name == "" {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "部门名称不能为空")
	}
	if len(name) > 128 {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "部门名称长度不能超过128")
	}
	managerName := strings.TrimSpace(request.ManagerName)
	if len(managerName) > 64 {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "负责人长度不能超过64")
	}
	contactPhone := strings.TrimSpace(request.ContactPhone)
	if len(contactPhone) > 32 {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "联系电话长度不能超过32")
	}
	sortOrder := 0
	if request.SortOrder != nil {
		if *request.SortOrder < 0 {
			return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "排序号不能小于0")
		}
		sortOrder = *request.SortOrder
	}
	status, err := normalizedStatus(request.Status, "")
	if err != nil {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "状态不合法")
	}
	remark := strings.TrimSpace(request.Remark)
	if len(remark) > 255 {
		return DepartmentRequest{}, NewAuthError(AuthErrorValidation, "备注长度不能超过255")
	}
	return DepartmentRequest{
		DepartmentCode: code,
		DepartmentName: name,
		ParentID:       request.ParentID,
		ManagerName:    managerName,
		ContactPhone:   contactPhone,
		SortOrder:      &sortOrder,
		Status:         status,
		Remark:         remark,
	}, nil
}

func departmentSortOrder(request DepartmentRequest) int {
	if request.SortOrder == nil {
		return 0
	}
	return *request.SortOrder
}
