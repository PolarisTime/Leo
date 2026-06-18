package platform

import (
	"context"
	"errors"
	"testing"

	"github.com/jackc/pgx/v5"
)

func TestInitializeSetupCompletesMissingParts(t *testing.T) {
	taxRate := 0.13
	tests := []struct {
		name                string
		adminConfigured     bool
		companyConfigured   bool
		request             InitialSetupSubmitRequest
		existingAdmin       string
		existingCompany     string
		wantAdmin           string
		wantCompany         string
		wantCreateAdmin     bool
		wantCreateCompany   bool
		wantOobeCompleted   bool
		wantBusinessMessage string
	}{
		{
			name:              "initialize new environment",
			request:           setupInitializeTestRequest("admin", "Leo Trading", taxRate),
			wantAdmin:         "admin",
			wantCompany:       "Leo Trading",
			wantCreateAdmin:   true,
			wantCreateCompany: true,
			wantOobeCompleted: true,
		},
		{
			name:              "complete missing admin only",
			companyConfigured: true,
			request: InitialSetupSubmitRequest{
				Admin: setupInitializeAdminRequest("admin"),
			},
			existingCompany:   "Existing Co",
			wantAdmin:         "admin",
			wantCompany:       "Existing Co",
			wantCreateAdmin:   true,
			wantOobeCompleted: true,
		},
		{
			name:            "complete missing company only",
			adminConfigured: true,
			request: InitialSetupSubmitRequest{
				Company: setupInitializeCompanyRequest("Leo Trading", taxRate),
			},
			existingAdmin:     "existing-admin",
			wantAdmin:         "existing-admin",
			wantCompany:       "Leo Trading",
			wantCreateCompany: true,
			wantOobeCompleted: true,
		},
		{
			name:                "reject when already completed",
			adminConfigured:     true,
			companyConfigured:   true,
			request:             InitialSetupSubmitRequest{},
			wantBusinessMessage: "系统已完成首次初始化",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ops := &fakeSetupInitializeOperations{
				adminConfigured:   tt.adminConfigured,
				companyConfigured: tt.companyConfigured,
				existingAdmin:     tt.existingAdmin,
				existingCompany:   tt.existingCompany,
			}

			got, err := initializeSetup(context.Background(), ops, tt.request)
			if tt.wantBusinessMessage != "" {
				if err == nil {
					t.Fatal("expected business error, got nil")
				}
				var authErr AuthError
				if !errors.As(err, &authErr) || authErr.Kind != AuthErrorBusiness || authErr.Message != tt.wantBusinessMessage {
					t.Fatalf("unexpected error: %#v", err)
				}
				if ops.txCalled {
					t.Fatal("transaction should not start when setup is already complete")
				}
				return
			}
			if err != nil {
				t.Fatalf("initializeSetup returned error: %v", err)
			}
			if got.AdminLoginName != tt.wantAdmin || got.CompanyName != tt.wantCompany {
				t.Fatalf("response = %+v, want admin=%q company=%q", got, tt.wantAdmin, tt.wantCompany)
			}
			if ops.createdAdmin != tt.wantCreateAdmin {
				t.Fatalf("createdAdmin = %v, want %v", ops.createdAdmin, tt.wantCreateAdmin)
			}
			if ops.createdCompany != tt.wantCreateCompany {
				t.Fatalf("createdCompany = %v, want %v", ops.createdCompany, tt.wantCreateCompany)
			}
			if ops.oobeCompleted != tt.wantOobeCompleted {
				t.Fatalf("oobeCompleted = %v, want %v", ops.oobeCompleted, tt.wantOobeCompleted)
			}
		})
	}
}

func setupInitializeTestRequest(loginName string, companyName string, taxRate float64) InitialSetupSubmitRequest {
	return InitialSetupSubmitRequest{
		Admin:   setupInitializeAdminRequest(loginName),
		Company: setupInitializeCompanyRequest(companyName, taxRate),
	}
}

func setupInitializeAdminRequest(loginName string) *InitialSetupAdminSubmitRequest {
	return &InitialSetupAdminSubmitRequest{
		Admin: &InitialSetupAdminRequest{
			LoginName: loginName,
			Password:  "secret123",
			UserName:  "管理员",
			Mobile:    "13800138000",
		},
		TotpCode: "123456",
	}
}

func setupInitializeCompanyRequest(companyName string, taxRate float64) *InitialSetupCompanyRequest {
	return &InitialSetupCompanyRequest{
		CompanyName: companyName,
		TaxNo:       "TAX123",
		BankName:    "Bank",
		BankAccount: "6222",
		TaxRate:     &taxRate,
	}
}

type fakeSetupInitializeOperations struct {
	adminConfigured   bool
	companyConfigured bool
	existingAdmin     string
	existingCompany   string
	createdAdmin      bool
	createdCompany    bool
	oobeCompleted     bool
	txCalled          bool
}

func (f *fakeSetupInitializeOperations) ensureReady() error {
	return nil
}

func (f *fakeSetupInitializeOperations) isAdminConfigured(context.Context) (bool, error) {
	return f.adminConfigured, nil
}

func (f *fakeSetupInitializeOperations) isCompanyConfigured(context.Context) (bool, error) {
	return f.companyConfigured, nil
}

func (f *fakeSetupInitializeOperations) prepareAdmin(_ context.Context, request *InitialSetupAdminSubmitRequest) (setupPreparedAdmin, error) {
	return setupPreparedAdmin{loginName: request.Admin.LoginName}, nil
}

func (f *fakeSetupInitializeOperations) withTx(_ context.Context, fn func(pgx.Tx) error) error {
	f.txCalled = true
	return fn(nil)
}

func (f *fakeSetupInitializeOperations) isAdminConfiguredTx(context.Context, pgx.Tx) (bool, error) {
	return f.adminConfigured, nil
}

func (f *fakeSetupInitializeOperations) isCompanyConfiguredTx(context.Context, pgx.Tx) (bool, error) {
	return f.companyConfigured, nil
}

func (f *fakeSetupInitializeOperations) createAdminTx(_ context.Context, _ pgx.Tx, admin setupPreparedAdmin) (string, error) {
	f.createdAdmin = true
	f.adminConfigured = true
	return admin.loginName, nil
}

func (f *fakeSetupInitializeOperations) createCompanyRecordTx(_ context.Context, _ pgx.Tx, request *InitialSetupCompanyRequest) (string, error) {
	f.createdCompany = true
	f.companyConfigured = true
	return request.CompanyName, nil
}

func (f *fakeSetupInitializeOperations) resolveExistingAdminLoginNameTx(context.Context, pgx.Tx) (string, error) {
	return f.existingAdmin, nil
}

func (f *fakeSetupInitializeOperations) resolveExistingCompanyNameTx(context.Context, pgx.Tx) (string, error) {
	return f.existingCompany, nil
}

func (f *fakeSetupInitializeOperations) ensureOobeCompletedSwitchTx(context.Context, pgx.Tx) error {
	f.oobeCompleted = true
	return nil
}
