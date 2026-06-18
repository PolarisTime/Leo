package platform

import (
	"errors"
	"testing"
)

func TestNormalizeRoleSettingRequestMatchesJavaContract(t *testing.T) {
	tests := []struct {
		name    string
		request RoleSettingRequest
		wantErr bool
	}{
		{
			name: "normalizes role code and default status",
			request: RoleSettingRequest{
				RoleCode:  " ops_admin ",
				RoleName:  " 运营 ",
				RoleType:  "业务角色",
				DataScope: "本部门",
			},
		},
		{
			name: "rejects role code with invalid character",
			request: RoleSettingRequest{
				RoleCode:  "OPS.ADMIN",
				RoleName:  "运营",
				RoleType:  "业务角色",
				DataScope: "本部门",
			},
			wantErr: true,
		},
		{
			name: "rejects too long role name",
			request: RoleSettingRequest{
				RoleCode:  "OPS",
				RoleName:  stringOfLength(129),
				RoleType:  "业务角色",
				DataScope: "本部门",
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := normalizeRoleSettingRequest(tt.request)
			if tt.wantErr {
				if err == nil {
					t.Fatal("expected error")
				}
				var authErr AuthError
				if !errors.As(err, &authErr) {
					t.Fatalf("error type = %T, want AuthError", err)
				}
				return
			}
			if err != nil {
				t.Fatalf("normalizeRoleSettingRequest error: %v", err)
			}
			if got.RoleCode != "OPS_ADMIN" {
				t.Fatalf("RoleCode = %q", got.RoleCode)
			}
			if got.RoleName != "运营" {
				t.Fatalf("RoleName = %q", got.RoleName)
			}
			if got.Status != "正常" {
				t.Fatalf("Status = %q", got.Status)
			}
		})
	}
}

func TestNormalizeRolePermissionItemsAddsRequiredReadAndAccessControl(t *testing.T) {
	got, err := normalizeRolePermissionItems([]RolePermissionItem{
		{Resource: "role", Action: "manage_permissions"},
	})
	if err != nil {
		t.Fatalf("normalizeRolePermissionItems error: %v", err)
	}
	want := map[string]struct{}{
		"access-control:read":     {},
		"role:manage_permissions": {},
		"role:read":               {},
	}
	if len(got) != len(want) {
		t.Fatalf("len = %d, want %d: %#v", len(got), len(want), got)
	}
	for _, item := range got {
		key := item.Resource + ":" + item.Action
		if _, ok := want[key]; !ok {
			t.Fatalf("unexpected permission %s in %#v", key, got)
		}
	}
}

func TestNewPageResponseUsesJavaContractFields(t *testing.T) {
	got := NewPageResponse([]string{"a", "b"}, 5, NormalizePageQuery(1, 2, "", ""))
	if got.TotalElements != 5 || got.TotalPages != 3 {
		t.Fatalf("unexpected totals: %+v", got)
	}
	if got.CurrentPage != 1 || got.PageSize != 2 || !got.HasMore {
		t.Fatalf("unexpected page fields: %+v", got)
	}
}

func stringOfLength(length int) string {
	value := make([]byte, length)
	for i := range value {
		value[i] = 'A'
	}
	return string(value)
}
