package platform

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type CarrierService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

type carrierRecord struct {
	ID           int64
	CarrierCode  string
	CarrierName  string
	ContactName  sql.NullString
	ContactPhone sql.NullString
	VehicleType  sql.NullString
	PriceMode    sql.NullString
	Status       string
	Remark       sql.NullString
	Vehicles     []vehicleRecord
	CreatedBy    int64
}

type vehicleRecord struct {
	ID      int64
	Plate   string
	Contact sql.NullString
	Phone   sql.NullString
	Remark  sql.NullString
}

type carrierReferenceCheck struct {
	table          string
	column         string
	value          string
	extraCondition string
	extraArgs      []any
}

func NewCarrierService(db *pgxpool.Pool, machineID int64) CarrierService {
	return CarrierService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s CarrierService) Options(ctx context.Context) ([]CarrierOptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT carrier.id, carrier.carrier_name, vehicle.plate
		  FROM md_carrier carrier
		  LEFT JOIN md_vehicle vehicle
		    ON vehicle.carrier_id = carrier.id
		   AND vehicle.deleted_flag = false
		 WHERE carrier.deleted_flag = false
		   AND carrier.status = '正常'
		 ORDER BY carrier.carrier_code ASC, vehicle.sort_order ASC, vehicle.id ASC
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	ordered := []int64{}
	byID := map[int64]*CarrierOptionResponse{}
	for rows.Next() {
		var id int64
		var carrierName string
		var plate sql.NullString
		if err := rows.Scan(&id, &carrierName, &plate); err != nil {
			return nil, err
		}
		option := byID[id]
		if option == nil {
			ordered = append(ordered, id)
			option = &CarrierOptionResponse{
				ID:            id,
				Label:         carrierName,
				Value:         carrierName,
				VehiclePlates: []string{},
			}
			byID[id] = option
		}
		if plate.Valid {
			option.VehiclePlates = append(option.VehiclePlates, plate.String)
		}
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	result := make([]CarrierOptionResponse, 0, len(ordered))
	for _, id := range ordered {
		result = append(result, *byID[id])
	}
	return result, nil
}

func (s CarrierService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[CarrierResponse], error) {
	if s.db == nil {
		return PageResponse[CarrierResponse]{}, errors.New("database client is not configured")
	}
	where, args := carrierFilters(keyword, status)
	where, args = applyDataScopeFilter(ctx, where, args, "created_by")
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM md_carrier WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[CarrierResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"id":           "id",
		"carrierCode":  "carrier_code",
		"carrierName":  "carrier_name",
		"contactName":  "contact_name",
		"contactPhone": "contact_phone",
		"vehicleType":  "vehicle_type",
		"priceMode":    "price_mode",
		"status":       "status",
	}, "id")
	rows, err := s.db.Query(ctx, `
		SELECT id, carrier_code, carrier_name, contact_name,
		       contact_phone, vehicle_type, price_mode, status, remark, created_by
		  FROM md_carrier
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[CarrierResponse]{}, err
	}
	defer rows.Close()

	records := []carrierRecord{}
	for rows.Next() {
		record, err := scanCarrier(rows)
		if err != nil {
			return PageResponse[CarrierResponse]{}, err
		}
		records = append(records, record)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[CarrierResponse]{}, err
	}
	if err := s.fillCarrierVehicles(ctx, records); err != nil {
		return PageResponse[CarrierResponse]{}, err
	}
	content := make([]CarrierResponse, 0, len(records))
	for _, record := range records {
		content = append(content, carrierResponse(record))
	}
	return NewPageResponse(content, total, query), nil
}

func (s CarrierService) Detail(ctx context.Context, id int64) (CarrierResponse, error) {
	if s.db == nil {
		return CarrierResponse{}, errors.New("database client is not configured")
	}
	record, err := s.detail(ctx, id)
	if err != nil {
		return CarrierResponse{}, err
	}
	return carrierResponse(record), nil
}

func (s CarrierService) Create(ctx context.Context, request CarrierRequest) (CarrierResponse, error) {
	if s.db == nil {
		return CarrierResponse{}, errors.New("database client is not configured")
	}
	if err := validateCarrierRequest(request); err != nil {
		return CarrierResponse{}, err
	}
	if err := s.ensureCarrierCodeAvailable(ctx, request.CarrierCode, 0); err != nil {
		return CarrierResponse{}, err
	}
	id := s.idGenerator.Next()
	createdBy := auditUserID(ctx)
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return CarrierResponse{}, err
	}
	defer tx.Rollback(ctx)

	_, err = tx.Exec(ctx, `
		INSERT INTO md_carrier (
			id, carrier_code, carrier_name, contact_name, contact_phone,
			vehicle_type, price_mode, status, remark, created_by, created_name,
			updated_by, updated_name, updated_at, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, 'system', $10, 'system', CURRENT_TIMESTAMP, false)
	`, id, request.CarrierCode, request.CarrierName, nullableTextParam(request.ContactName),
		nullableTextParam(request.ContactPhone), nullableTextParam(request.VehicleType),
		nullableTextParam(request.PriceMode), request.Status, nullableTextParam(request.Remark), createdBy)
	if err != nil {
		if isUniqueViolation(err) {
			return CarrierResponse{}, NewAuthError(AuthErrorBusiness, "物流方编码已存在")
		}
		return CarrierResponse{}, err
	}
	if err := s.replaceCarrierVehicles(ctx, tx, id, request.Vehicles); err != nil {
		return CarrierResponse{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return CarrierResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s CarrierService) Update(ctx context.Context, id int64, request CarrierRequest) (CarrierResponse, error) {
	if s.db == nil {
		return CarrierResponse{}, errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return CarrierResponse{}, err
	}
	if err := validateCarrierRequest(request); err != nil {
		return CarrierResponse{}, err
	}
	if current.CarrierCode != request.CarrierCode {
		if err := s.ensureCarrierCodeAvailable(ctx, request.CarrierCode, id); err != nil {
			return CarrierResponse{}, err
		}
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return CarrierResponse{}, err
	}
	defer tx.Rollback(ctx)
	commandTag, err := tx.Exec(ctx, `
		UPDATE md_carrier
		   SET carrier_code = $2,
		       carrier_name = $3,
		       contact_name = $4,
		       contact_phone = $5,
		       vehicle_type = $6,
		       price_mode = $7,
		       status = $8,
		       remark = $9,
		       updated_by = $10,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, request.CarrierCode, request.CarrierName, nullableTextParam(request.ContactName),
		nullableTextParam(request.ContactPhone), nullableTextParam(request.VehicleType),
		nullableTextParam(request.PriceMode), request.Status, nullableTextParam(request.Remark), auditUserID(ctx))
	if err != nil {
		if isUniqueViolation(err) {
			return CarrierResponse{}, NewAuthError(AuthErrorBusiness, "物流方编码已存在")
		}
		return CarrierResponse{}, err
	}
	if commandTag.RowsAffected() == 0 {
		return CarrierResponse{}, NewAuthError(AuthErrorNotFound, "物流方不存在")
	}
	if err := s.replaceCarrierVehicles(ctx, tx, id, request.Vehicles); err != nil {
		return CarrierResponse{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return CarrierResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s CarrierService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	current, err := s.detail(ctx, id)
	if err != nil {
		return err
	}
	if err := s.assertCarrierUnused(ctx, current); err != nil {
		return err
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return err
	}
	defer tx.Rollback(ctx)
	commandTag, err := tx.Exec(ctx, `
		UPDATE md_carrier
		   SET deleted_flag = true,
		       updated_by = $2,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, auditUserID(ctx))
	if err != nil {
		return err
	}
	if commandTag.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "物流方不存在")
	}
	if _, err := tx.Exec(ctx, `
		UPDATE md_vehicle
		   SET deleted_flag = true,
		       updated_by = $2,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE carrier_id = $1
		   AND deleted_flag = false
	`, id, auditUserID(ctx)); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func (s CarrierService) detail(ctx context.Context, id int64) (carrierRecord, error) {
	var record carrierRecord
	err := s.db.QueryRow(ctx, `
		SELECT id, carrier_code, carrier_name, contact_name,
		       contact_phone, vehicle_type, price_mode, status, remark, created_by
		  FROM md_carrier
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&record.ID,
		&record.CarrierCode,
		&record.CarrierName,
		&record.ContactName,
		&record.ContactPhone,
		&record.VehicleType,
		&record.PriceMode,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return carrierRecord{}, NewAuthError(AuthErrorNotFound, "物流方不存在")
	}
	if err != nil {
		return carrierRecord{}, err
	}
	if err := assertDataScopeAccess(ctx, record.CreatedBy); err != nil {
		return carrierRecord{}, err
	}
	records := []carrierRecord{record}
	if err := s.fillCarrierVehicles(ctx, records); err != nil {
		return carrierRecord{}, err
	}
	return records[0], nil
}

func (s CarrierService) ensureCarrierCodeAvailable(ctx context.Context, carrierCode string, excludeID int64) error {
	query := `
		SELECT EXISTS (
			SELECT 1
			  FROM md_carrier
			 WHERE carrier_code = $1
			   AND deleted_flag = false
	`
	args := []any{carrierCode}
	if excludeID > 0 {
		query += " AND id <> $2"
		args = append(args, excludeID)
	}
	query += ")"
	var exists bool
	if err := s.db.QueryRow(ctx, query, args...).Scan(&exists); err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "物流方编码已存在")
	}
	return nil
}

