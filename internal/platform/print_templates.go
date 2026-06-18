package platform

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	printTemplateTypeCoord    = "COORD"
	printTemplateTypePDFForm  = "PDF_FORM"
	printTemplateEngineLODOP  = "LODOP"
	printTemplateStatusActive = "ACTIVE"
	printTemplateSyncManual   = "MANUAL"
	printTemplateSyncFile     = "FILE"

	MaxPrintTemplateUploadJSONBytes = 1024 * 1024
	defaultPDFFormLayout            = "src/main/resources/print-forms/yingjie-a4-remark.layout.json"
	defaultPurchaseLayout           = "src/main/resources/print-forms/default-purchase.layout.json"
	defaultSalesLayout              = "src/main/resources/print-forms/default-sales.layout.json"
	defaultLogisticsLayout          = "src/main/resources/print-forms/default-logistics.layout.json"
	defaultStatementLayout          = "src/main/resources/print-forms/default-statement.layout.json"
)

var printTemplateJavaZone = time.FixedZone("Asia/Shanghai", 8*60*60)

var (
	allowedPrintTemplateBillTypes = map[string]struct{}{
		"purchase-order": {}, "purchase-inbound": {}, "sales-order": {}, "sales-outbound": {},
		"freight-bill": {}, "purchase-contract": {}, "sales-contract": {},
		"supplier-statement": {}, "customer-statement": {}, "freight-statement": {},
		"receipt": {}, "payment": {}, "invoice-receipt": {}, "invoice-issue": {},
	}
	allowedPrintTemplateTypes = map[string]struct{}{
		printTemplateTypeCoord: {}, printTemplateTypePDFForm: {},
	}
	allowedPrintTemplateEngines = map[string]struct{}{
		printTemplateEngineLODOP: {}, printTemplateTypePDFForm: {},
	}
	allowedPrintTemplateStatuses = map[string]struct{}{
		printTemplateStatusActive: {}, "DISABLED": {},
	}
	dangerousLODOPPatterns = []*regexp.Regexp{
		regexp.MustCompile(`(?i)\b(eval|Function)\s*\(`),
		regexp.MustCompile(`(?i)\b(window|document|localStorage|sessionStorage|location|history|navigator)\b`),
		regexp.MustCompile(`(?i)\b(fetch|XMLHttpRequest|WebSocket)\b`),
	}
)

type PrintTemplateService struct {
	store       printTemplateStore
	idGenerator *IDGenerator
}

type printTemplateStore interface {
	listByBillType(ctx context.Context, billType string) ([]printTemplateRecord, error)
	findByID(ctx context.Context, id int64) (printTemplateRecord, error)
	existsByBillTypeAndTemplateName(ctx context.Context, billType string, templateName string, excludeID int64) (bool, error)
	existsByBillTypeAndTemplateCode(ctx context.Context, billType string, templateCode string, excludeID int64) (bool, error)
	insert(ctx context.Context, record printTemplateRecord) error
	update(ctx context.Context, record printTemplateRecord) error
	delete(ctx context.Context, id int64) error
}

type printTemplateRecord struct {
	ID             int64
	BillType       string
	TemplateName   string
	TemplateCode   string
	TemplateHtml   string
	TemplateType   string
	Engine         string
	AssetRef       sql.NullString
	VersionNo      int
	Status         string
	SyncMode       string
	SourceRef      sql.NullString
	SourceChecksum sql.NullString
	CreateTime     time.Time
	UpdateTime     sql.NullTime
}

type printTemplateUploadJSON struct {
	Filename string
	Content  []byte
}

func NewPrintTemplateService(db *pgxpool.Pool, machineID int64) PrintTemplateService {
	return PrintTemplateService{
		store:       printTemplatePGStore{db: db},
		idGenerator: NewIDGenerator(machineID),
	}
}

func (s PrintTemplateService) ListByBillType(ctx context.Context, billType string) ([]PrintTemplateResponse, error) {
	if s.store == nil {
		return nil, errors.New("database client is not configured")
	}
	normalized, err := normalizePrintTemplateBillType(billType)
	if err != nil {
		return nil, err
	}
	records, err := s.store.listByBillType(ctx, normalized)
	if err != nil {
		return nil, err
	}
	responses := make([]PrintTemplateResponse, 0, len(records))
	for _, record := range records {
		responses = append(responses, printTemplateResponse(record))
	}
	return responses, nil
}

