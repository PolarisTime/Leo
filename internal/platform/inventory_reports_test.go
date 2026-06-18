package platform

import (
	"context"
	"strings"
	"testing"
)

func TestInventoryReportFiltersBuildPredicates(t *testing.T) {
	where, args := inventoryReportFilters(InventoryReportFilter{
		Keyword:       "钢",
		WarehouseName: "一号库",
		Category:      "钢材",
	}, 1)

	if !strings.Contains(where, "(report.quantity > 0 OR report.weight_ton > 0)") ||
		!strings.Contains(where, "LOWER(COALESCE(report.material_code, '')) LIKE $2") ||
		!strings.Contains(where, "report.warehouse_name = $3") ||
		!strings.Contains(where, "report.category = $4") {
		t.Fatalf("where = %q, want inventory filters with offset placeholders", where)
	}
	if len(args) != 3 || args[0] != "%钢%" || args[1] != "一号库" || args[2] != "钢材" {
		t.Fatalf("args = %#v, want keyword, warehouse, category", args)
	}
}

func TestInventoryReportDataScopeClauses(t *testing.T) {
	ctx := ContextWithDataScope(context.Background(), DataScope{
		UserID:       7,
		Resource:     "inventory-report",
		Action:       "read",
		Scope:        "self",
		OwnerUserIDs: []int64{7},
	})

	inbound, outbound, args := inventoryReportDataScopeClauses(ctx)
	if inbound != "AND inbound.created_by = $1" || outbound != "AND outbound.created_by = $1" {
		t.Fatalf("data scope clauses = %q/%q, want inbound/outbound created_by", inbound, outbound)
	}
	if len(args) != 1 || args[0] != int64(7) {
		t.Fatalf("args = %#v, want owner 7", args)
	}
}