func (s CarrierService) replaceCarrierVehicles(ctx context.Context, tx pgx.Tx, carrierID int64, vehicles []VehicleItem) error {
	if _, err := tx.Exec(ctx, "DELETE FROM md_vehicle WHERE carrier_id = $1", carrierID); err != nil {
		return err
	}
	for index, item := range vehicles {
		plate := strings.TrimSpace(item.Plate)
		if plate == "" {
			continue
		}
		_, err := tx.Exec(ctx, `
			INSERT INTO md_vehicle (
				id, carrier_id, plate, contact, phone, remark, sort_order,
				created_by, created_name, updated_by, updated_name, updated_at, deleted_flag
			) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, 'system', $8, 'system', CURRENT_TIMESTAMP, false)
		`, s.idGenerator.Next(), carrierID, plate, nullableTextParam(item.Contact),
			nullableTextParam(item.Phone), nullableTextParam(item.Remark), index, auditUserID(ctx))
		if err != nil {
			return err
		}
	}
	return nil
}

func (s CarrierService) fillCarrierVehicles(ctx context.Context, records []carrierRecord) error {
	if len(records) == 0 {
		return nil
	}
	byID := map[int64]int{}
	ids := make([]int64, 0, len(records))
	for index := range records {
		byID[records[index].ID] = index
		ids = append(ids, records[index].ID)
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, carrier_id, plate, contact, phone, remark
		  FROM md_vehicle
		 WHERE carrier_id = ANY($1)
		   AND deleted_flag = false
		 ORDER BY carrier_id ASC, sort_order ASC, id ASC
	`, ids)
	if err != nil {
		if isUndefinedTable(err) || isUndefinedColumn(err) {
			return nil
		}
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var carrierID int64
		var vehicle vehicleRecord
		if err := rows.Scan(&vehicle.ID, &carrierID, &vehicle.Plate, &vehicle.Contact, &vehicle.Phone, &vehicle.Remark); err != nil {
			return err
		}
		if index, ok := byID[carrierID]; ok {
			records[index].Vehicles = append(records[index].Vehicles, vehicle)
		}
	}
	return rows.Err()
}

func (s CarrierService) assertCarrierUnused(ctx context.Context, record carrierRecord) error {
	for _, reference := range carrierReferences(record) {
		if strings.TrimSpace(reference.value) == "" {
			continue
		}
		query := fmt.Sprintf(
			"SELECT count(*) FROM %s WHERE deleted_flag = false AND %s = $1",
			reference.table,
			reference.column,
		)
		args := []any{reference.value}
		if strings.TrimSpace(reference.extraCondition) != "" {
			query += " AND " + reference.extraCondition
			args = append(args, reference.extraArgs...)
		}
		var count int64
		if err := s.db.QueryRow(ctx, query, args...).Scan(&count); err != nil {
			if isUndefinedTable(err) || isUndefinedColumn(err) {
				continue
			}
			return err
		}
		if count > 0 {
			return NewAuthError(
				AuthErrorBusiness,
				fmt.Sprintf("该物流商已被业务或主数据引用，不能删除（%s.%s 中有 %d 条记录）", reference.table, reference.column, count),
			)
		}
	}
	return nil
}

func carrierReferences(record carrierRecord) []carrierReferenceCheck {
	return []carrierReferenceCheck{
		{table: "st_freight_statement", column: "carrier_code", value: record.CarrierCode},
		{table: "fm_payment", column: "counterparty_code", value: record.CarrierCode, extraCondition: "business_type IN ($2, $3)", extraArgs: []any{"物流商", "物流付款"}},
		{table: "fm_ledger_adjustment", column: "counterparty_code", value: record.CarrierCode, extraCondition: "counterparty_type = $2", extraArgs: []any{"物流商"}},
		{table: "lg_freight_bill", column: "carrier_name", value: record.CarrierName},
		{table: "st_freight_statement", column: "carrier_name", value: record.CarrierName, extraCondition: "(carrier_code IS NULL OR BTRIM(carrier_code) = '')"},
		{table: "fm_payment", column: "counterparty_name", value: record.CarrierName, extraCondition: "business_type IN ($2, $3) AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')", extraArgs: []any{"物流商", "物流付款"}},
		{table: "fm_ledger_adjustment", column: "counterparty_name", value: record.CarrierName, extraCondition: "counterparty_type = $2 AND (counterparty_code IS NULL OR BTRIM(counterparty_code) = '')", extraArgs: []any{"物流商"}},
	}
}

func carrierFilters(keyword string, status string) (string, []any) {
	where := "deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(keyword)+"%")
		where += " AND (carrier_code LIKE $" + strconvArg(len(args)) +
			" OR carrier_name LIKE $" + strconvArg(len(args)) +
			" OR contact_name LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		args = append(args, strings.TrimSpace(status))
		where += " AND status = $" + strconvArg(len(args))
	}
	return where, args
}

func validateCarrierRequest(request CarrierRequest) error {
	if strings.TrimSpace(request.CarrierCode) == "" {
		return NewAuthError(AuthErrorValidation, "物流方编码不能为空")
	}
	if strings.TrimSpace(request.CarrierName) == "" {
		return NewAuthError(AuthErrorValidation, "物流方名称不能为空")
	}
	if strings.TrimSpace(request.Status) == "" {
		return NewAuthError(AuthErrorValidation, "状态不能为空")
	}
	return nil
}

func carrierResponse(record carrierRecord) CarrierResponse {
	return CarrierResponse{
		ID:           record.ID,
		CarrierCode:  record.CarrierCode,
		CarrierName:  record.CarrierName,
		ContactName:  nullableStringPointer(record.ContactName),
		ContactPhone: nullableStringPointer(record.ContactPhone),
		VehicleType:  nullableStringPointer(record.VehicleType),
		Vehicles:     vehicleResponses(record.Vehicles),
		PriceMode:    nullableStringPointer(record.PriceMode),
		Status:       record.Status,
		Remark:       nullableStringPointer(record.Remark),
	}
}

func vehicleResponses(records []vehicleRecord) []VehicleInfo {
	result := make([]VehicleInfo, 0, len(records))
	for _, record := range records {
		result = append(result, VehicleInfo{
			ID:      record.ID,
			Plate:   record.Plate,
			Contact: nullableStringPointer(record.Contact),
			Phone:   maskPhonePointer(record.Phone),
			Remark:  nullableStringPointer(record.Remark),
		})
	}
	return result
}

func maskPhonePointer(value sql.NullString) *string {
	if !value.Valid {
		return nil
	}
	result := maskPhone(value.String)
	return &result
}

func maskPhone(value string) string {
	if strings.TrimSpace(value) == "" {
		return value
	}
	trimmed := strings.TrimSpace(value)
	if len([]rune(trimmed)) <= 4 {
		return trimmed
	}
	runes := []rune(trimmed)
	if len(runes) <= 7 {
		return string(runes[:3]) + "****"
	}
	return string(runes[:3]) + "****" + string(runes[len(runes)-4:])
}

type carrierScanner interface {
	Scan(dest ...any) error
}

func scanCarrier(row carrierScanner) (carrierRecord, error) {
	var record carrierRecord
	err := row.Scan(
		&record.ID,
		&record.CarrierCode,
		&record.CarrierName,
		&record.ContactName,
		&record.ContactPhone,
		&record.VehicleType,
		&record.PriceMode,
		&record.Status,
		&record.Remark,
		&record.CreatedBy,
	)
	return record, err
}
