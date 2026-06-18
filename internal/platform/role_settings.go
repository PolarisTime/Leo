package platform

import (
	"context"
	"database/sql"
	"errors"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type RoleSettingService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

func NewRoleSettingService(db *pgxpool.Pool, machineID ...int64) RoleSettingService {
	var idGenerator *IDGenerator
	if len(machineID) > 0 {
		idGenerator = NewIDGenerator(machineID[0])
	}
	return RoleSettingService{db: db, idGenerator: idGenerator}
}

func (s RoleSettingService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[RoleSettingResponse], error) {
	if s.db == nil {
		return PageResponse[RoleSettingResponse]{}, errors.New("database client is not configured")
	}
	where, args, err := roleSettingFilters(keyword, status)
	if err != nil {
		return PageResponse[RoleSettingResponse]{}, err
	}
	var total int64
	countSQL := "SELECT count(*) FROM sys_role WHERE " + where
	if err := s.db.QueryRow(ctx, countSQL, args...).Scan(&total); err != nil {
		return PageResponse[RoleSettingResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"roleCode": "role_code",
		"roleName": "role_name",
		"roleType": "role_type",
		"status":   "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, role_code, role_name, role_type, data_scope,
		       COALESCE(permission_summary, ''), COALESCE(user_count, 0), status, COALESCE(remark, '')
		  FROM sys_role
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[RoleSettingResponse]{}, err
	}
	defer rows.Close()

	content := []RoleSettingResponse{}
	roleIDs := []int64{}
	for rows.Next() {
		var row RoleSettingResponse
		if err := rows.Scan(&row.ID, &row.RoleCode, &row.RoleName, &row.RoleType, &row.DataScope, &row.PermissionSummary, &row.UserCount, &row.Status, &row.Remark); err != nil {
			return PageResponse[RoleSettingResponse]{}, err
		}
		content = append(content, row)
		roleIDs = append(roleIDs, row.ID)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[RoleSettingResponse]{}, err
	}
	if err := s.fillRoleStats(ctx, content); err != nil {
		return PageResponse[RoleSettingResponse]{}, err
	}
	_ = roleIDs
	return NewPageResponse(content, total, query), nil
}

func (s RoleSettingService) Detail(ctx context.Context, id int64) (RoleSettingResponse, error) {
	if s.db == nil {
		return RoleSettingResponse{}, errors.New("database client is not configured")
	}
	row, err := s.detail(ctx, s.db, id)
	if err != nil {
		return RoleSettingResponse{}, err
	}
	content := []RoleSettingResponse{row}
	if err := s.fillRoleStats(ctx, content); err != nil {
		return RoleSettingResponse{}, err
	}
	return content[0], nil
}

func (s RoleSettingService) Create(ctx context.Context, operatorID int64, request RoleSettingRequest) (RoleSettingResponse, error) {
	if s.db == nil {
		return RoleSettingResponse{}, errors.New("database client is not configured")
	}
	normalized, err := normalizeRoleSettingRequest(request)
	if err != nil {
		return RoleSettingResponse{}, err
	}
	if err := s.assertOperatorCanManageRoleCode(ctx, operatorID, normalized.RoleCode); err != nil {
		return RoleSettingResponse{}, err
	}
	if err := s.ensureRoleCodeAvailable(ctx, s.db, normalized.RoleCode, 0); err != nil {
		return RoleSettingResponse{}, err
	}
	id := s.nextID()
	_, err = s.db.Exec(ctx, `
		INSERT INTO sys_role (
			id, role_code, role_name, role_type, data_scope,
			permission_count, permission_summary, user_count, status, remark,
			created_by, created_name, deleted_flag
		)
		VALUES ($1, $2, $3, $4, $5, 0, NULL, 0, $6, $7, 0, 'system', false)
	`, id, normalized.RoleCode, normalized.RoleName, normalized.RoleType, normalized.DataScope, normalized.Status, optionalNullString(normalized.Remark))
	if err != nil {
		return RoleSettingResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s RoleSettingService) Update(ctx context.Context, operatorID int64, id int64, request RoleSettingRequest) (RoleSettingResponse, error) {
	if s.db == nil {
		return RoleSettingResponse{}, errors.New("database client is not configured")
	}
	normalized, err := normalizeRoleSettingRequest(request)
	if err != nil {
		return RoleSettingResponse{}, err
	}
	current, err := s.detail(ctx, s.db, id)
	if err != nil {
		return RoleSettingResponse{}, err
	}
	if err := s.assertOperatorCanManageRoleCode(ctx, operatorID, current.RoleCode); err != nil {
		return RoleSettingResponse{}, err
	}
	if err := s.assertOperatorCanManageRoleCode(ctx, operatorID, normalized.RoleCode); err != nil {
		return RoleSettingResponse{}, err
	}
	if isAdminRoleCode(current.RoleCode) {
		if normalized.RoleCode != roleSettingAdminCode {
			return RoleSettingResponse{}, NewAuthError(AuthErrorBusiness, "系统管理员角色编码不能修改")
		}
		if normalized.Status != "正常" {
			return RoleSettingResponse{}, NewAuthError(AuthErrorBusiness, "系统管理员角色不能禁用")
		}
	}
	existingPermissions, err := s.rolePermissionMap(ctx, s.db, id)
	if err != nil {
		return RoleSettingResponse{}, err
	}
	if err := s.assertOperatorCanGrantDataScope(ctx, operatorID, normalized.DataScope, existingPermissions); err != nil {
		return RoleSettingResponse{}, err
	}
	if err := s.ensureRoleCodeAvailable(ctx, s.db, normalized.RoleCode, id); err != nil {
		return RoleSettingResponse{}, err
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE sys_role
		   SET role_code = $2,
		       role_name = $3,
		       role_type = $4,
		       data_scope = $5,
		       permission_count = 0,
		       permission_summary = NULL,
		       user_count = 0,
		       status = $6,
		       remark = $7,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, normalized.RoleCode, normalized.RoleName, normalized.RoleType, normalized.DataScope, normalized.Status, optionalNullString(normalized.Remark))
	if err != nil {
		return RoleSettingResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return RoleSettingResponse{}, NewAuthError(AuthErrorNotFound, "角色不存在")
	}
	return s.Detail(ctx, id)
}

func (s RoleSettingService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, s.db, id)
	if err != nil {
		return err
	}
	if isAdminRoleCode(current.RoleCode) {
		return NewAuthError(AuthErrorBusiness, "系统管理员角色不能删除")
	}
	commandTag, err := s.db.Exec(ctx, `
		UPDATE sys_role
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
		return NewAuthError(AuthErrorNotFound, "角色不存在")
	}
	return nil
}

func (s RoleSettingService) Permissions(ctx context.Context, id int64) ([]RolePermissionItem, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	if _, err := s.detail(ctx, s.db, id); err != nil {
		return nil, err
	}
	rows, err := s.db.Query(ctx, `
		SELECT resource_code, action_code
		  FROM sys_role_permission
		 WHERE role_id = $1
		   AND deleted_flag = false
		 ORDER BY resource_code ASC, action_code ASC
	`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []RolePermissionItem{}
	for rows.Next() {
		var item RolePermissionItem
		if err := rows.Scan(&item.Resource, &item.Action); err != nil {
			return nil, err
		}
		item.Resource = normalizeResourceCode(item.Resource)
		item.Action = normalizeActionCode(item.Action)
		if item.Resource == "" || item.Action == "" {
			continue
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s RoleSettingService) SavePermissions(ctx context.Context, operatorID int64, id int64, permissions []RolePermissionItem) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	role, err := s.detail(ctx, s.db, id)
	if err != nil {
		return err
	}
	if isAdminRoleCode(role.RoleCode) {
		return NewAuthError(AuthErrorBusiness, "系统管理员角色权限不能修改")
	}
	normalized, err := normalizeRolePermissionItems(permissions)
	if err != nil {
		return err
	}
	if err := s.assertOperatorCanGrantDataScope(ctx, operatorID, role.DataScope, permissionItemMap(normalized)); err != nil {
		return err
	}
	if err := s.assertOperatorCanGrantPermissions(ctx, operatorID, normalized); err != nil {
		return err
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() {
		_ = tx.Rollback(ctx)
	}()
	if _, err := tx.Exec(ctx, `
		UPDATE sys_role_permission
		   SET deleted_flag = true,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE role_id = $1
		   AND deleted_flag = false
	`, id); err != nil {
		return err
	}
	for _, item := range normalized {
		_, err := tx.Exec(ctx, `
			INSERT INTO sys_role_permission (
				id, role_id, resource_code, action_code, created_by, created_name, deleted_flag
			)
			VALUES ($1, $2, $3, $4, 0, 'system', false)
			ON CONFLICT (role_id, resource_code, action_code) DO UPDATE
			   SET deleted_flag = false,
			       updated_by = 0,
			       updated_name = 'system',
			       updated_at = CURRENT_TIMESTAMP
		`, s.nextID(), id, item.Resource, item.Action)
		if err != nil {
			return err
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return err
	}
	return nil
}

func (s RoleSettingService) rolePermissionMap(ctx context.Context, q interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}, id int64) (map[string]map[string]struct{}, error) {
	rows, err := q.Query(ctx, `
		SELECT resource_code, action_code
		  FROM sys_role_permission
		 WHERE role_id = $1
		   AND deleted_flag = false
	`, id)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := map[string]map[string]struct{}{}
	for rows.Next() {
		var resource, action string
		if err := rows.Scan(&resource, &action); err != nil {
			return nil, err
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" || !catalogAllows(resource, action) {
			continue
		}
		if _, ok := result[resource]; !ok {
			result[resource] = map[string]struct{}{}
		}
		result[resource][action] = struct{}{}
	}
	return result, rows.Err()
}

func (s RoleSettingService) assertOperatorCanManageRoleCode(ctx context.Context, operatorID int64, roleCode string) error {
	if !isAdminRoleCode(roleCode) {
		return nil
	}
	isAdmin, err := s.operatorIsAdmin(ctx, operatorID)
	if err != nil {
		return err
	}
	if !isAdmin {
		return NewAuthError(AuthErrorBusiness, "非系统管理员不能管理系统管理员角色")
	}
	return nil
}

func (s RoleSettingService) assertOperatorCanGrantPermissions(ctx context.Context, operatorID int64, permissions []RolePermissionItem) error {
	isAdmin, err := s.operatorIsAdmin(ctx, operatorID)
	if err != nil {
		return err
	}
	if isAdmin {
		return nil
	}
	operatorPermissions, err := s.operatorPermissionMap(ctx, operatorID)
	if err != nil {
		return err
	}
	for _, permission := range permissions {
		if _, ok := operatorPermissions[permission.Resource][permission.Action]; !ok {
			return NewAuthError(AuthErrorBusiness, "不能授予超出自身权限范围的权限")
		}
	}
	return nil
}

func (s RoleSettingService) assertOperatorCanGrantDataScope(ctx context.Context, operatorID int64, requestedDataScope string, actionsByResource map[string]map[string]struct{}) error {
	isAdmin, err := s.operatorIsAdmin(ctx, operatorID)
	if err != nil {
		return err
	}
	if isAdmin {
		return nil
	}
	requested := normalizeDataScope(requestedDataScope)
	if len(actionsByResource) == 0 {
		if broaderDataScope(requested, "self") != "self" {
			return NewAuthError(AuthErrorBusiness, "不能授予超出自身数据范围的角色")
		}
		return nil
	}
	operatorScopes, err := s.operatorDataScopeMap(ctx, operatorID)
	if err != nil {
		return err
	}
	for resource, actions := range actionsByResource {
		for action := range actions {
			current := operatorScopes[resource+":"+action]
			if broaderDataScope(requested, current) != normalizeDataScope(current) {
				return NewAuthError(AuthErrorBusiness, "不能授予超出自身数据范围的角色")
			}
		}
	}
	return nil
}

func (s RoleSettingService) operatorIsAdmin(ctx context.Context, operatorID int64) (bool, error) {
	if operatorID <= 0 {
		return false, nil
	}
	var exists bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_user_role user_role
			  JOIN sys_role role
			    ON role.id = user_role.role_id
			   AND role.deleted_flag = false
			   AND role.status = '正常'
			 WHERE user_role.user_id = $1
			   AND user_role.deleted_flag = false
			   AND upper(role.role_code) = $2
		)
	`, operatorID, roleSettingAdminCode).Scan(&exists)
	return exists, err
}

func (s RoleSettingService) operatorPermissionMap(ctx context.Context, operatorID int64) (map[string]map[string]struct{}, error) {
	rows, err := s.db.Query(ctx, `
		SELECT permission.resource_code, permission.action_code
		  FROM sys_user_role user_role
		  JOIN sys_role role
		    ON role.id = user_role.role_id
		   AND role.deleted_flag = false
		   AND role.status = '正常'
		  JOIN sys_role_permission permission
		    ON permission.role_id = role.id
		   AND permission.deleted_flag = false
		 WHERE user_role.user_id = $1
		   AND user_role.deleted_flag = false
	`, operatorID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := map[string]map[string]struct{}{}
	for rows.Next() {
		var resource, action string
		if err := rows.Scan(&resource, &action); err != nil {
			return nil, err
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		if _, ok := result[resource]; !ok {
			result[resource] = map[string]struct{}{}
		}
		result[resource][action] = struct{}{}
	}
	return result, rows.Err()
}

func (s RoleSettingService) operatorDataScopeMap(ctx context.Context, operatorID int64) (map[string]string, error) {
	rows, err := s.db.Query(ctx, `
		SELECT permission.resource_code, permission.action_code, COALESCE(role.data_scope, '本人')
		  FROM sys_user_role user_role
		  JOIN sys_role role
		    ON role.id = user_role.role_id
		   AND role.deleted_flag = false
		   AND role.status = '正常'
		  JOIN sys_role_permission permission
		    ON permission.role_id = role.id
		   AND permission.deleted_flag = false
		 WHERE user_role.user_id = $1
		   AND user_role.deleted_flag = false
	`, operatorID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := map[string]string{}
	for rows.Next() {
		var resource, action, scope string
		if err := rows.Scan(&resource, &action, &scope); err != nil {
			return nil, err
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		key := resource + ":" + action
		result[key] = broaderDataScope(result[key], scope)
	}
	return result, rows.Err()
}

func (s RoleSettingService) PermissionOptions(context.Context) ([]MenuNode, error) {
	return rolePermissionOptions(), nil
}

func (s RoleSettingService) Templates(context.Context) ([]RoleTemplate, error) {
	return roleTemplates(), nil
}

func (s RoleSettingService) detail(ctx context.Context, q pgxQuerier, id int64) (RoleSettingResponse, error) {
	var row RoleSettingResponse
	err := q.QueryRow(ctx, `
		SELECT id, role_code, role_name, role_type, data_scope,
		       COALESCE(permission_summary, ''), COALESCE(user_count, 0), status, COALESCE(remark, '')
		  FROM sys_role
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(&row.ID, &row.RoleCode, &row.RoleName, &row.RoleType, &row.DataScope, &row.PermissionSummary, &row.UserCount, &row.Status, &row.Remark)
	if errors.Is(err, pgx.ErrNoRows) {
		return RoleSettingResponse{}, NewAuthError(AuthErrorNotFound, "角色不存在")
	}
	if err != nil {
		return RoleSettingResponse{}, err
	}
	return row, nil
}

func (s RoleSettingService) ensureRoleCodeAvailable(ctx context.Context, q pgxQuerier, roleCode string, excludeID int64) error {
	var exists bool
	err := q.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_role
			 WHERE role_code = $1
			   AND deleted_flag = false
			   AND id <> $2
		)
	`, roleCode, excludeID).Scan(&exists)
	if err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "角色编码已存在")
	}
	return nil
}

func (s RoleSettingService) nextID() int64 {
	if s.idGenerator == nil {
		return roleSettingFallbackIDGenerator.Next()
	}
	return s.idGenerator.Next()
}

func (s RoleSettingService) fillRoleStats(ctx context.Context, roles []RoleSettingResponse) error {
	if len(roles) == 0 {
		return nil
	}
	ids := make([]int64, 0, len(roles))
	indexByID := map[int64]int{}
	for i := range roles {
		ids = append(ids, roles[i].ID)
		indexByID[roles[i].ID] = i
		roles[i].PermissionCodes = []string{}
	}
	rows, err := s.db.Query(ctx, `
		SELECT role_id, resource_code, action_code
		  FROM sys_role_permission
		 WHERE deleted_flag = false
		   AND role_id = ANY($1)
		 ORDER BY resource_code ASC, action_code ASC
	`, ids)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var roleID int64
		var resource, action string
		if err := rows.Scan(&roleID, &resource, &action); err != nil {
			return err
		}
		index, ok := indexByID[roleID]
		if !ok {
			continue
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		roles[index].PermissionCodes = append(roles[index].PermissionCodes, resource+":"+action)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	countRows, err := s.db.Query(ctx, `
		SELECT role_id, count(DISTINCT user_id)
		  FROM sys_user_role
		 WHERE deleted_flag = false
		   AND role_id = ANY($1)
		 GROUP BY role_id
	`, ids)
	if err != nil {
		return err
	}
	defer countRows.Close()
	for countRows.Next() {
		var roleID int64
		var count int
		if err := countRows.Scan(&roleID, &count); err != nil {
			return err
		}
		if index, ok := indexByID[roleID]; ok {
			roles[index].UserCount = count
		}
	}
	if err := countRows.Err(); err != nil {
		return err
	}
	for i := range roles {
		roles[i].PermissionCount = len(roles[i].PermissionCodes)
		roles[i].PermissionSummary = rolePermissionSummary(roles[i].PermissionCodes)
	}
	return nil
}

func roleSettingFilters(keyword string, status string) (string, []any, error) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, likeKeyword(keyword))
		where += " AND (lower(role_code) LIKE $" + strconvArg(len(args)) +
			" OR lower(role_name) LIKE $" + strconvArg(len(args)) +
			" OR lower(role_type) LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		normalized, err := normalizedStatus(status, "正常")
		if err != nil {
			return "", nil, NewAuthError(AuthErrorValidation, "角色状态不合法")
		}
		args = append(args, normalized)
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args, nil
}

func rolePermissionSummary(codes []string) string {
	if len(codes) == 0 {
		return ""
	}
	seen := map[string]struct{}{}
	labels := []string{}
	for _, code := range codes {
		resource, action, ok := strings.Cut(code, ":")
		if !ok {
			continue
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		label := rolePermissionLabel(resource, action)
		if _, ok := seen[label]; ok {
			continue
		}
		seen[label] = struct{}{}
		labels = append(labels, label)
	}
	if len(labels) <= 6 {
		return strings.Join(labels, "、")
	}
	return strings.Join(labels[:6], "、") + " 等" + strconv.Itoa(len(labels)) + "项"
}

func rolePermissionLabel(resource string, action string) string {
	return catalogResourceTitle(resource) + "-" + catalogActionTitle(resource, action)
}

func catalogResourceTitle(resource string) string {
	for _, entry := range PermissionCatalog() {
		if entry.Code == resource {
			return entry.Title
		}
	}
	return resource
}

func catalogActionTitle(resource string, action string) string {
	for _, entry := range PermissionCatalog() {
		if entry.Code != resource {
			continue
		}
		for _, option := range entry.Actions {
			if option.Code == action {
				return option.Title
			}
		}
	}
	return action
}

type pgxQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

const roleSettingAdminCode = "ADMIN"

var roleSettingFallbackIDGenerator = NewIDGenerator(0)

var roleCodePattern = regexp.MustCompile(`^[A-Z0-9_-]+$`)

var allowedRoleTypes = map[string]struct{}{
	"平台角色": {},
	"系统角色": {},
	"业务角色": {},
	"财务角色": {},
}

var allowedRoleDataScopes = map[string]struct{}{
	"全部数据": {},
	"全部":   {},
	"本部门":  {},
	"本人":   {},
}

var permissionGroupOrder = []string{"工作台", "主数据", "采购", "销售", "物流", "合同", "对账", "财务", "报表", "系统"}

func normalizeRoleSettingRequest(request RoleSettingRequest) (RoleSettingRequest, error) {
	roleCode := strings.ToUpper(strings.TrimSpace(request.RoleCode))
	if roleCode == "" {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色编码不能为空")
	}
	if len(roleCode) > 64 {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色编码长度不能超过64")
	}
	if !roleCodePattern.MatchString(roleCode) {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色编码格式不正确")
	}
	roleName := strings.TrimSpace(request.RoleName)
	if roleName == "" {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色名称不能为空")
	}
	if len(roleName) > 128 {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色名称长度不能超过128")
	}
	roleType := strings.TrimSpace(request.RoleType)
	if len(roleType) > 32 {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色类型长度不能超过32")
	}
	if _, ok := allowedRoleTypes[roleType]; !ok {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色类型不合法")
	}
	dataScope := strings.TrimSpace(request.DataScope)
	if len(dataScope) > 32 {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "数据范围长度不能超过32")
	}
	if _, ok := allowedRoleDataScopes[dataScope]; !ok {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "数据范围不合法")
	}
	status, err := normalizedStatus(request.Status, "正常")
	if err != nil {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "角色状态不合法")
	}
	remark := strings.TrimSpace(request.Remark)
	if len(remark) > 255 {
		return RoleSettingRequest{}, NewAuthError(AuthErrorValidation, "备注长度不能超过255")
	}
	return RoleSettingRequest{
		RoleCode:  roleCode,
		RoleName:  roleName,
		RoleType:  roleType,
		DataScope: dataScope,
		Status:    status,
		Remark:    remark,
	}, nil
}

func normalizeRolePermissionItems(items []RolePermissionItem) ([]RolePermissionItem, error) {
	if items == nil {
		return nil, NewAuthError(AuthErrorValidation, "权限列表不能为空")
	}
	actionsByResource := map[string]map[string]struct{}{}
	order := []string{}
	seen := map[string]struct{}{}
	for _, item := range items {
		resource := normalizeResourceCode(item.Resource)
		if resolved, ok := resourceForMenuCode(resource); ok {
			resource = resolved
		}
		action := normalizeActionCode(item.Action)
		uniqueKey := resource + ":" + action
		if _, ok := seen[uniqueKey]; ok {
			return nil, NewAuthError(AuthErrorValidation, "权限列表存在重复项")
		}
		seen[uniqueKey] = struct{}{}
		if !catalogAllows(resource, action) {
			return nil, NewAuthError(AuthErrorValidation, "存在无效的资源权限配置: "+uniqueKey)
		}
		if _, ok := actionsByResource[resource]; !ok {
			actionsByResource[resource] = map[string]struct{}{}
			order = append(order, resource)
		}
		actionsByResource[resource][action] = struct{}{}
	}
	for _, actions := range actionsByResource {
		for action := range actions {
			if action != "read" {
				actions["read"] = struct{}{}
				break
			}
		}
	}
	if hasAccessControlChild(actionsByResource) {
		if _, ok := actionsByResource["access-control"]; !ok {
			actionsByResource["access-control"] = map[string]struct{}{}
			order = append(order, "access-control")
		}
		actionsByResource["access-control"]["read"] = struct{}{}
	}
	sort.Strings(order)
	result := []RolePermissionItem{}
	for _, resource := range order {
		actions := make([]string, 0, len(actionsByResource[resource]))
		for action := range actionsByResource[resource] {
			actions = append(actions, action)
		}
		sort.Strings(actions)
		for _, action := range actions {
			result = append(result, RolePermissionItem{Resource: resource, Action: action})
		}
	}
	return result, nil
}

func permissionItemMap(items []RolePermissionItem) map[string]map[string]struct{} {
	result := map[string]map[string]struct{}{}
	for _, item := range items {
		resource := normalizeResourceCode(item.Resource)
		action := normalizeActionCode(item.Action)
		if resource == "" || action == "" {
			continue
		}
		if _, ok := result[resource]; !ok {
			result[resource] = map[string]struct{}{}
		}
		result[resource][action] = struct{}{}
	}
	return result
}

func hasAccessControlChild(actionsByResource map[string]map[string]struct{}) bool {
	for _, resource := range []string{"user-account", "permission", "role"} {
		if _, ok := actionsByResource[resource]; ok {
			return true
		}
	}
	return false
}

func rolePermissionOptions() []MenuNode {
	grouped := map[string][]CatalogEntry{}
	for _, entry := range PermissionCatalog() {
		grouped[entry.Group] = append(grouped[entry.Group], entry)
	}
	groups := make([]string, 0, len(grouped))
	for group := range grouped {
		groups = append(groups, group)
	}
	sort.SliceStable(groups, func(i, j int) bool {
		return permissionGroupIndex(groups[i]) < permissionGroupIndex(groups[j])
	})
	result := []MenuNode{}
	for groupIndex, group := range groups {
		groupCode := "permission-group-" + strconv.Itoa(groupIndex+1)
		children := []MenuNode{}
		for childIndex, entry := range grouped[group] {
			resourceCode := entry.Code
			routePath := firstStringPointer(entry.PathPrefixes)
			actions := make([]string, 0, len(entry.Actions))
			for _, action := range entry.Actions {
				actions = append(actions, action.Code)
			}
			children = append(children, MenuNode{
				MenuCode:     entry.Code,
				MenuName:     entry.Title,
				ParentCode:   &groupCode,
				RoutePath:    routePath,
				SortOrder:    childIndex + 1,
				MenuType:     "菜单",
				ResourceCode: &resourceCode,
				Actions:      actions,
				Children:     []MenuNode{},
			})
		}
		result = append(result, MenuNode{
			MenuCode:  groupCode,
			MenuName:  group,
			SortOrder: groupIndex + 1,
			MenuType:  "目录",
			Actions:   []string{},
			Children:  children,
		})
	}
	return result
}

func permissionGroupIndex(group string) int {
	for i, value := range permissionGroupOrder {
		if value == group {
			return i
		}
	}
	return len(permissionGroupOrder)
}

func firstStringPointer(values []string) *string {
	if len(values) == 0 || strings.TrimSpace(values[0]) == "" {
		return nil
	}
	value := values[0]
	return &value
}

func roleTemplates() []RoleTemplate {
	return []RoleTemplate{
		{
			Name:        "采购员",
			Description: "采购订单、采购入库、供应商管理",
			Permissions: []RoleTemplatePermissionEntry{
				roleTemplatePermission("purchase-order", "read", "create", "update", "delete"),
				roleTemplatePermission("purchase-inbound", "read", "create", "update"),
				roleTemplatePermission("supplier", "read"),
				roleTemplatePermission("material", "read"),
				roleTemplatePermission("warehouse", "read"),
				roleTemplatePermission("supplier-statement", "read"),
			},
		},
		{
			Name:        "销售员",
			Description: "销售订单、销售出库、客户管理",
			Permissions: []RoleTemplatePermissionEntry{
				roleTemplatePermission("sales-order", "read", "create", "update", "delete"),
				roleTemplatePermission("sales-outbound", "read", "create", "update"),
				roleTemplatePermission("customer", "read"),
				roleTemplatePermission("material", "read"),
				roleTemplatePermission("warehouse", "read"),
				roleTemplatePermission("customer-statement", "read"),
			},
		},
		{
			Name:        "财务",
			Description: "收付款、发票、对账单",
			Permissions: []RoleTemplatePermissionEntry{
				roleTemplatePermission("receipt", "read", "create", "update", "audit"),
				roleTemplatePermission("payment", "read", "create", "update", "audit"),
				roleTemplatePermission("invoice-issue", "read", "create", "update"),
				roleTemplatePermission("invoice-receipt", "read", "create", "update"),
				roleTemplatePermission("customer-statement", "read", "export"),
				roleTemplatePermission("supplier-statement", "read", "export"),
				roleTemplatePermission("company-setting", "read"),
			},
		},
		{
			Name:        "仓管",
			Description: "仓库、入库出库只读",
			Permissions: []RoleTemplatePermissionEntry{
				roleTemplatePermission("warehouse", "read", "create", "update"),
				roleTemplatePermission("purchase-inbound", "read"),
				roleTemplatePermission("sales-outbound", "read"),
				roleTemplatePermission("material", "read"),
			},
		},
		{
			Name:        "管理员",
			Description: "全部资源全部操作",
			Permissions: []RoleTemplatePermissionEntry{
				roleTemplatePermission("*", "*"),
			},
		},
	}
}

func roleTemplatePermission(resource string, actions ...string) RoleTemplatePermissionEntry {
	return RoleTemplatePermissionEntry{Resource: resource, Actions: actions}
}

func isAdminRoleCode(value string) bool {
	return strings.ToUpper(strings.TrimSpace(value)) == roleSettingAdminCode
}

func strconvArg(value int) string {
	return strconv.Itoa(value)
}

var _ = sql.ErrNoRows
