package platform

import (
	"context"
	"database/sql"
	"errors"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type MaterialCategoryService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type materialCategoryRecord struct {
	ID                    int64
	CategoryCode          string
	CategoryName          string
	SortOrder             int
	PurchaseWeighRequired bool
	Status                string
	Remark                sql.NullString
	CreatedBy             int64
}

func NewMaterialCategoryService(db *pgxpool.Pool, machineID int64) MaterialCategoryService {
	return MaterialCategoryService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s MaterialCategoryService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[MaterialCategoryResponse], error) {
	if s.db == nil {
		return PageResponse[MaterialCategoryResponse]{}, errors.New("database client is not configured")
	}
	where, args := materialCategoryFilters(keyword, status)
	where, args = applyDataScopeFilter(ctx, where, args, "created_by")
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM md_material_category WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[MaterialCategoryResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy, err := materialCategoryOrderBy(query)
	if err != nil {
		return PageResponse[MaterialCategoryResponse]{}, err
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, category_code, category_name, COALESCE(sort_order, 0),
		       COALESCE(purchase_weigh_required, false), status, remark, created_by
		  FROM md_material_category
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[MaterialCategoryResponse]{}, err
	}
	defer rows.Close()

	content := []MaterialCategoryResponse{}
	for rows.Next() {
		record, err := scanMaterialCategory(rows)
		if err != nil {
			return PageResponse[MaterialCategoryResponse]{}, err
		}
		content = append(content, materialCategoryResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[MaterialCategoryResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func materialCategoryOrderBy(query PageQuery) (string, error) {
	allowed := map[string]string{
		"categoryCode":          "category_code",
		"categoryName":          "category_name",
		"sortOrder":             "sort_order",
		"purchaseWeighRequired": "purchase_weigh_required",
		"status":                "status",
		"id":                    "id",
	}
	sortBy := strings.TrimSpace(query.SortBy)
	if sortBy == "" {
		sortBy = "sortOrder"
	}
	column := allowed[sortBy]
	if column == "" {
		return "", errors.New("No property '" + sortBy + "' found for type 'MaterialCategory'")
	}
	if strings.EqualFold(query.Direction, "asc") {
		return column + " ASC", nil
	}
	return column + " DESC", nil
}

func (s MaterialCategoryService) Detail(ctx context.Context, id int64) (MaterialCategoryResponse, error) {
	if s.db == nil {
		return MaterialCategoryResponse{}, errors.New("database client is not configured")
	}
	record, err := s.detail(ctx, id)
	if err != nil {
		return MaterialCategoryResponse{}, err
	}
	return materialCategoryResponse(record), nil
}

func (s MaterialCategoryService) Create(ctx context.Context, request MaterialCategoryRequest) (MaterialCategoryResponse, error) {
	if s.db == nil {
		return MaterialCategoryResponse{}, errors.New("database client is not configured")
	}
	normalized, err := normalizeMaterialCategoryRequest(request)
	if err != nil {
		return MaterialCategoryResponse{}, err
	}
	if err := s.ensureCategoryCodeAvailable(ctx, normalized.CategoryCode, 0); err != nil {
		return MaterialCategoryResponse{}, err
	}
	id := s.idGenerator.Next()
	createdBy := auditUserID(ctx)
	_, err = s.db.Exec(ctx, `
		INSERT INTO md_material_category (
			id, category_code, category_name, sort_order, purchase_weigh_required,
			status, remark, created_by, created_name,
			updated_by, updated_name, updated_at, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'system', $8, 'system', CURRENT_TIMESTAMP, false)
	`, id, normalized.CategoryCode, normalized.CategoryName, normalized.sortOrderValue(),
		normalized.purchaseWeighRequiredValue(), normalized.Status, optionalNullString(normalized.Remark), createdBy)
	if err != nil {
		if isUniqueViolation(err) {
			return MaterialCategoryResponse{}, NewAuthError(AuthErrorBusiness, "类别编码已存在")
		}
		return MaterialCategoryResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s MaterialCategoryService) Update(ctx context.Context, id int64, request MaterialCategoryRequest) (MaterialCategoryResponse, error) {
	if s.db == nil {
		return MaterialCategoryResponse{}, errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return MaterialCategoryResponse{}, err
	}
	normalized, err := normalizeMaterialCategoryRequest(request)
	if err != nil {
		return MaterialCategoryResponse{}, err
	}
	if current.CategoryCode != normalized.CategoryCode {
		if err := s.ensureCategoryCodeAvailable(ctx, normalized.CategoryCode, id); err != nil {
			return MaterialCategoryResponse{}, err
		}
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_material_category
		   SET category_code = $2,
		       category_name = $3,
		       sort_order = $4,
		       purchase_weigh_required = $5,
		       status = $6,
		       remark = $7,
		       updated_by = $8,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, normalized.CategoryCode, normalized.CategoryName, normalized.sortOrderValue(),
		normalized.purchaseWeighRequiredValue(), normalized.Status, optionalNullString(normalized.Remark), auditUserID(ctx))
	if err != nil {
		if isUniqueViolation(err) {
			return MaterialCategoryResponse{}, NewAuthError(AuthErrorBusiness, "类别编码已存在")
		}
		return MaterialCategoryResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return MaterialCategoryResponse{}, NewAuthError(AuthErrorNotFound, "商品类别不存在")
	}
	return s.Detail(ctx, id)
}

func (s MaterialCategoryService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	if _, err := s.detail(ctx, id); err != nil {
		return err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE md_material_category
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
		return NewAuthError(AuthErrorNotFound, "商品类别不存在")
	}
	return nil
}

func (s MaterialCategoryService) Options(ctx context.Context) ([]MaterialCategoryOptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT category_name, COALESCE(purchase_weigh_required, false)
		  FROM md_material_category
		 WHERE status = '正常'
		   AND deleted_flag = false
		 ORDER BY sort_order ASC, id ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := []MaterialCategoryOptionResponse{}
	for rows.Next() {
		var name string
		var purchaseWeighRequired bool
		if err := rows.Scan(&name, &purchaseWeighRequired); err != nil {
			return nil, err
		}
		result = append(result, MaterialCategoryOptionResponse{
			Value:                 name,
			Label:                 name,
			PurchaseWeighRequired: purchaseWeighRequired,
		})
	}
	return result, rows.Err()
}

func (s MaterialCategoryService) detail(ctx context.Context, id int64) (materialCategoryRecord, error) {
	var record materialCategoryRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, category_code, category_name, COALESCE(sort_order, 0),
		       COALESCE(purchase_weigh_required, false), status, remark, created_by
		  FROM md_material_category
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&record.ID,
		&record.CategoryCode,
		&record.CategoryName,
		&record.SortOrder,
		&record.PurchaseWeighRequired,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return materialCategoryRecord{}, NewAuthError(AuthErrorNotFound, "商品类别不存在")
	}
	if err != nil {
		return materialCategoryRecord{}, err
	}
	if err := assertDataScopeAccess(ctx, record.CreatedBy); err != nil {
		return materialCategoryRecord{}, err
	}
	return record, nil
}

func (s MaterialCategoryService) ensureCategoryCodeAvailable(ctx context.Context, categoryCode string, excludeID int64) error {
	query := `
		SELECT EXISTS (
			SELECT 1
			  FROM md_material_category
			 WHERE category_code = $1
			   AND deleted_flag = false
	`
	args := []any{categoryCode}
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
		return NewAuthError(AuthErrorBusiness, "类别编码已存在")
	}
	return nil
}

func materialCategoryFilters(keyword string, status string) (string, []any) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(keyword)+"%")
		where += " AND (category_code LIKE $" + strconvArg(len(args)) + " OR category_name LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		args = append(args, strings.TrimSpace(status))
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args
}

func normalizeMaterialCategoryRequest(request MaterialCategoryRequest) (MaterialCategoryRequest, error) {
	categoryCode, err := requiredMaterialCategoryText(request.CategoryCode, "类别编码")
	if err != nil {
		return MaterialCategoryRequest{}, err
	}
	if len([]rune(categoryCode)) > 32 {
		return MaterialCategoryRequest{}, NewAuthError(AuthErrorValidation, "类别编码长度不能超过32个字符")
	}
	categoryName, err := requiredMaterialCategoryText(request.CategoryName, "类别名称")
	if err != nil {
		return MaterialCategoryRequest{}, err
	}
	if len([]rune(categoryName)) > 64 {
		return MaterialCategoryRequest{}, NewAuthError(AuthErrorValidation, "类别名称长度不能超过64个字符")
	}
	if request.SortOrder != nil {
		if *request.SortOrder < 0 {
			return MaterialCategoryRequest{}, NewAuthError(AuthErrorValidation, "排序值不能小于0")
		}
		if *request.SortOrder > 9999 {
			return MaterialCategoryRequest{}, NewAuthError(AuthErrorValidation, "排序值不能超过9999")
		}
	}
	status := strings.TrimSpace(request.Status)
	if len([]rune(status)) > 16 {
		return MaterialCategoryRequest{}, NewAuthError(AuthErrorValidation, "状态长度不能超过16个字符")
	}
	if status == "" {
		status = "正常"
	}
	remark := strings.TrimSpace(request.Remark)
	if len([]rune(remark)) > 255 {
		return MaterialCategoryRequest{}, NewAuthError(AuthErrorValidation, "备注长度不能超过255个字符")
	}
	return MaterialCategoryRequest{
		CategoryCode:          categoryCode,
		CategoryName:          categoryName,
		SortOrder:             request.SortOrder,
		PurchaseWeighRequired: request.PurchaseWeighRequired,
		Status:                status,
		Remark:                remark,
	}, nil
}

func requiredMaterialCategoryText(value string, field string) (string, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return "", NewAuthError(AuthErrorValidation, field+"不能为空")
	}
	return value, nil
}

func (request MaterialCategoryRequest) sortOrderValue() int {
	if request.SortOrder == nil {
		return 0
	}
	return *request.SortOrder
}

func (request MaterialCategoryRequest) purchaseWeighRequiredValue() bool {
	return request.PurchaseWeighRequired != nil && *request.PurchaseWeighRequired
}

func materialCategoryResponse(record materialCategoryRecord) MaterialCategoryResponse {
	return MaterialCategoryResponse{
		ID:                    strconv.FormatInt(record.ID, 10),
		CategoryCode:          record.CategoryCode,
		CategoryName:          record.CategoryName,
		SortOrder:             record.SortOrder,
		PurchaseWeighRequired: record.PurchaseWeighRequired,
		Status:                record.Status,
		Remark:                nullableString(record.Remark),
	}
}

type materialCategoryScanner interface {
	Scan(dest ...any) error
}

func scanMaterialCategory(row materialCategoryScanner) (materialCategoryRecord, error) {
	var record materialCategoryRecord
	err := row.Scan(
		&record.ID,
		&record.CategoryCode,
		&record.CategoryName,
		&record.SortOrder,
		&record.PurchaseWeighRequired,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	return record, err
}