func (s PrintTemplateService) GetBillType(ctx context.Context, id int64) (string, error) {
	if s.store == nil {
		return "", errors.New("database client is not configured")
	}
	record, err := s.store.findByID(ctx, id)
	if err != nil {
		return "", err
	}
	return record.BillType, nil
}

func (s PrintTemplateService) Create(ctx context.Context, request PrintTemplateRequest) (PrintTemplateResponse, error) {
	if s.store == nil {
		return PrintTemplateResponse{}, errors.New("database client is not configured")
	}
	id := s.nextID()
	normalized, err := normalizePrintTemplateCreateRequest(request, id)
	if err != nil {
		return PrintTemplateResponse{}, err
	}
	if err := s.ensurePrintTemplateUnique(ctx, normalized.BillType, normalized.TemplateName, normalized.TemplateCode, 0); err != nil {
		return PrintTemplateResponse{}, err
	}
	record := printTemplateRecord{
		ID:           id,
		BillType:     normalized.BillType,
		TemplateName: normalized.TemplateName,
		TemplateCode: normalized.TemplateCode,
		TemplateHtml: normalized.TemplateHtml,
		TemplateType: normalized.TemplateType,
		Engine:       normalized.Engine,
		AssetRef:     optionalNullString(normalized.AssetRef),
		VersionNo:    normalized.versionNoValue(),
		Status:       normalized.Status,
		SyncMode:     printTemplateSyncManual,
		CreateTime:   time.Now(),
	}
	if err := s.store.insert(ctx, record); err != nil {
		return PrintTemplateResponse{}, printTemplateWriteError(err)
	}
	return s.detail(ctx, id)
}

func (s PrintTemplateService) Update(ctx context.Context, id int64, request PrintTemplateRequest) (PrintTemplateResponse, error) {
	if s.store == nil {
		return PrintTemplateResponse{}, errors.New("database client is not configured")
	}
	current, err := s.store.findByID(ctx, id)
	if err != nil {
		return PrintTemplateResponse{}, err
	}
	if current.SyncMode == printTemplateSyncFile {
		return PrintTemplateResponse{}, NewAuthError(AuthErrorBusiness, "文件托管模板请通过上传 JSON 或修改源文件后重启同步")
	}
	normalized, err := normalizePrintTemplateUpdateRequest(request, current)
	if err != nil {
		return PrintTemplateResponse{}, err
	}
	if err := s.ensurePrintTemplateUnique(ctx, normalized.BillType, normalized.TemplateName, normalized.TemplateCode, id); err != nil {
		return PrintTemplateResponse{}, err
	}
	current.BillType = normalized.BillType
	current.TemplateName = normalized.TemplateName
	current.TemplateCode = normalized.TemplateCode
	current.TemplateHtml = normalized.TemplateHtml
	current.TemplateType = normalized.TemplateType
	current.Engine = normalized.Engine
	current.AssetRef = optionalNullString(normalized.AssetRef)
	current.VersionNo = normalized.versionNoValue()
	current.Status = normalized.Status
	if err := s.store.update(ctx, current); err != nil {
		return PrintTemplateResponse{}, printTemplateWriteError(err)
	}
	return s.detail(ctx, id)
}

func (s PrintTemplateService) UploadJSON(ctx context.Context, id int64, filename string, content []byte) (PrintTemplateResponse, error) {
	if s.store == nil {
		return PrintTemplateResponse{}, errors.New("database client is not configured")
	}
	current, err := s.store.findByID(ctx, id)
	if err != nil {
		return PrintTemplateResponse{}, err
	}
	if normalizePrintTemplateTypeValue(current.TemplateType) != printTemplateTypePDFForm {
		return PrintTemplateResponse{}, NewAuthError(AuthErrorBusiness, "仅 PDF_FORM 模板支持上传 JSON")
	}
	templateHTML, err := readPrintTemplateUploadJSON(printTemplateUploadJSON{Filename: filename, Content: content})
	if err != nil {
		return PrintTemplateResponse{}, err
	}
	if current.VersionNo < 1 {
		current.VersionNo = 1
	}
	current.TemplateHtml = templateHTML
	current.VersionNo++
	current.SyncMode = printTemplateSyncManual
	current.SourceRef = sql.NullString{}
	current.SourceChecksum = sql.NullString{}
	if err := s.store.update(ctx, current); err != nil {
		return PrintTemplateResponse{}, printTemplateWriteError(err)
	}
	return s.detail(ctx, id)
}

