package platform

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type IoReportService struct {
	db *pgxpool.Pool
}

type ioReportRecord struct {
	ID            int64
	BusinessDate  time.Time
	BusinessType  string
	SourceNo      sql.NullString
	MaterialCode  sql.NullString
	Brand         sql.NullString
	Material      sql.NullString
	Category      sql.NullString
	Spec          sql.NullString
	Length        sql.NullString
	WarehouseName sql.NullString
	BatchNo       sql.NullString
	InQuantity    int
	OutQuantity   int
	QuantityUnit  sql.NullString
	InWeightTon   float64
	OutWeightTon  float64
	Unit          sql.NullString
	Remark        sql.NullString
}

func NewIoReportService(db *pgxpool.Pool) IoReportService {
	return IoReportService{db: db}
}

func (s IoReportService) Page(ctx context.Context, query PageQuery, filter IoReportFilter) (PageResponse[IoReportResponse], error) {
	if s.db == nil {
		return PageResponse[IoReportResponse]{}, errors.New("database client is not configured")
	}
	where, args, err := ioReportFilters(ctx, filter)
	if err != nil {
		return PageResponse[IoReportResponse]{}, err
	}
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT COUNT(1) "+ioReportFromSQL()+where, args...).Scan(&total); err != nil {
		return PageResponse[IoReportResponse]{}, err
	}
	if total == 0 {
		return NewPageResponse([]IoReportResponse{}, 0, query), nil
	}

	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderExpression := ioReportSortExpression("report", query.SortBy, query.Direction)
	pagedOrderExpression := ioReportSortExpression("paged", query.SortBy, query.Direction)
	rows, err := s.db.Query(ctx, `
		SELECT *
		FROM (
			SELECT
				ROW_NUMBER() OVER (ORDER BY `+orderExpression+`) AS id,
				report.business_date,
				report.business_type,
				report.source_no,
				report.material_code,
				report.brand,
				report.material,
				report.category,
				report.spec,
				report.length,
				report.warehouse_name,
				report.batch_no,
				report.in_quantity,
				report.out_quantity,
				report.quantity_unit,
				report.in_weight_ton::float8,
				report.out_weight_ton::float8,
				report.unit,
				report.remark
			`+ioReportFromSQL()+`
			`+where+`
		) paged
		ORDER BY `+pagedOrderExpression+`
		LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[IoReportResponse]{}, err
	}
	defer rows.Close()

	content := []IoReportResponse{}
	for rows.Next() {
		record, err := scanIoReport(rows)
		if err != nil {
			return PageResponse[IoReportResponse]{}, err
		}
		content = append(content, ioReportResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[IoReportResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func ioReportFilters(ctx context.Context, filter IoReportFilter) (string, []any, error) {
	clauses := []string{}
	args := []any{}
	scope, ok := CurrentDataScope(ctx)
	if ok && normalizeDataScope(scope.Scope) != "all" {
		if len(scope.OwnerUserIDs) == 0 {
			clauses = append(clauses, "1 = 0")
		} else if len(scope.OwnerUserIDs) == 1 {
			args = append(args, scope.OwnerUserIDs[0])
			clauses = append(clauses, "report.created_by = $"+strconvArg(len(args)))
		} else {
			args = append(args, scope.OwnerUserIDs)
			clauses = append(clauses, "report.created_by = ANY($"+strconvArg(len(args))+")")
		}
	}
	if strings.TrimSpace(filter.Keyword) != "" {
		args = append(args, likeKeyword(filter.Keyword))
		placeholder := "$" + strconvArg(len(args))
		clauses = append(clauses, `(
			LOWER(COALESCE(report.source_no, '')) LIKE `+placeholder+`
			OR LOWER(COALESCE(report.material_code, '')) LIKE `+placeholder+`
			OR LOWER(COALESCE(report.spec, '')) LIKE `+placeholder+`
			OR LOWER(COALESCE(report.brand, '')) LIKE `+placeholder+`
		)`)
	}
	if strings.TrimSpace(filter.BusinessType) != "" {
		businessType := strings.TrimSpace(filter.BusinessType)
		if businessType != "采购入库" && businessType != "销售出库" {
			return "", nil, NewAuthError(AuthErrorValidation, "businessType 不合法")
		}
		args = append(args, businessType)
		clauses = append(clauses, "report.business_type = $"+strconvArg(len(args)))
	}
	if strings.TrimSpace(filter.StartDate) != "" {
		startDate, err := parseISODate(filter.StartDate, "startDate")
		if err != nil {
			return "", nil, err
		}
		args = append(args, startDate)
		clauses = append(clauses, "report.business_date >= $"+strconvArg(len(args)))
	}
	if strings.TrimSpace(filter.EndDate) != "" {
		endDate, err := parseISODate(filter.EndDate, "endDate")
		if err != nil {
			return "", nil, err
		}
		args = append(args, endDate)
		clauses = append(clauses, "report.business_date <= $"+strconvArg(len(args)))
	}
	if strings.TrimSpace(filter.StartDate) != "" && strings.TrimSpace(filter.EndDate) != "" {
		startDate, _ := time.Parse(time.DateOnly, strings.TrimSpace(filter.StartDate))
		endDate, _ := time.Parse(time.DateOnly, strings.TrimSpace(filter.EndDate))
		if startDate.After(endDate) {
			return "", nil, NewAuthError(AuthErrorValidation, "startDate 不能晚于 endDate")
		}
	}
	if len(clauses) == 0 {
		return "", args, nil
	}
	return " WHERE " + strings.Join(clauses, " AND "), args, nil
}

func parseISODate(value string, name string) (time.Time, error) {
	parsed, err := time.Parse(time.DateOnly, strings.TrimSpace(value))
	if err != nil {
		return time.Time{}, NewAuthError(AuthErrorValidation, name+": 参数格式错误")
	}
	return parsed, nil
}

func ioReportSortExpression(alias string, sortBy string, direction string) string {
	sortDirection := "DESC"
	if strings.EqualFold(strings.TrimSpace(direction), "asc") {
		sortDirection = "ASC"
	}
	switch strings.TrimSpace(sortBy) {
	case "businessType":
		return "LOWER(COALESCE(" + alias + ".business_type, '')) " + sortDirection +
			", " + alias + ".business_date DESC, LOWER(COALESCE(" + alias + ".source_no, '')) DESC"
	case "sourceNo":
		return "LOWER(COALESCE(" + alias + ".source_no, '')) " + sortDirection +
			", " + alias + ".business_date DESC"
	case "materialCode":
		return "LOWER(COALESCE(" + alias + ".material_code, '')) " + sortDirection +
			", " + alias + ".business_date DESC"
	case "warehouseName":
		return "LOWER(COALESCE(" + alias + ".warehouse_name, '')) " + sortDirection +
			", " + alias + ".business_date DESC, LOWER(COALESCE(" + alias + ".source_no, '')) DESC"
	default:
		return alias + ".business_date " + sortDirection +
			", LOWER(COALESCE(" + alias + ".source_no, '')) DESC"
	}
}

func ioReportFromSQL() string {
	return `
		FROM (
			SELECT
				inbound.inbound_date AS business_date,
				'采购入库' AS business_type,
				inbound.inbound_no AS source_no,
				item.material_code,
				item.brand,
				item.material,
				item.category,
				item.spec,
				item.length,
				COALESCE(NULLIF(item.warehouse_name, ''), inbound.warehouse_name) AS warehouse_name,
				item.batch_no,
				item.quantity AS in_quantity,
				0 AS out_quantity,
				item.quantity_unit,
				item.weight_ton AS in_weight_ton,
				CAST(0 AS NUMERIC(14, 3)) AS out_weight_ton,
				item.unit,
				inbound.remark,
				inbound.created_by
			FROM po_purchase_inbound inbound
			JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
			WHERE inbound.deleted_flag = FALSE
			UNION ALL
			SELECT
				outbound.outbound_date AS business_date,
				'销售出库' AS business_type,
				outbound.outbound_no AS source_no,
				item.material_code,
				item.brand,
				item.material,
				item.category,
				item.spec,
				item.length,
				outbound.warehouse_name AS warehouse_name,
				item.batch_no,
				0 AS in_quantity,
				item.quantity AS out_quantity,
				item.quantity_unit,
				CAST(0 AS NUMERIC(14, 3)) AS in_weight_ton,
				item.weight_ton AS out_weight_ton,
				item.unit,
				outbound.remark,
				outbound.created_by
			FROM so_sales_outbound outbound
			JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
			WHERE outbound.deleted_flag = FALSE
		) report
	`
}

type ioReportScanner interface {
	Scan(dest ...any) error
}

func scanIoReport(row ioReportScanner) (ioReportRecord, error) {
	var record ioReportRecord
	err := row.Scan(
		&record.ID,
		&record.BusinessDate,
		&record.BusinessType,
		&record.SourceNo,
		&record.MaterialCode,
		&record.Brand,
		&record.Material,
		&record.Category,
		&record.Spec,
		&record.Length,
		&record.WarehouseName,
		&record.BatchNo,
		&record.InQuantity,
		&record.OutQuantity,
		&record.QuantityUnit,
		&record.InWeightTon,
		&record.OutWeightTon,
		&record.Unit,
		&record.Remark,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return ioReportRecord{}, err
	}
	return record, err
}

func ioReportResponse(record ioReportRecord) IoReportResponse {
	return IoReportResponse{
		ID:            record.ID,
		BusinessDate:  record.BusinessDate.Format(time.DateOnly),
		BusinessType:  record.BusinessType,
		SourceNo:      nullableString(record.SourceNo),
		MaterialCode:  nullableString(record.MaterialCode),
		Brand:         nullableString(record.Brand),
		Material:      nullableString(record.Material),
		Category:      nullableString(record.Category),
		Spec:          nullableString(record.Spec),
		Length:        nullableString(record.Length),
		WarehouseName: nullableString(record.WarehouseName),
		BatchNo:       nullableString(record.BatchNo),
		InQuantity:    record.InQuantity,
		OutQuantity:   record.OutQuantity,
		QuantityUnit:  nullableString(record.QuantityUnit),
		InWeightTon:   record.InWeightTon,
		OutWeightTon:  record.OutWeightTon,
		Unit:          nullableString(record.Unit),
		Remark:        nullableString(record.Remark),
	}
}
