package platform

import (
	"context"
	"strings"
)

type contextKey string

const (
	principalContextKey contextKey = "platform.principal"
	dataScopeContextKey contextKey = "platform.data_scope"
)

type DataScope struct {
	UserID       int64
	Resource     string
	Action       string
	Scope        string
	OwnerUserIDs []int64
}

func ContextWithAuthenticatedPrincipal(ctx context.Context, principal AuthenticatedPrincipal) context.Context {
	return context.WithValue(ctx, principalContextKey, principal)
}

func AuthenticatedPrincipalFromContext(ctx context.Context) (AuthenticatedPrincipal, bool) {
	principal, ok := ctx.Value(principalContextKey).(AuthenticatedPrincipal)
	return principal, ok
}

func auditUserID(ctx context.Context) int64 {
	principal, ok := AuthenticatedPrincipalFromContext(ctx)
	if !ok || principal.UserID <= 0 {
		return 0
	}
	return principal.UserID
}

func ContextWithDataScope(ctx context.Context, scope DataScope) context.Context {
	scope.Scope = normalizeDataScope(scope.Scope)
	scope.Resource = normalizeResourceCode(scope.Resource)
	scope.Action = normalizeActionCode(scope.Action)
	scope.OwnerUserIDs = normalizeOwnerUserIDs(scope.OwnerUserIDs)
	return context.WithValue(ctx, dataScopeContextKey, scope)
}

func CurrentDataScope(ctx context.Context) (DataScope, bool) {
	scope, ok := ctx.Value(dataScopeContextKey).(DataScope)
	return scope, ok
}

func DataScopeAll(userID int64, resource string, action string) DataScope {
	return DataScope{
		UserID:   userID,
		Resource: normalizeResourceCode(resource),
		Action:   normalizeActionCode(action),
		Scope:    "all",
	}
}

func applyDataScopeFilter(ctx context.Context, where string, args []any, createdByColumn string) (string, []any) {
	scope, ok := CurrentDataScope(ctx)
	if !ok || normalizeDataScope(scope.Scope) == "all" {
		return where, args
	}
	createdByColumn = strings.TrimSpace(createdByColumn)
	if createdByColumn == "" {
		createdByColumn = "created_by"
	}
	if len(scope.OwnerUserIDs) == 0 {
		return where + " AND 1 = 0", args
	}
	if len(scope.OwnerUserIDs) == 1 {
		args = append(args, scope.OwnerUserIDs[0])
		return where + " AND " + createdByColumn + " = $" + strconvArg(len(args)), args
	}
	args = append(args, scope.OwnerUserIDs)
	return where + " AND " + createdByColumn + " = ANY($" + strconvArg(len(args)) + ")", args
}

func assertDataScopeAccess(ctx context.Context, createdBy int64) error {
	scope, ok := CurrentDataScope(ctx)
	if !ok || normalizeDataScope(scope.Scope) == "all" {
		return nil
	}
	for _, ownerID := range scope.OwnerUserIDs {
		if ownerID == createdBy {
			return nil
		}
	}
	return NewAuthError(AuthErrorForbidden, "无数据权限")
}

func normalizeOwnerUserIDs(ownerUserIDs []int64) []int64 {
	seen := map[int64]struct{}{}
	result := make([]int64, 0, len(ownerUserIDs))
	for _, ownerID := range ownerUserIDs {
		if ownerID <= 0 {
			continue
		}
		if _, ok := seen[ownerID]; ok {
			continue
		}
		seen[ownerID] = struct{}{}
		result = append(result, ownerID)
	}
	return result
}

func IsBusinessResource(resource string) bool {
	resource = normalizeResourceCode(resource)
	for _, entry := range PermissionCatalog() {
		if entry.Code == resource {
			return entry.BusinessResource
		}
	}
	return false
}
