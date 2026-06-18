package platform

import (
	"context"
	"database/sql"
	"errors"
	"sort"
	"strconv"
	"testing"
	"time"
)

func TestNormalizePrintTemplateRequestDefaultsAndValidates(t *testing.T) {
	version := 2
	got, err := normalizePrintTemplateRequest(PrintTemplateRequest{
		BillType:     " sales-order ",
		TemplateName: "  A4 ",
		TemplateCode: "tpl demo/1",
		TemplateHtml: " LODOP.PRINT_INIT(\"demo\"); ",
		VersionNo:    &version,
	})
	if err != nil {
		t.Fatalf("normalizePrintTemplateRequest error: %v", err)
	}
	if got.BillType != "sales-order" {
		t.Fatalf("BillType = %q", got.BillType)
	}
	if got.TemplateType != printTemplateTypeCoord || got.Engine != printTemplateEngineLODOP {
		t.Fatalf("type/engine = %q/%q", got.TemplateType, got.Engine)
	}
	if got.TemplateCode != "TPL_DEMO_1" {
		t.Fatalf("TemplateCode = %q", got.TemplateCode)
	}
	if got.VersionNo == nil || *got.VersionNo != 2 {
		t.Fatalf("VersionNo = %v", got.VersionNo)
	}
	if got.Status != printTemplateStatusActive {
		t.Fatalf("Status = %q", got.Status)
	}
}

func TestNormalizePrintTemplateRequestRejectsInvalidBillType(t *testing.T) {
	_, err := normalizePrintTemplateRequest(PrintTemplateRequest{
		BillType:     "unknown",
		TemplateName: "A4",
		TemplateCode: "TPL_A4",
		TemplateHtml: "LODOP.PRINT_INIT(\"demo\");",
	})
	assertAuthErrorKind(t, err, AuthErrorValidation)
}

func TestValidatePrintTemplateContent(t *testing.T) {
	tests := []struct {
		name         string
		templateHTML string
		templateType string
		wantErr      bool
	}{
		{
			name:         "coord allows lodop calls",
			templateHTML: `LODOP.PRINT_INIT("demo");`,
			templateType: printTemplateTypeCoord,
		},
		{
			name:         "coord rejects dangerous script",
			templateHTML: `LODOP.PRINT_INIT("demo"); fetch("/api");`,
			templateType: printTemplateTypeCoord,
			wantErr:      true,
		},
		{
			name:         "pdf form allows generic layout",
			templateHTML: `{"fields":{"billNo":{"source":"billNo"}}}`,
			templateType: printTemplateTypePDFForm,
		},
		{
			name:         "pdf form rejects legacy form field",
			templateHTML: `{"form":"LEGACY","fields":{}}`,
			templateType: printTemplateTypePDFForm,
			wantErr:      true,
		},
		{
			name:         "pdf form rejects missing layout nodes",
			templateHTML: `{"page":{"width":595}}`,
			templateType: printTemplateTypePDFForm,
			wantErr:      true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var err error
			if tt.templateType == printTemplateTypePDFForm {
				err = validatePrintTemplatePDFFormJSON(tt.templateHTML)
			} else {
				err = validatePrintTemplateCoordContent(tt.templateHTML)
			}
			if tt.wantErr && err == nil {
				t.Fatal("expected error")
			}
			if !tt.wantErr && err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
		})
	}
}

func TestPrintTemplateServiceCRUD(t *testing.T) {
	ctx := context.Background()
	store := newFakePrintTemplateStore()
	service := PrintTemplateService{store: store, idGenerator: NewIDGenerator(1)}

	created, err := service.Create(ctx, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "A4",
		TemplateHtml: `LODOP.PRINT_INIT("demo");`,
	})
	if err != nil {
		t.Fatalf("Create error: %v", err)
	}
	if created.TemplateCode == "" || created.TemplateType != printTemplateTypeCoord || created.Engine != printTemplateEngineLODOP {
		t.Fatalf("unexpected create response: %+v", created)
	}

	list, err := service.ListByBillType(ctx, "sales-order")
	if err != nil {
		t.Fatalf("ListByBillType error: %v", err)
	}
	if len(list) != 1 || list[0].ID != created.ID {
		t.Fatalf("list = %+v, want created template", list)
	}

	id := mustParseID(t, created.ID)
	billType, err := service.GetBillType(ctx, id)
	if err != nil {
		t.Fatalf("GetBillType error: %v", err)
	}
	if billType != "sales-order" {
		t.Fatalf("billType = %q", billType)
	}

	updated, err := service.Update(ctx, id, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "A5",
		TemplateHtml: `LODOP.PRINT_INIT("updated");`,
	})
	if err != nil {
		t.Fatalf("Update error: %v", err)
	}
	if updated.TemplateName != "A5" || updated.TemplateCode != created.TemplateCode {
		t.Fatalf("unexpected update response: %+v", updated)
	}

	if err := service.Delete(ctx, id); err != nil {
		t.Fatalf("Delete error: %v", err)
	}
	_, err = service.GetBillType(ctx, id)
	assertAuthErrorKind(t, err, AuthErrorNotFound)
}