func (s PrintTemplateService) UploadJson(ctx context.Context, id int64, filename string, content []byte) (PrintTemplateResponse, error) {
	return s.UploadJSON(ctx, id, filename, content)
}

func (s PrintTemplateService) Delete(ctx context.Context, id int64) error {
	if s.store == nil {
		return errors.New("database client is not configured")
	}
	if err := s.store.delete(ctx, id); err != nil {
		return err
	}
	return nil
}

func (s PrintTemplateService) detail(ctx context.Context, id int64) (PrintTemplateResponse, error) {
	record, err := s.store.findByID(ctx, id)
	if err != nil {
		return PrintTemplateResponse{}, err
	}
	return printTemplateResponse(record), nil
}

func (s PrintTemplateService) nextID() int64 {
	if s.idGenerator == nil {
		s.idGenerator = NewIDGenerator(0)
	}
	return s.idGenerator.Next()
}

func (s PrintTemplateService) ensurePrintTemplateUnique(ctx context.Context, billType string, templateName string, templateCode string, excludeID int64) error {
	exists, err := s.store.existsByBillTypeAndTemplateName(ctx, billType, templateName, excludeID)
	if err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "同一单据已存在同名打印模板")
	}
	exists, err = s.store.existsByBillTypeAndTemplateCode(ctx, billType, templateCode, excludeID)
	if err != nil {
		return err
	}
	if exists {
		return NewAuthError(AuthErrorBusiness, "同一单据已存在同编码打印模板")
	}
	return nil
}

func normalizePrintTemplateCreateRequest(request PrintTemplateRequest, id int64) (PrintTemplateRequest, error) {
	if strings.TrimSpace(request.TemplateCode) == "" {
		request.TemplateCode = "TPL_" + strconv.FormatInt(id, 10)
	}
	return normalizePrintTemplateRequest(request)
}

func normalizePrintTemplateUpdateRequest(request PrintTemplateRequest, current printTemplateRecord) (PrintTemplateRequest, error) {
	if strings.TrimSpace(request.TemplateCode) == "" {
		request.TemplateCode = current.TemplateCode
	}
	return normalizePrintTemplateRequest(request)
}

func normalizePrintTemplateRequest(request PrintTemplateRequest) (PrintTemplateRequest, error) {
	billType, err := normalizePrintTemplateBillType(request.BillType)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	templateName, err := normalizePrintTemplateName(request.TemplateName)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	templateType, err := normalizePrintTemplateType(request.TemplateType)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	engine, err := normalizePrintTemplateEngine(request.Engine, templateType)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	assetRef, err := normalizePrintTemplateAssetRef(request.AssetRef, templateType)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	templateCode, err := normalizePrintTemplateCode(request.TemplateCode)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	versionNo, err := normalizePrintTemplateVersionNo(request.VersionNo)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	status, err := normalizePrintTemplateStatus(request.Status)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	templateHTML, err := normalizePrintTemplateHTML(billType, request.TemplateHtml, templateType)
	if err != nil {
		return PrintTemplateRequest{}, err
	}
	return PrintTemplateRequest{
		BillType:     billType,
		TemplateName: templateName,
		TemplateCode: templateCode,
		TemplateHtml: templateHTML,
		TemplateType: templateType,
		Engine:       engine,
		AssetRef:     assetRef,
		VersionNo:    &versionNo,
		Status:       status,
	}, nil
}

func normalizePrintTemplateBillType(billType string) (string, error) {
	normalized := strings.TrimSpace(billType)
	if normalized == "" {
		return "", NewAuthError(AuthErrorBusiness, "适用页面不能为空")
	}
	if _, ok := allowedPrintTemplateBillTypes[normalized]; !ok {
		return "", NewAuthError(AuthErrorValidation, "适用页面不合法")
	}
	return normalized, nil
}

