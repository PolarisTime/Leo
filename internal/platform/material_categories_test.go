package platform

import "testing"

func TestNormalizeMaterialCategoryRequestDefaultsAndTrims(t *testing.T) {
	sortOrder := 3
	purchaseWeighRequired := true
	got, err := normalizeMaterialCategoryRequest(MaterialCategoryRequest{
		CategoryCode:          " REBAR ",
		CategoryName:          " 螺纹钢 ",
		SortOrder:             &sortOrder,
		PurchaseWeighRequired: &purchaseWeighRequired,
		Status:                " ",
		Remark:                " 备注 ",
	})
	if err != nil {
		t.Fatalf("normalizeMaterialCategoryRequest error: %v", err)
	}
	if got.CategoryCode != "REBAR" || got.CategoryName != "螺纹钢" || got.Status != "正常" || got.Remark != "备注" {
		t.Fatalf("unexpected normalized request: %+v", got)
	}
	if got.sortOrderValue() != 3 || !got.purchaseWeighRequiredValue() {
		t.Fatalf("unexpected defaults: sort=%d weigh=%v", got.sortOrderValue(), got.purchaseWeighRequiredValue())
	}
}

func TestNormalizeMaterialCategoryRequestDefaultZeroValues(t *testing.T) {
	got, err := normalizeMaterialCategoryRequest(MaterialCategoryRequest{
		CategoryCode: "WIRE",
		CategoryName: "线材",
	})
	if err != nil {
		t.Fatalf("normalizeMaterialCategoryRequest error: %v", err)
	}
	if got.sortOrderValue() != 0 || got.purchaseWeighRequiredValue() {
		t.Fatalf("unexpected zero defaults: sort=%d weigh=%v", got.sortOrderValue(), got.purchaseWeighRequiredValue())
	}
}

func TestNormalizeMaterialCategoryRequestRejectsInvalidInput(t *testing.T) {
	negativeSort := -1
	tooLargeSort := 10000
	tests := []struct {
		name    string
		request MaterialCategoryRequest
		want    string
	}{
		{
			name:    "missing code",
			request: MaterialCategoryRequest{CategoryName: "螺纹钢"},
			want:    "类别编码不能为空",
		},
		{
			name:    "missing name",
			request: MaterialCategoryRequest{CategoryCode: "REBAR"},
			want:    "类别名称不能为空",
		},
		{
			name:    "code too long",
			request: MaterialCategoryRequest{CategoryCode: "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567", CategoryName: "螺纹钢"},
			want:    "类别编码长度不能超过32个字符",
		},
		{
			name:    "negative sort",
			request: MaterialCategoryRequest{CategoryCode: "REBAR", CategoryName: "螺纹钢", SortOrder: &negativeSort},
			want:    "排序值不能小于0",
		},
		{
			name:    "sort too large",
			request: MaterialCategoryRequest{CategoryCode: "REBAR", CategoryName: "螺纹钢", SortOrder: &tooLargeSort},
			want:    "排序值不能超过9999",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			_, err := normalizeMaterialCategoryRequest(tt.request)
			if err == nil {
				t.Fatalf("expected error")
			}
			authErr, ok := err.(AuthError)
			if !ok {
				t.Fatalf("error type = %T, want AuthError", err)
			}
			if authErr.Message != tt.want {
				t.Fatalf("message = %q, want %q", authErr.Message, tt.want)
			}
		})
	}
}

func TestMaterialCategoryResponseUsesStringID(t *testing.T) {
	got := materialCategoryResponse(materialCategoryRecord{ID: 123, CategoryCode: "REBAR", CategoryName: "螺纹钢", Status: "正常"})
	if got.ID != "123" {
		t.Fatalf("id = %q, want 123", got.ID)
	}
}
