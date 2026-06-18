package platform

import (
	"context"
	"database/sql"
	"strings"
	"testing"
	"time"
)

func TestPendingInvoiceReceiptReportBuildRowsFiltersKeywordAndCalculatesPending(t *testing.T) {
	sourceRows := []pendingInvoiceReceiptReportSourceRow{
		{
			OrderNo:                  "PO-001",
			SupplierName:             "华东供应商",
			OrderDate:                time.Date(2026, 1, 10, 9, 30, 0, 0, time.UTC),
			MaterialCode:             "M-001",
			Brand:                    "宝钢",
			Material:                 "螺纹钢",
			Category:                 "钢材",
			Spec:                     "HRB400",
			Length:                   sql.NullString{String: "9m", Valid: true},
			OrderQuantity:            10,
			QuantityUnit:             "件",
			OrderWeightTon:           12.5,
			UnitPrice:                3500,
			OrderAmount:              43750,
			ReceivedInvoiceWeightTon: 2.5,
			ReceivedInvoiceAmount:    8750,
		},
		{
			OrderNo:                  "PO-002",
			SupplierName:             "北方供应商",
			OrderDate:                time.Date(2026, 1, 11, 0, 0, 0, 0, time.UTC),
			MaterialCode:             "M-002",
			Brand:                    "鞍钢",
			Material:                 "角钢",
			Category:                 "型材",
			Spec:                     "50x50",
			OrderQuantity:            4,
			QuantityUnit:             "件",
			OrderWeightTon:           8,
			UnitPrice:                3200,
			OrderAmount:              25600,
			ReceivedInvoiceWeightTon: 8,
			ReceivedInvoiceAmount:    25600,
		},
		{
			OrderNo:                  "PO-003",
			SupplierName:             "南方供应商",
			OrderDate:                time.Date(2026, 1, 12, 0, 0, 0, 0, time.UTC),
			MaterialCode:             "M-003",
			Brand:                    "沙钢",
			Material:                 "盘螺",
			Category:                 "钢材",
			Spec:                     "HRB500",
			OrderQuantity:            6,
			QuantityUnit:             "件",
			OrderWeightTon:           6,
			UnitPrice:                3600,
			OrderAmount:              21600,
			ReceivedInvoiceWeightTon: 1,
			ReceivedInvoiceAmount:    3600,
		},
	}

	rows := buildPendingInvoiceReceiptReportRows(sourceRows, "宝钢")
	if len(rows) != 1 {
		t.Fatalf("rows length = %d, want 1: %+v", len(rows), rows)
	}
	row := rows[0]
	if row.ID != 1 || row.OrderNo != "PO-001" || row.InvoiceTitle != "华东供应商" || row.Length != "9m" {
		t.Fatalf("unexpected row identity fields: %+v", row)
	}
	if row.OrderDate != "2026-01-10T09:30:00" || row.PendingInvoiceWeightTon != 10 || row.PendingInvoiceAmount != 35000 {
		t.Fatalf("unexpected row calculated fields: %+v", row)
	}
	if row.Status != "未收票" {
		t.Fatalf("status = %q, want 未收票", row.Status)
	}

	rows = buildPendingInvoiceReceiptReportRows(sourceRows, "沙钢")
	if len(rows) != 1 || rows[0].ID != 2 || rows[0].OrderNo != "PO-003" {
		t.Fatalf("keyword source index rows = %+v, want PO-003 with source id 2", rows)
	}
}

func TestPendingInvoiceReceiptReportSortAndPage(t *testing.T) {
	rows := []PendingInvoiceReceiptReportResponse{
		{ID: 1, OrderNo: "PO-002", PendingInvoiceAmount: 20, PendingInvoiceWeightTon: 2},
		{ID: 2, OrderNo: "PO-003", PendingInvoiceAmount: 30, PendingInvoiceWeightTon: 3},
		{ID: 3, OrderNo: "PO-001", PendingInvoiceAmount: 10, PendingInvoiceWeightTon: 1},
	}

	sortPendingInvoiceReceiptReportRows(rows, "pendingInvoiceAmount", "asc")
	if rows[0].OrderNo != "PO-001" || rows[2].OrderNo != "PO-003" {
		t.Fatalf("amount asc order = %+v, want PO-001 first and PO-003 last", rows)
	}

	sortPendingInvoiceReceiptReportRows(rows, "unsupported", "desc")
	if rows[0].OrderNo != "PO-003" || rows[2].OrderNo != "PO-001" {
		t.Fatalf("fallback orderNo desc order = %+v, want PO-003 first and PO-001 last", rows)
	}

	page := pagePendingInvoiceReceiptReportRows(rows, PageQuery{Page: 1, Size: 1})
	if page.TotalElements != 3 || page.TotalPages != 3 || len(page.Content) != 1 || page.Content[0].OrderNo != "PO-002" {
		t.Fatalf("page = %+v, want second item with totals", page)
	}

	page = pagePendingInvoiceReceiptReportRows(rows, PageQuery{Page: -1, Size: 0})
	if page.CurrentPage != 0 || page.PageSize != 20 || page.TotalElements != 3 || len(page.Content) != 3 {
		t.Fatalf("normalized page = %+v, want page 0 size 20 with all content", page)
	}
}

func TestPendingInvoiceReceiptReportFiltersApplyDataScopeAndDates(t *testing.T) {
	ctx := ContextWithDataScope(context.Background(), DataScope{
		UserID:       7,
		Resource:     "pending-invoice-receipt-report",
		Action:       "read",
		Scope:        "self",
		OwnerUserIDs: []int64{7},
	})

	where, args, err := pendingInvoiceReceiptReportFilters(ctx, PendingInvoiceReceiptReportFilter{
		SupplierName: "华东供应商",
		StartDate:    "2026-01-01",
		EndDate:      "2026-01-31",
	})
	if err != nil {
		t.Fatalf("pendingInvoiceReceiptReportFilters returned error: %v", err)
	}
	if !strings.Contains(where, "o.created_by = $1") ||
		!strings.Contains(where, "o.supplier_name = $2") ||
		!strings.Contains(where, "o.order_date >= $3") ||
		!strings.Contains(where, "o.order_date <= $4") {
		t.Fatalf("where = %q, want data scope, supplier and date predicates", where)
	}
	if len(args) != 4 || args[0] != int64(7) || args[1] != "华东供应商" {
		t.Fatalf("args = %#v, want owner, supplier, start date, end date", args)
	}

	_, _, err = pendingInvoiceReceiptReportFilters(context.Background(), PendingInvoiceReceiptReportFilter{StartDate: "2026/01/01"})
	if err == nil || !strings.Contains(err.Error(), "startDate: 参数格式错误") {
		t.Fatalf("date parse error = %v, want startDate format error", err)
	}
}