func TestPrintTemplateServiceUploadJSON(t *testing.T) {
	ctx := context.Background()
	store := newFakePrintTemplateStore()
	service := PrintTemplateService{store: store, idGenerator: NewIDGenerator(1)}
	created, err := service.Create(ctx, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "PDF",
		TemplateCode: "SO_PDF",
		TemplateType: printTemplateTypePDFForm,
		TemplateHtml: `{"fields":{"billNo":{"source":"billNo"}}}`,
		VersionNo:    intPtr(3),
	})
	if err != nil {
		t.Fatalf("Create error: %v", err)
	}
	id := mustParseID(t, created.ID)
	record := store.records[id]
	record.SyncMode = printTemplateSyncFile
	record.SourceRef = sql.NullString{String: "print-forms/source.layout.json", Valid: true}
	record.SourceChecksum = sql.NullString{String: "abc", Valid: true}
	store.records[id] = record

	uploaded, err := service.UploadJSON(ctx, id, `C:\tmp\layout.json`, []byte("\xef\xbb\xbf"+`{"static":[]}`))
	if err != nil {
		t.Fatalf("UploadJSON error: %v", err)
	}
	if uploaded.TemplateHtml != `{"static":[]}` {
		t.Fatalf("TemplateHtml = %q", uploaded.TemplateHtml)
	}
	if uploaded.VersionNo != 4 {
		t.Fatalf("VersionNo = %d", uploaded.VersionNo)
	}
	if uploaded.SyncMode != printTemplateSyncManual || uploaded.SourceRef != nil || uploaded.SourceChecksum != nil {
		t.Fatalf("sync fields not reset: %+v", uploaded)
	}
}

func TestPrintTemplateServiceUploadJSONRejectsInvalidInput(t *testing.T) {
	ctx := context.Background()
	store := newFakePrintTemplateStore()
	service := PrintTemplateService{store: store, idGenerator: NewIDGenerator(1)}
	created, err := service.Create(ctx, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "Coord",
		TemplateHtml: `LODOP.PRINT_INIT("demo");`,
	})
	if err != nil {
		t.Fatalf("Create error: %v", err)
	}
	id := mustParseID(t, created.ID)
	_, err = service.UploadJSON(ctx, id, "layout.json", []byte(`{"static":[]}`))
	assertAuthErrorKind(t, err, AuthErrorBusiness)

	_, err = readPrintTemplateUploadJSON(printTemplateUploadJSON{Filename: "layout.txt", Content: []byte(`{"static":[]}`)})
	assertAuthErrorKind(t, err, AuthErrorValidation)

	_, err = readPrintTemplateUploadJSON(printTemplateUploadJSON{Filename: "layout.json", Content: []byte{0xff}})
	assertAuthErrorKind(t, err, AuthErrorValidation)
}

func TestPrintTemplateJavaCompatibilityDetails(t *testing.T) {
	code, err := normalizePrintTemplateCode("中文")
	if err != nil {
		t.Fatalf("normalizePrintTemplateCode error: %v", err)
	}
	if code != "__" {
		t.Fatalf("code = %q, want __", code)
	}

	content, err := readPrintTemplateUploadJSON(printTemplateUploadJSON{
		Filename: "layout.json",
		Content:  []byte(" \n\ufeff{\"static\":[]} \n"),
	})
	if err != nil {
		t.Fatalf("readPrintTemplateUploadJSON error: %v", err)
	}
	if content != `{"static":[]}` {
		t.Fatalf("content = %q", content)
	}

	record := printTemplateRecord{
		ID:           1,
		BillType:     "sales-order",
		TemplateName: "A4",
		TemplateCode: "TPL_1",
		TemplateHtml: `{"static":[]}`,
		TemplateType: printTemplateTypePDFForm,
		Engine:       printTemplateTypePDFForm,
		VersionNo:    1,
		Status:       printTemplateStatusActive,
		SyncMode:     printTemplateSyncManual,
		CreateTime:   time.Date(2026, 6, 18, 9, 30, 0, 0, time.UTC),
		UpdateTime:   sql.NullTime{Time: time.Date(2026, 6, 18, 10, 30, 0, 0, time.UTC), Valid: true},
	}
	response := printTemplateResponse(record)
	if response.CreateTime != 1781746200000 || response.UpdateTime == nil || *response.UpdateTime != 1781749800000 {
		t.Fatalf("time millis = %d/%v", response.CreateTime, response.UpdateTime)
	}
}

