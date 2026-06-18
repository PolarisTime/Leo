package platform

import (
	"context"
	"errors"
	"testing"
)

func TestAuditUserIDReadsAuthenticatedPrincipal(t *testing.T) {
	ctx := ContextWithAuthenticatedPrincipal(context.Background(), AuthenticatedPrincipal{UserID: 42})
	if got := auditUserID(ctx); got != 42 {
		t.Fatalf("auditUserID = %d, want 42", got)
	}
	if got := auditUserID(context.Background()); got != 0 {
		t.Fatalf("auditUserID without principal = %d, want 0", got)
	}
}

func TestApplyDataScopeFilter(t *testing.T) {
	tests := []struct {
		name      string
		scope     DataScope
		wantWhere string
		wantArgs  []any
	}{
		{
			name:      "all skips filter",
			scope:     DataScope{Scope: "all"},
			wantWhere: "deleted_flag = false",
			wantArgs:  []any{"正常"},
		},
		{
			name:      "self uses equality",
			scope:     DataScope{Scope: "self", OwnerUserIDs: []int64{7}},
			wantWhere: "deleted_flag = false AND created_by = $2",
			wantArgs:  []any{"正常", int64(7)},
		},
		{
			name:      "department uses any",
			scope:     DataScope{Scope: "department", OwnerUserIDs: []int64{7, 8}},
			wantWhere: "deleted_flag = false AND created_by = ANY($2)",
			wantArgs:  []any{"正常", []int64{7, 8}},
		},
		{
			name:      "empty owners denies all",
			scope:     DataScope{Scope: "self"},
			wantWhere: "deleted_flag = false AND 1 = 0",
			wantArgs:  []any{"正常"},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ctx := ContextWithDataScope(context.Background(), tt.scope)
			gotWhere, gotArgs := applyDataScopeFilter(ctx, "deleted_flag = false", []any{"正常"}, "created_by")
			if gotWhere != tt.wantWhere {
				t.Fatalf("where = %q, want %q", gotWhere, tt.wantWhere)
			}
			if len(gotArgs) != len(tt.wantArgs) {
				t.Fatalf("args len = %d, want %d: %#v", len(gotArgs), len(tt.wantArgs), gotArgs)
			}
			for i := range gotArgs {
				if !sameArg(gotArgs[i], tt.wantArgs[i]) {
					t.Fatalf("arg[%d] = %#v, want %#v", i, gotArgs[i], tt.wantArgs[i])
				}
			}
		})
	}
}

func TestAssertDataScopeAccess(t *testing.T) {
	ctx := ContextWithDataScope(context.Background(), DataScope{Scope: "self", OwnerUserIDs: []int64{7}})
	if err := assertDataScopeAccess(ctx, 7); err != nil {
		t.Fatalf("assertDataScopeAccess allowed owner error: %v", err)
	}
	err := assertDataScopeAccess(ctx, 8)
	if err == nil {
		t.Fatal("expected forbidden error")
	}
	var authErr AuthError
	if !errors.As(err, &authErr) || authErr.Kind != AuthErrorForbidden {
		t.Fatalf("error = %#v, want forbidden AuthError", err)
	}
}

func sameArg(left any, right any) bool {
	leftIDs, leftOK := left.([]int64)
	rightIDs, rightOK := right.([]int64)
	if leftOK || rightOK {
		if !leftOK || !rightOK || len(leftIDs) != len(rightIDs) {
			return false
		}
		for i := range leftIDs {
			if leftIDs[i] != rightIDs[i] {
				return false
			}
		}
		return true
	}
	return left == right
}