func normalizePrintTemplateName(templateName string) (string, error) {
	normalized := strings.TrimSpace(templateName)
	if normalized == "" {
		return "", NewAuthError(AuthErrorBusiness, "模板名称不能为空")
	}
	if len([]rune(normalized)) > 128 {
		return "", NewAuthError(AuthErrorValidation, "模板名称不能超过128个字符")
	}
	return normalized, nil
}

func normalizePrintTemplateCode(templateCode string) (string, error) {
	normalized := strings.TrimSpace(templateCode)
	if normalized == "" {
		return "", NewAuthError(AuthErrorBusiness, "模板编码不能为空")
	}
	normalized = strings.ToUpper(normalized)
	var builder strings.Builder
	for _, r := range normalized {
		if (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '_' || r == '-' {
			builder.WriteRune(r)
			continue
		}
		builder.WriteByte('_')
	}
	normalized = builder.String()
	if normalized == "" {
		return "", NewAuthError(AuthErrorValidation, "模板编码不合法")
	}
	if len(normalized) > 96 {
		return "", NewAuthError(AuthErrorValidation, "模板编码不能超过96个字符")
	}
	return normalized, nil
}

func normalizePrintTemplateType(templateType string) (string, error) {
	normalized := normalizePrintTemplateTypeValue(templateType)
	if _, ok := allowedPrintTemplateTypes[normalized]; !ok {
		return "", NewAuthError(AuthErrorValidation, "模板类型仅支持 COORD 或 PDF_FORM")
	}
	return normalized, nil
}

func normalizePrintTemplateTypeValue(templateType string) string {
	if strings.TrimSpace(templateType) == "" {
		return printTemplateTypeCoord
	}
	return strings.ToUpper(strings.TrimSpace(templateType))
}

func normalizePrintTemplateEngine(engine string, templateType string) (string, error) {
	normalized := strings.TrimSpace(engine)
	if normalized == "" {
		normalized = defaultPrintTemplateEngine(templateType)
	}
	normalized = strings.ToUpper(normalized)
	if _, ok := allowedPrintTemplateEngines[normalized]; !ok {
		return "", NewAuthError(AuthErrorValidation, "渲染引擎仅支持 LODOP 或 PDF_FORM")
	}
	if templateType == printTemplateTypePDFForm && normalized != printTemplateTypePDFForm {
		return "", NewAuthError(AuthErrorValidation, "PDF_FORM 模板必须使用 PDF_FORM 引擎")
	}
	if templateType == printTemplateTypeCoord && normalized != printTemplateEngineLODOP {
		return "", NewAuthError(AuthErrorValidation, "COORD 模板必须使用 LODOP 引擎")
	}
	return normalized, nil
}

func defaultPrintTemplateEngine(templateType string) string {
	if templateType == printTemplateTypePDFForm {
		return printTemplateTypePDFForm
	}
	return printTemplateEngineLODOP
}

func normalizePrintTemplateStatus(status string) (string, error) {
	normalized := strings.TrimSpace(status)
	if normalized == "" {
		normalized = printTemplateStatusActive
	}
	normalized = strings.ToUpper(normalized)
	if _, ok := allowedPrintTemplateStatuses[normalized]; !ok {
		return "", NewAuthError(AuthErrorValidation, "模板状态仅支持 ACTIVE 或 DISABLED")
	}
	return normalized, nil
}

func normalizePrintTemplateVersionNo(versionNo *int) (int, error) {
	if versionNo == nil {
		return 1, nil
	}
	if *versionNo < 1 {
		return 0, NewAuthError(AuthErrorValidation, "模板版本号必须大于 0")
	}
	return *versionNo, nil
}

func normalizePrintTemplateAssetRef(assetRef string, templateType string) (string, error) {
	if templateType != printTemplateTypePDFForm {
		return "", nil
	}
	normalized := strings.TrimSpace(assetRef)
	if normalized == "" {
		return "", nil
	}
	if strings.Contains(normalized, "..") || strings.HasPrefix(normalized, "/") || !strings.HasSuffix(strings.ToLower(normalized), ".pdf") {
		return "", NewAuthError(AuthErrorValidation, "PDF 底版资源路径不合法")
	}
	if len(normalized) > 255 {
		return "", NewAuthError(AuthErrorValidation, "PDF 底版资源路径不能超过255个字符")
	}
	return normalized, nil
}

func normalizePrintTemplateHTML(billType string, templateHTML string, templateType string) (string, error) {
	if templateType == printTemplateTypePDFForm {
		normalized := strings.TrimSpace(templateHTML)
		if normalized == "" {
			loaded, err := defaultPrintTemplateHTML(billType)
			if err != nil {
				return "", err
			}
			normalized = strings.TrimSpace(loaded)
		}
		if len(normalized) > 200000 {
			return "", NewAuthError(AuthErrorValidation, "模板内容不能超过200000个字符")
		}
		if err := validatePrintTemplatePDFFormJSON(normalized); err != nil {
			return "", err
		}
		return normalized, nil
	}
	normalized := strings.TrimSpace(templateHTML)
	if normalized == "" {
		return "", NewAuthError(AuthErrorBusiness, "模板内容不能为空")
	}
	if len(normalized) > 200000 {
		return "", NewAuthError(AuthErrorValidation, "模板内容不能超过200000个字符")
	}
	if err := validatePrintTemplateCoordContent(normalized); err != nil {
		return "", err
	}
	return normalized, nil
}

func validatePrintTemplateCoordContent(templateHTML string) error {
	for _, pattern := range dangerousLODOPPatterns {
		if pattern.MatchString(templateHTML) {
			return NewAuthError(AuthErrorValidation, "模板内容包含不允许的脚本或危险标签")
		}
	}
	return nil
}

func validatePrintTemplatePDFFormJSON(templateHTML string) error {
	decoder := json.NewDecoder(strings.NewReader(templateHTML))
	decoder.DisallowUnknownFields()
	var value any
	if err := decoder.Decode(&value); err != nil {
		return NewAuthError(AuthErrorValidation, "PDF 表单模板配置不是合法 JSON")
	}
	if decoder.Decode(&value) != io.EOF {
		return NewAuthError(AuthErrorValidation, "PDF 表单模板配置不是合法 JSON")
	}
	object, ok := value.(map[string]any)
	if !ok {
		return NewAuthError(AuthErrorValidation, "PDF 表单模板配置不是合法 JSON 对象")
	}
	if _, ok := object["form"]; ok {
		return NewAuthError(AuthErrorValidation, "PDF_FORM 模板不支持 form 专用配置，请使用通用布局 JSON")
	}
	staticValid := false
	if staticValue, ok := object["static"]; ok {
		_, staticValid = staticValue.([]any)
	}
	_, fieldsValid := object["fields"].(map[string]any)
	_, tableValid := object["table"].(map[string]any)
	if !staticValid && !fieldsValid && !tableValid {
		return NewAuthError(AuthErrorValidation, "PDF_FORM 模板必须配置通用字段或明细布局")
	}
	return nil
}

func defaultPrintTemplateHTML(billType string) (string, error) {
	path := resolvePrintTemplateLayoutPath(defaultPrintTemplateLayoutPath(billType))
	content, err := os.ReadFile(path)
	if err != nil {
		return "", NewAuthError(AuthErrorInternal, "读取 PDF_FORM 默认布局失败")
	}
	return string(content), nil
}

func defaultPrintTemplateLayoutPath(billType string) string {
	if strings.HasPrefix(billType, "purchase-") {
		return defaultPurchaseLayout
	}
	if strings.HasPrefix(billType, "sales-") {
		return defaultSalesLayout
	}
	if billType == "freight-bill" {
		return defaultLogisticsLayout
	}
	if strings.HasSuffix(billType, "-statement") {
		return defaultStatementLayout
	}
	return defaultPDFFormLayout
}

func resolvePrintTemplateLayoutPath(path string) string {
	if _, err := os.Stat(path); err == nil {
		return path
	}
	wd, err := os.Getwd()
	if err != nil {
		return path
	}
	for {
		candidate := filepath.Join(wd, path)
		if _, err := os.Stat(candidate); err == nil {
			return candidate
		}
		parent := filepath.Dir(wd)
		if parent == wd {
			return path
		}
		wd = parent
	}
}

func readPrintTemplateUploadJSON(file printTemplateUploadJSON) (string, error) {
	if err := validatePrintTemplateJSONFilename(file.Filename); err != nil {
		return "", err
	}
	if len(file.Content) == 0 {
		return "", NewAuthError(AuthErrorValidation, "上传 JSON 文件不能为空")
	}
	if len(file.Content) > MaxPrintTemplateUploadJSONBytes {
		return "", NewAuthError(AuthErrorValidation, "上传 JSON 文件不能超过 1MB")
	}
	if !utf8.Valid(file.Content) {
		return "", NewAuthError(AuthErrorValidation, "上传 JSON 文件必须使用 UTF-8 编码")
	}
	content := strings.TrimSpace(string(file.Content))
	content = strings.TrimSpace(strings.TrimPrefix(content, "\ufeff"))
	if content == "" {
		return "", NewAuthError(AuthErrorValidation, "上传 JSON 文件不能为空")
	}
	if err := validatePrintTemplatePDFFormJSON(content); err != nil {
		return "", err
	}
	return content, nil
}

func validatePrintTemplateJSONFilename(filename string) error {
	if strings.TrimSpace(filename) == "" {
		return NewAuthError(AuthErrorValidation, "请上传 JSON 文件")
	}
	base := filepath.Base(strings.ReplaceAll(filename, "\\", "/"))
	if !strings.HasSuffix(strings.ToLower(base), ".json") {
		return NewAuthError(AuthErrorValidation, "请上传 JSON 文件")
	}
	return nil
}

func (request PrintTemplateRequest) versionNoValue() int {
	if request.VersionNo == nil {
		return 1
	}
	return *request.VersionNo
}

func printTemplateResponse(record printTemplateRecord) PrintTemplateResponse {
	return PrintTemplateResponse{
		ID:             strconv.FormatInt(record.ID, 10),
		TemplateName:   record.TemplateName,
		TemplateCode:   record.TemplateCode,
		TemplateHtml:   record.TemplateHtml,
		BillType:       record.BillType,
		TemplateType:   record.TemplateType,
		Engine:         record.Engine,
		AssetRef:       nullableStringPointer(record.AssetRef),
		VersionNo:      record.VersionNo,
		Status:         record.Status,
		SyncMode:       record.SyncMode,
		SourceRef:      nullableStringPointer(record.SourceRef),
		SourceChecksum: nullableStringPointer(record.SourceChecksum),
		CreateTime:     javaLocalDateTimeMillis(record.CreateTime),
		UpdateTime:     nullableJavaLocalDateTimeMillis(record.UpdateTime),
	}
}

func nullableStringPointer(value sql.NullString) *string {
	if !value.Valid {
		return nil
	}
	result := value.String
	return &result
}

func nullableJavaLocalDateTimeMillis(value sql.NullTime) *int64 {
	if !value.Valid {
		return nil
	}
	result := javaLocalDateTimeMillis(value.Time)
	return &result
}

func javaLocalDateTimeMillis(value time.Time) int64 {
	return time.Date(
		value.Year(), value.Month(), value.Day(),
		value.Hour(), value.Minute(), value.Second(), value.Nanosecond(),
		printTemplateJavaZone,
	).UnixMilli()
}

func printTemplateWriteError(err error) error {
	if isUniqueViolation(err) {
		return NewAuthError(AuthErrorBusiness, "同一单据已存在同名或同编码打印模板")
	}
	return err
}

type printTemplatePGStore struct {
	db *pgxpool.Pool
}

func (s printTemplatePGStore) listByBillType(ctx context.Context, billType string) ([]printTemplateRecord, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, bill_type, template_name, template_code, template_html,
		       template_type, engine, asset_ref, version_no, status,
		       sync_mode, source_ref, source_checksum, created_at, updated_at
		  FROM sys_print_template
		 WHERE bill_type = $1
		   AND deleted_flag = false
		 ORDER BY updated_at DESC, id DESC
	`, billType)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	records := []printTemplateRecord{}
	for rows.Next() {
		record, err := scanPrintTemplateRecord(rows)
		if err != nil {
			return nil, err
		}
		records = append(records, record)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return records, nil
}

func (s printTemplatePGStore) findByID(ctx context.Context, id int64) (printTemplateRecord, error) {
	if s.db == nil {
		return printTemplateRecord{}, errors.New("database client is not configured")
	}
	row := s.db.QueryRow(ctx, `
		SELECT id, bill_type, template_name, template_code, template_html,
		       template_type, engine, asset_ref, version_no, status,
		       sync_mode, source_ref, source_checksum, created_at, updated_at
		  FROM sys_print_template
		 WHERE id = $1
		   AND deleted_flag = false
	`, id)
	record, err := scanPrintTemplateRow(row)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return printTemplateRecord{}, NewAuthError(AuthErrorNotFound, "打印模板不存在")
		}
		return printTemplateRecord{}, err
	}
	return record, nil
}

func (s printTemplatePGStore) existsByBillTypeAndTemplateName(ctx context.Context, billType string, templateName string, excludeID int64) (bool, error) {
	return s.exists(ctx, "template_name", billType, templateName, excludeID)
}

func (s printTemplatePGStore) existsByBillTypeAndTemplateCode(ctx context.Context, billType string, templateCode string, excludeID int64) (bool, error) {
	return s.exists(ctx, "template_code", billType, templateCode, excludeID)
}

func (s printTemplatePGStore) exists(ctx context.Context, column string, billType string, value string, excludeID int64) (bool, error) {
	if s.db == nil {
		return false, errors.New("database client is not configured")
	}
	query := "SELECT EXISTS (SELECT 1 FROM sys_print_template WHERE bill_type = $1 AND " + column + " = $2 AND deleted_flag = false"
	args := []any{billType, value}
	if excludeID > 0 {
		query += " AND id <> $3"
		args = append(args, excludeID)
	}
	query += ")"
	var exists bool
	if err := s.db.QueryRow(ctx, query, args...).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (s printTemplatePGStore) insert(ctx context.Context, record printTemplateRecord) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	_, err := s.db.Exec(ctx, `
		INSERT INTO sys_print_template (
			id, bill_type, template_name, template_code, template_html,
			template_type, engine, asset_ref, version_no, status,
			sync_mode, source_ref, source_checksum, is_default,
			created_by, created_name, deleted_flag
		) VALUES (
			$1, $2, $3, $4, $5,
			$6, $7, $8, $9, $10,
			$11, $12, $13, false,
			0, 'system', false
		)
	`, record.ID, record.BillType, record.TemplateName, record.TemplateCode, record.TemplateHtml,
		record.TemplateType, record.Engine, record.AssetRef, record.VersionNo, record.Status,
		record.SyncMode, record.SourceRef, record.SourceChecksum)
	return err
}

func (s printTemplatePGStore) update(ctx context.Context, record printTemplateRecord) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	command, err := s.db.Exec(ctx, `
		UPDATE sys_print_template
		   SET bill_type = $2,
		       template_name = $3,
		       template_code = $4,
		       template_html = $5,
		       template_type = $6,
		       engine = $7,
		       asset_ref = $8,
		       version_no = $9,
		       status = $10,
		       sync_mode = $11,
		       source_ref = $12,
		       source_checksum = $13,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, record.ID, record.BillType, record.TemplateName, record.TemplateCode, record.TemplateHtml,
		record.TemplateType, record.Engine, record.AssetRef, record.VersionNo, record.Status,
		record.SyncMode, record.SourceRef, record.SourceChecksum)
	if err != nil {
		return err
	}
	if command.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "打印模板不存在")
	}
	return nil
}

func (s printTemplatePGStore) delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	command, err := s.db.Exec(ctx, `
		UPDATE sys_print_template
		   SET deleted_flag = true,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id)
	if err != nil {
		return err
	}
	if command.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "打印模板不存在")
	}
	return nil
}

type printTemplateScanner interface {
	Scan(dest ...any) error
}

func scanPrintTemplateRecord(rows pgx.Rows) (printTemplateRecord, error) {
	return scanPrintTemplateRow(rows)
}

func scanPrintTemplateRow(row printTemplateScanner) (printTemplateRecord, error) {
	var record printTemplateRecord
	err := row.Scan(
		&record.ID,
		&record.BillType,
		&record.TemplateName,
		&record.TemplateCode,
		&record.TemplateHtml,
		&record.TemplateType,
		&record.Engine,
		&record.AssetRef,
		&record.VersionNo,
		&record.Status,
		&record.SyncMode,
		&record.SourceRef,
		&record.SourceChecksum,
		&record.CreateTime,
		&record.UpdateTime,
	)
	return record, err
}