func TestPrintTemplateServiceRejectsDuplicateAndNotFound(t *testing.T) {
	ctx := context.Background()
	store := newFakePrintTemplateStore()
	service := PrintTemplateService{store: store, idGenerator: NewIDGenerator(1)}
	_, err := service.Create(ctx, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "A4",
		TemplateCode: "SO_A4",
		TemplateHtml: `LODOP.PRINT_INIT("demo");`,
	})
	if err != nil {
		t.Fatalf("Create error: %v", err)
	}

	_, err = service.Create(ctx, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "A4",
		TemplateCode: "SO_A5",
		TemplateHtml: `LODOP.PRINT_INIT("demo");`,
	})
	assertAuthErrorKind(t, err, AuthErrorBusiness)

	_, err = service.Create(ctx, PrintTemplateRequest{
		BillType:     "sales-order",
		TemplateName: "Other",
		TemplateCode: "SO_A4",
		TemplateHtml: `LODOP.PRINT_INIT("demo");`,
	})
	assertAuthErrorKind(t, err, AuthErrorBusiness)

	_, err = service.GetBillType(ctx, 404)
	assertAuthErrorKind(t, err, AuthErrorNotFound)
	if err := service.Delete(ctx, 404); err == nil {
		t.Fatal("expected delete not found error")
	} else {
		assertAuthErrorKind(t, err, AuthErrorNotFound)
	}
}

type fakePrintTemplateStore struct {
	records map[int64]printTemplateRecord
}

func newFakePrintTemplateStore() *fakePrintTemplateStore {
	return &fakePrintTemplateStore{records: map[int64]printTemplateRecord{}}
}

func (s *fakePrintTemplateStore) listByBillType(_ context.Context, billType string) ([]printTemplateRecord, error) {
	records := []printTemplateRecord{}
	for _, record := range s.records {
		if record.BillType == billType {
			records = append(records, record)
		}
	}
	sort.Slice(records, func(i, j int) bool {
		left, right := records[i], records[j]
		if left.UpdateTime.Valid != right.UpdateTime.Valid {
			return left.UpdateTime.Valid
		}
		if left.UpdateTime.Valid && !left.UpdateTime.Time.Equal(right.UpdateTime.Time) {
			return left.UpdateTime.Time.After(right.UpdateTime.Time)
		}
		return left.ID > right.ID
	})
	return records, nil
}

func (s *fakePrintTemplateStore) findByID(_ context.Context, id int64) (printTemplateRecord, error) {
	record, ok := s.records[id]
	if !ok {
		return printTemplateRecord{}, NewAuthError(AuthErrorNotFound, "打印模板不存在")
	}
	return record, nil
}

func (s *fakePrintTemplateStore) existsByBillTypeAndTemplateName(_ context.Context, billType string, templateName string, excludeID int64) (bool, error) {
	for _, record := range s.records {
		if record.ID != excludeID && record.BillType == billType && record.TemplateName == templateName {
			return true, nil
		}
	}
	return false, nil
}

func (s *fakePrintTemplateStore) existsByBillTypeAndTemplateCode(_ context.Context, billType string, templateCode string, excludeID int64) (bool, error) {
	for _, record := range s.records {
		if record.ID != excludeID && record.BillType == billType && record.TemplateCode == templateCode {
			return true, nil
		}
	}
	return false, nil
}

func (s *fakePrintTemplateStore) insert(_ context.Context, record printTemplateRecord) error {
	record.CreateTime = time.Now()
	s.records[record.ID] = record
	return nil
}

func (s *fakePrintTemplateStore) update(_ context.Context, record printTemplateRecord) error {
	if _, ok := s.records[record.ID]; !ok {
		return NewAuthError(AuthErrorNotFound, "打印模板不存在")
	}
	record.UpdateTime = sql.NullTime{Time: time.Now(), Valid: true}
	s.records[record.ID] = record
	return nil
}

func (s *fakePrintTemplateStore) delete(_ context.Context, id int64) error {
	if _, ok := s.records[id]; !ok {
		return NewAuthError(AuthErrorNotFound, "打印模板不存在")
	}
	delete(s.records, id)
	return nil
}

func assertAuthErrorKind(t *testing.T, err error, want AuthErrorKind) {
	t.Helper()
	if err == nil {
		t.Fatalf("expected %s error, got nil", want)
	}
	var authErr AuthError
	if !errors.As(err, &authErr) {
		t.Fatalf("error type = %T, want AuthError", err)
	}
	if authErr.Kind != want {
		t.Fatalf("AuthError.Kind = %s, want %s; message=%q", authErr.Kind, want, authErr.Message)
	}
}

func mustParseID(t *testing.T, value string) int64 {
	t.Helper()
	id, err := strconv.ParseInt(value, 10, 64)
	if err != nil {
		t.Fatalf("invalid id %q: %v", value, err)
	}
	return id
}

func intPtr(value int) *int {
	return &value
}
