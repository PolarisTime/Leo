package platform

import (
	"context"
	"database/sql"
	"errors"
	"sort"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const pendingInvoiceReceiptReportDateTimeLayout = "2006-01-02T15:04:05"

type PendingInvoiceReceiptReportService struct {
	db *pgxpool.Pool
}

type pendingInvoiceReceiptReportSourceRow struct {
	OrderNo                  string
	SupplierName             string
	OrderDate                time.Time
	MaterialCode             string
	Brand                    string
	Material                 string
	Category                 string
	Spec                     string
	Length                   sql.NullString
	OrderQuantity            int
	QuantityUnit             string
	OrderWeightTon           float64
	UnitPrice                float64
	OrderAmount              float64
	ReceivedInvoiceWeightTon float64
	ReceivedInvoiceAmount    float64
}

func NewPendingInvoiceReceiptReportService(db *pgxpool.Pool) PendingInvoiceReceiptReportService {
	return PendingInvoiceReceiptReportService{db: db}
}

func (s PendingInvoiceReceiptReportService) Page(ctx context.Context, query PageQuery, filter PendingInvoiceReceiptReportFilter) (PageResponse[PendingInvoiceReceiptReportResponse], error) {
	if s.db == nil {
		return PageResponse[PendingInvoiceReceiptReportResponse]{}, errors.New("database client is not configured")
	}
	rows, err := s.loadSourceRows(ctx, filter)
	if err != nil {
		return PageResponse[PendingInvoiceReceiptReportResponse]{}, err
	}
	content := buildPendingInvoiceReceiptReportRows(rows, filter.Keyword)
	sortPendingInvoiceReceiptReportRows(content, query.SortBy, query.Direction)
	return pagePendingInvoiceReceiptReportRows(content, query), nil
}

func (s PendingInvoiceReceiptReportService) loadSourceRows(ctx context.Context, filter PendingInvoiceReceiptReportFilter) ([]pendingInvoiceReceiptReportSourceRow, error) {
	where, args, err := pendingInvoiceReceiptReportFilters(ctx, filter)
	if err != nil {
		return nil, err
	}
	rows, err := s.db.Query(ctx, `
		SELECT
			o.order_no,
			o.supplier_name,
			o.order_date,
			i.material_code,
			i.brand,
			i.material,
			i.category,
			i.spec,
			i.length,
			i.quantity,
			i.quantity_unit,
			i.weight_ton::float8,
			i.unit_price::float8,
			i.amount::float8,
			COALESCE(progress.total_weight_ton, 0)::float8,
			COALESCE(progress.total_amount, 0)::float8
		FROM po_purchase_order o
		JOIN po_purchase_order_item i ON i.order_id = o.id
		LEFT JOIN (
			SELECT
				item.source_purchase_order_item_id,
				COALESCE(SUM(item.weight_ton), 0)::float8 AS total_weight_ton,
				COALESCE(SUM(item.amount), 0)::float8 AS total_amount
			FROM fm_invoice_receipt receipt
			JOIN fm_invoice_receipt_item item ON item.receipt_id = receipt.id
			WHERE receipt.deleted_flag = FALSE
			GROUP BY item.source_purchase_order_item_id
		) progress ON progress.source_purchase_order_item_id = i.id
		`+where+`
		ORDER BY o.id ASC, i.line_no ASC, i.id ASC
	`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := []pendingInvoiceReceiptReportSourceRow{}
	for rows.Next() {
		record, err := scanPendingInvoiceReceiptReportSourceRow(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, record)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return result, nil
}

func pendingInvoiceReceiptReportFilters(ctx context.Context, filter PendingInvoiceReceiptReportFilter) (string, []any, error) {
	where := "WHERE o.deleted_flag = false"
	args := []any{}
	where, args = applyDataScopeFilter(ctx, where, args, "o.created_by")
	if strings.TrimSpace(filter.SupplierName) != "" {
		args = append(args, strings.TrimSpace(filter.SupplierName))
		where += " AND o.supplier_name = $" + strconvArg(len(args))
	}
	if strings.TrimSpace(filter.StartDate) != "" {
		startDate, err := parsePendingInvoiceReceiptDate(filter.StartDate, "startDate")
		if err != nil {
			return "", nil, err
		}
		args = append(args, startDate)
		where += " AND o.order_date >= $" + strconvArg(len(args))
	}
	if strings.TrimSpace(filter.EndDate) != "" {
		endDate, err := parsePendingInvoiceReceiptDate(filter.EndDate, "endDate")
		if err != nil {
			return "", nil, err
		}
		args = append(args, endDate)
		where += " AND o.order_date <= $" + strconvArg(len(args))
	}
	return where, args, nil
}

func parsePendingInvoiceReceiptDate(value string, name string) (time.Time, error) {
	parsed, err := time.ParseInLocation(time.DateOnly, strings.TrimSpace(value), time.UTC)
	if err != nil {
		return time.Time{}, NewAuthError(AuthErrorValidation, name+": 参数格式错误")
	}
	return parsed, nil
}

func buildPendingInvoiceReceiptReportRows(sourceRows []pendingInvoiceReceiptReportSourceRow, keyword string) []PendingInvoiceReceiptReportResponse {
	normalizedKeyword := strings.TrimSpace(keyword)
	result := make([]PendingInvoiceReceiptReportResponse, 0, len(sourceRows))
	index := int64(1)
	for _, src := range sourceRows {
		pendingWeightTon := positiveOrZeroFloat(src.OrderWeightTon - src.ReceivedInvoiceWeightTon)
		pendingAmount := positiveOrZeroFloat(src.OrderAmount - src.ReceivedInvoiceAmount)
		if pendingWeightTon <= 0 && pendingAmount <= 0 {
			continue
		}
		row := PendingInvoiceReceiptReportResponse{
			ID:                       index,
			OrderNo:                  src.OrderNo,
			SupplierName:             src.SupplierName,
			InvoiceTitle:             src.SupplierName,
			OrderDate:                src.OrderDate.Format(pendingInvoiceReceiptReportDateTimeLayout),
			MaterialCode:             src.MaterialCode,
			Brand:                    src.Brand,
			Material:                 src.Material,
			Category:                 src.Category,
			Spec:                     src.Spec,
			Length:                   nullableString(src.Length),
			OrderQuantity:            src.OrderQuantity,
			QuantityUnit:             src.QuantityUnit,
			OrderWeightTon:           src.OrderWeightTon,
			ReceivedInvoiceWeightTon: src.ReceivedInvoiceWeightTon,
			PendingInvoiceWeightTon:  pendingWeightTon,
			UnitPrice:                src.UnitPrice,
			OrderAmount:              src.OrderAmount,
			ReceivedInvoiceAmount:    src.ReceivedInvoiceAmount,
			PendingInvoiceAmount:     pendingAmount,
			Status:                   "未收票",
		}
		index++
		if pendingInvoiceReceiptReportMatchesKeyword(row, normalizedKeyword) {
			result = append(result, row)
		}
	}
	return result
}

func pendingInvoiceReceiptReportMatchesKeyword(row PendingInvoiceReceiptReportResponse, keyword string) bool {
	if strings.TrimSpace(keyword) == "" {
		return true
	}
	return containsLower(row.OrderNo, keyword) ||
		containsLower(row.SupplierName, keyword) ||
		containsLower(row.InvoiceTitle, keyword) ||
		containsLower(row.MaterialCode, keyword) ||
		containsLower(row.Brand, keyword) ||
		containsLower(row.Material, keyword) ||
		containsLower(row.Category, keyword) ||
		containsLower(row.Spec, keyword)
}

func sortPendingInvoiceReceiptReportRows(rows []PendingInvoiceReceiptReportResponse, sortBy string, direction string) {
	less := pendingInvoiceReceiptReportLess(rows, sortBy, direction)
	sort.SliceStable(rows, less)
}

func pendingInvoiceReceiptReportLess(rows []PendingInvoiceReceiptReportResponse, sortBy string, direction string) func(i, j int) bool {
	asc := strings.EqualFold(strings.TrimSpace(direction), "asc")
	compareString := func(a, b string) int {
		a = strings.ToLower(strings.TrimSpace(a))
		b = strings.ToLower(strings.TrimSpace(b))
		switch {
		case a < b:
			return -1
		case a > b:
			return 1
		default:
			return 0
		}
	}
	compareFloat := func(a, b float64) int {
		switch {
		case a < b:
			return -1
		case a > b:
			return 1
		default:
			return 0
		}
	}
	compareTime := func(a, b string) int {
		switch {
		case a < b:
			return -1
		case a > b:
			return 1
		default:
			return 0
		}
	}
	return func(i, j int) bool {
		var cmp int
		switch strings.TrimSpace(sortBy) {
		case "supplierName":
			cmp = compareString(rows[i].SupplierName, rows[j].SupplierName)
		case "orderDate":
			cmp = compareTime(rows[i].OrderDate, rows[j].OrderDate)
		case "materialCode":
			cmp = compareString(rows[i].MaterialCode, rows[j].MaterialCode)
		case "pendingInvoiceWeightTon":
			cmp = compareFloat(rows[i].PendingInvoiceWeightTon, rows[j].PendingInvoiceWeightTon)
		case "pendingInvoiceAmount":
			cmp = compareFloat(rows[i].PendingInvoiceAmount, rows[j].PendingInvoiceAmount)
		default:
			cmp = compareString(rows[i].OrderNo, rows[j].OrderNo)
		}
		if asc {
			return cmp < 0
		}
		return cmp > 0
	}
}

func pagePendingInvoiceReceiptReportRows(rows []PendingInvoiceReceiptReportResponse, query PageQuery) PageResponse[PendingInvoiceReceiptReportResponse] {
	query = NormalizePageQuery(query.Page, query.Size, query.SortBy, query.Direction)
	start := query.Page * query.Size
	if start > len(rows) {
		start = len(rows)
	}
	end := start + query.Size
	if end > len(rows) {
		end = len(rows)
	}
	return NewPageResponse(rows[start:end], int64(len(rows)), query)
}

func positiveOrZeroFloat(value float64) float64 {
	if value > 0 {
		return value
	}
	return 0
}

type pendingInvoiceReceiptReportRowScanner interface {
	Scan(dest ...any) error
}

func scanPendingInvoiceReceiptReportSourceRow(row pendingInvoiceReceiptReportRowScanner) (pendingInvoiceReceiptReportSourceRow, error) {
	var record pendingInvoiceReceiptReportSourceRow
	err := row.Scan(
		&record.OrderNo,
		&record.SupplierName,
		&record.OrderDate,
		&record.MaterialCode,
		&record.Brand,
		&record.Material,
		&record.Category,
		&record.Spec,
		&record.Length,
		&record.OrderQuantity,
		&record.QuantityUnit,
		&record.OrderWeightTon,
		&record.UnitPrice,
		&record.OrderAmount,
		&record.ReceivedInvoiceWeightTon,
		&record.ReceivedInvoiceAmount,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return pendingInvoiceReceiptReportSourceRow{}, err
	}
	return record, err
}
