package platform

import (
	"context"
	"strings"
	"testing"
)

func TestIoReportFiltersValidateBusinessTypeAndDateRange(t *testing.T) {
	_, _, err := ioReportFilters(context.Background(), IoReportFilter{BusinessType: "调拨"})
	if err == nil || !strings.Contains(err.Error(), "businessType 不合法") {
		t.Fatalf("business type error = %v, want businessType 不合法", err)
	}

	_, _, err = ioReportFilters(context.Background(), IoReportFilter{
		StartDate: "2026-01-31",
		EndDate:   "2026-01-01",
	})
	if err == nil || !strings.Contains(err.Error(), "startDate 不能晚于 endDate") {
		t.Fatalf("date range error = %v, want startDate 不能晚于 endDate", err)
	}
}

func TestIoReportFiltersApplyDataScope(t *testing.T) {
	ctx := ContextWithDataScope(context.Background(), DataScope{
		UserID:       7,
		Resource:     "io-report",
		Action:       "read",
		Scope:        "self",
		OwnerUserIDs: []int64{7},
	})

	where, args, err := ioReportFilters(ctx, IoReportFilter{Keyword: "钢筋", BusinessType: "采购入库"})
	if err != nil {
		t.Fatalf("ioReportFilters returned error: %v", err)
	}
	if !strings.Contains(where, "report.created_by = $1") || !strings.Contains(where, "report.business_type = $3") {
		t.Fatalf("where = %q, want data scope and business type predicates", where)
	}
	if len(args) != 3 || args[0] != int64(7) || args[1] != "%钢筋%" || args[2] != "采购入库" {
		t.Fatalf("args = %#v, want owner, keyword, business type", args)
	}
}
