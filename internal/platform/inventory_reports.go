package platform

import (
	"context"
	"database/sql"
	"errors"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type InventoryReportService struct {
	db *pgxpool.Pool
}

type inventoryReportRecord struct {
	ID             int64
	MaterialCode   sql.NullString
	Brand          sql.NullString
	Material       sql.NullString
	Category       sql.NullString
	Spec           sql.NullString
	Length         sql.NullString
	WarehouseName  sql.NullString
	BatchNo        sql.NullString
	Quantity       int
	QuantityUnit   sql.NullString
	WeightTon      float64
	Unit           sql.NullString
	PieceWeightTon float64
}

func NewInventoryReportService(db *pgxpool.Pool) InventoryReportService {
	return InventoryReportService{db: db}
}

func (s InventoryReportService) Page(ctx context.Context, query PageQuery, filter InventoryReportFilter) (PageResponse[InventoryReportResponse], error) {
	if s.db == nil {
		return PageResponse[InventoryReportResponse]{}, errors.New("database client is not configured")
	}
	cte, cteArgs := inventoryReportCTE(ctx)
	where, whereArgs := inventoryReportFilters(filter, len(cteArgs))
	args := append(cteArgs, whereArgs...)
	var total int64
	if err := s.db.QueryRow(ctx, cte+"SELECT COUNT(1) FROM inventory report"+where, args...).Scan(&total); err != nil {
		return PageResponse[InventoryReportResponse]{}, err
	}
	if total == 0 {
		return NewPageResponse([]InventoryReportResponse{}, 0, query), nil
	}

	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderExpression := inventoryReportSortExpression("report", query.SortBy, query.Direction)
	pagedOrderExpression := inventoryReportSortExpression("paged", query.SortBy, query.Direction)
	rows, err := s.db.Query(ctx, cte+`
		SELECT *
		FROM (
			SELECT
				ROW_NUMBER() OVER (ORDER BY `+orderExpression+`) AS id,
				report.material_code,
				report.brand,
				report.material,
				report.category,
				report.spec,
				report.length,
				report.warehouse_name,
				report.batch_no,
				report.quantity,
				report.quantity_unit,
				report.weight_ton::float8,
				report.unit,
				COALESCE(material.piece_weight_ton, 0)::float8 AS piece_weight_ton
			FROM inventory report
			LEFT JOIN md_material material ON material.material_code = report.material_code
				AND material.deleted_flag = FALSE
			`+where+`
		) paged
		ORDER BY `+pagedOrderExpression+`
		LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[InventoryReportResponse]{}, err
	}
	defer rows.Close()

	content := []InventoryReportResponse{}
	for rows.Next() {
		record, err := scanInventoryReport(rows)
		if err != nil {
			return PageResponse[InventoryReportResponse]{}, err
		}
		content = append(content, inventoryReportResponse(record))
	}
	if err := rows.Err(); err != nil {
		return PageResponse[InventoryReportResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func inventoryReportCTE(ctx context.Context) (string, []any) {
	inboundScope, outboundScope, args := inventoryReportDataScopeClauses(ctx)
	return `
		WITH inventory AS (
			SELECT
				movement.material_code,
				movement.brand,
				movement.material,
				movement.category,
				movement.spec,
				movement.length,
				movement.warehouse_name,
				movement.batch_no,
				movement.quantity_unit,
				movement.unit,
				SUM(movement.quantity_delta) AS quantity,
				SUM(movement.weight_delta) AS weight_ton
			FROM (
				SELECT
					item.material_code,
					item.brand,
					item.material,
					item.category,
					item.spec,
					item.length,
					COALESCE(NULLIF(item.warehouse_name, ''), inbound.warehouse_name) AS warehouse_name,
					item.batch_no,
					item.quantity_unit,
					item.unit,
					item.quantity AS quantity_delta,
					item.weight_ton AS weight_delta
				FROM po_purchase_inbound inbound
				JOIN po_purchase_inbound_item item ON item.inbound_id = inbound.id
				WHERE inbound.deleted_flag = FALSE
				` + inboundScope + `
				UNION ALL
				SELECT
					item.material_code,
					item.brand,
					item.material,
					item.category,
					item.spec,
					item.length,
					outbound.warehouse_name AS warehouse_name,
					item.batch_no,
					item.quantity_unit,
					item.unit,
					-item.quantity AS quantity_delta,
					-item.weight_ton AS weight_delta
				FROM so_sales_outbound outbound
				JOIN so_sales_outbound_item item ON item.outbound_id = outbound.id
				WHERE outbound.deleted_flag = FALSE
				` + outboundScope + `
			) movement
			GROUP BY
				movement.material_code,
				movement.brand,
				movement.material,
				movement.category,
				movement.spec,
				movement.length,
				movement.warehouse_name,
				movement.batch_no,
				movement.quantity_unit,
				movement.unit
		)
	`, args
}

func inventoryReportDataScopeClauses(ctx context.Context) (string, string, []any) {
	scope, ok := CurrentDataScope(ctx)
	if !ok || normalizeDataScope(scope.Scope) == "all" {
		return "", "", nil
	}
	if len(scope.OwnerUserIDs) == 0 {
		return "AND 1 = 0", "AND 1 = 0", nil
	}
	if len(scope.OwnerUserIDs) == 1 {
		return "AND inbound.created_by = $1", "AND outbound.created_by = $1", []any{scope.OwnerUserIDs[0]}
	}
	return "AND inbound.created_by = ANY($1)", "AND outbound.created_by = ANY($1)", []any{scope.OwnerUserIDs}
}

func inventoryReportFilters(filter InventoryReportFilter, offset int) (string, []any) {
	clauses := []string{"(report.quantity > 0 OR report.weight_ton > 0)"}
	args := []any{}
	if strings.TrimSpace(filter.Keyword) != "" {
		args = append(args, likeKeyword(filter.Keyword))
		placeholder := "$" + strconvArg(offset+len(args))
		clauses = append(clauses, `(
			LOWER(COALESCE(report.material_code, '')) LIKE `+placeholder+`
			OR LOWER(COALESCE(report.brand, '')) LIKE `+placeholder+`
			OR LOWER(COALESCE(report.spec, '')) LIKE `+placeholder+`
			OR LOWER(COALESCE(report.material, '')) LIKE `+placeholder+`
		)`)
	}
	if strings.TrimSpace(filter.WarehouseName) != "" {
		args = append(args, strings.TrimSpace(filter.WarehouseName))
		clauses = append(clauses, "report.warehouse_name = $"+strconvArg(offset+len(args)))
	}
	if strings.TrimSpace(filter.Category) != "" {
		args = append(args, strings.TrimSpace(filter.Category))
		clauses = append(clauses, "report.category = $"+strconvArg(offset+len(args)))
	}
	return "\nWHERE " + strings.Join(clauses, "\n  AND ") + "\n", args
}

func inventoryReportSortExpression(alias string, sortBy string, direction string) string {
	sortDirection := "DESC"
	if strings.EqualFold(strings.TrimSpace(direction), "asc") {
		sortDirection = "ASC"
	}
	switch strings.TrimSpace(sortBy) {
	case "brand":
		return "LOWER(COALESCE(" + alias + ".brand, '')) " + sortDirection +
			", LOWER(COALESCE(" + alias + ".material_code, '')) ASC"
	case "category":
		return "LOWER(COALESCE(" + alias + ".category, '')) " + sortDirection +
			", LOWER(COALESCE(" + alias + ".material_code, '')) ASC"
	case "warehouseName":
		return "LOWER(COALESCE(" + alias + ".warehouse_name, '')) " + sortDirection +
			", LOWER(COALESCE(" + alias + ".material_code, '')) ASC"
	case "quantity":
		return alias + ".quantity " + sortDirection +
			", LOWER(COALESCE(" + alias + ".material_code, '')) ASC"
	case "weightTon":
		return alias + ".weight_ton " + sortDirection +
			", LOWER(COALESCE(" + alias + ".material_code, '')) ASC"
	default:
		return "LOWER(COALESCE(" + alias + ".material_code, '')) " + sortDirection +
			", LOWER(COALESCE(" + alias + ".warehouse_name, '')) ASC"
	}
}

type inventoryReportScanner interface {
	Scan(dest ...any) error
}

func scanInventoryReport(row inventoryReportScanner) (inventoryReportRecord, error) {
	var record inventoryReportRecord
	err := row.Scan(
		&record.ID,
		&record.MaterialCode,
		&record.Brand,
		&record.Material,
		&record.Category,
		&record.Spec,
		&record.Length,
		&record.WarehouseName,
		&record.BatchNo,
		&record.Quantity,
		&record.QuantityUnit,
		&record.WeightTon,
		&record.Unit,
		&record.PieceWeightTon,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return inventoryReportRecord{}, err
	}
	return record, err
}

func inventoryReportResponse(record inventoryReportRecord) InventoryReportResponse {
	return InventoryReportResponse{
		ID:             record.ID,
		MaterialCode:   nullableString(record.MaterialCode),
		Brand:          nullableString(record.Brand),
		Material:       nullableString(record.Material),
		Category:       nullableString(record.Category),
		Spec:           nullableString(record.Spec),
		Length:         nullableString(record.Length),
		WarehouseName:  nullableString(record.WarehouseName),
		BatchNo:        nullableString(record.BatchNo),
		Quantity:       record.Quantity,
		QuantityUnit:   nullableString(record.QuantityUnit),
		WeightTon:      record.WeightTon,
		Unit:           nullableString(record.Unit),
		PieceWeightTon: record.PieceWeightTon,
	}
}
