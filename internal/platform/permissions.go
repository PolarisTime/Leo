package platform

import (
	"context"
	"errors"
	"sort"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type PermissionService struct {
	db *pgxpool.Pool
}

func NewPermissionService(db *pgxpool.Pool) PermissionService {
	return PermissionService{db: db}
}

func (s PermissionService) Require(ctx context.Context, userID int64, resource string, action string) error {
	resource = normalizeResourceCode(resource)
	action = normalizeActionCode(action)
	if userID <= 0 || resource == "" || action == "" {
		return NewAuthError(AuthErrorForbidden, "无权访问")
	}
	if s.db == nil {
		return NewAuthError(AuthErrorForbidden, "无权访问")
	}
	scope, err := s.DataScope(ctx, userID, resource, action)
	if err != nil {
		return err
	}
	if scope.UserID == 0 {
		return NewAuthError(AuthErrorForbidden, "无权访问")
	}
	return nil
}

func (s PermissionService) DataScope(ctx context.Context, userID int64, resource string, action string) (DataScope, error) {
	resource = normalizeResourceCode(resource)
	action = normalizeActionCode(action)
	if userID <= 0 || resource == "" || action == "" {
		return DataScope{}, NewAuthError(AuthErrorForbidden, "无权访问")
	}
	if s.db == nil {
		return DataScope{}, NewAuthError(AuthErrorForbidden, "无权访问")
	}
	rows, err := s.db.Query(ctx, `
		WITH RECURSIVE active_roles AS (
			SELECT role.id, role.parent_id, COALESCE(role.data_scope, 'self') AS data_scope
			  FROM sys_user_role user_role
			  JOIN sys_role role
			    ON role.id = user_role.role_id
			   AND role.deleted_flag = false
			   AND role.status = '正常'
			 WHERE user_role.user_id = $1
			   AND user_role.deleted_flag = false
			UNION
			SELECT parent.id, parent.parent_id, COALESCE(parent.data_scope, 'self') AS data_scope
			  FROM sys_role parent
			  JOIN active_roles child ON child.parent_id = parent.id
			 WHERE parent.deleted_flag = false
			   AND parent.status = '正常'
		)
		SELECT active_roles.data_scope
		  FROM active_roles
		  JOIN sys_role_permission permission
		    ON permission.role_id = active_roles.id
		   AND permission.deleted_flag = false
		 WHERE lower(permission.resource_code) = $2
		   AND lower(permission.action_code) IN ($3, $4)
	`, userID, resource, action, legacyActionCode(action))
	if err != nil {
		return DataScope{}, err
	}
	defer rows.Close()

	scope := ""
	for rows.Next() {
		var dataScope string
		if err := rows.Scan(&dataScope); err != nil {
			return DataScope{}, err
		}
		scope = broaderDataScope(scope, dataScope)
	}
	if err := rows.Err(); err != nil {
		return DataScope{}, err
	}
	if scope == "" {
		return DataScope{}, NewAuthError(AuthErrorForbidden, "无权访问")
	}
	if !IsBusinessResource(resource) {
		return DataScopeAll(userID, resource, action), nil
	}
	ownerUserIDs, err := s.dataScopeOwnerUserIDs(ctx, userID, scope)
	if err != nil {
		return DataScope{}, err
	}
	return DataScope{
		UserID:       userID,
		Resource:     resource,
		Action:       action,
		Scope:        normalizeDataScope(scope),
		OwnerUserIDs: ownerUserIDs,
	}, nil
}

func (s PermissionService) dataScopeOwnerUserIDs(ctx context.Context, userID int64, scope string) ([]int64, error) {
	switch normalizeDataScope(scope) {
	case "all":
		return nil, nil
	case "department":
		return s.departmentOwnerUserIDs(ctx, userID)
	default:
		return []int64{userID}, nil
	}
}

func (s PermissionService) departmentOwnerUserIDs(ctx context.Context, userID int64) ([]int64, error) {
	var departmentID int64
	err := s.db.QueryRow(ctx, `
		SELECT COALESCE(department_id, 0)
		  FROM sys_user
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, userID).Scan(&departmentID)
	if errors.Is(err, pgx.ErrNoRows) || departmentID <= 0 {
		return []int64{userID}, nil
	}
	if err != nil {
		return nil, err
	}

	var departmentActive bool
	err = s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_department
			 WHERE id = $1
			   AND deleted_flag = false
			   AND status = '正常'
		)
	`, departmentID).Scan(&departmentActive)
	if errors.Is(err, pgx.ErrNoRows) || !departmentActive {
		return []int64{userID}, nil
	}
	if err != nil {
		return nil, err
	}

	rows, err := s.db.Query(ctx, `
		WITH RECURSIVE departments AS (
			SELECT id
			  FROM sys_department
			 WHERE id = $1
			   AND deleted_flag = false
			   AND status = '正常'
			UNION
			SELECT child.id
			  FROM sys_department child
			  JOIN departments parent ON child.parent_id = parent.id
			 WHERE child.deleted_flag = false
			   AND child.status = '正常'
		)
		SELECT id
		  FROM sys_user
		 WHERE deleted_flag = false
		   AND department_id IN (SELECT id FROM departments)
	`, departmentID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	ownerUserIDs := []int64{userID}
	for rows.Next() {
		var ownerID int64
		if err := rows.Scan(&ownerID); err != nil {
			return nil, err
		}
		ownerUserIDs = append(ownerUserIDs, ownerID)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	ownerUserIDs = normalizeOwnerUserIDs(ownerUserIDs)
	sort.Slice(ownerUserIDs, func(i, j int) bool {
		return ownerUserIDs[i] < ownerUserIDs[j]
	})
	return ownerUserIDs, nil
}

func legacyActionCode(action string) string {
	switch strings.ToLower(strings.TrimSpace(action)) {
	case "read":
		return "view"
	case "update":
		return "edit"
	default:
		return strings.ToLower(strings.TrimSpace(action))
	}
}
