package platform

import (
	"archive/zip"
	"bytes"
	"encoding/xml"
	"errors"
	"io"
	"strconv"
	"strings"
	"testing"
)

func TestInventoryReportExcelDownloadBuildsXLSX(t *testing.T) {
	file, err := inventoryReportExcelDownload([]InventoryReportResponse{
		{
			MaterialCode:   "M-001",
			Brand:          "品牌A",
			Material:       "材质A",
			Category:       "类别A",
			Spec:           "规格A",
			Length:         "9m",
			WarehouseName:  "一号仓",
			BatchNo:        "B-001",
			Quantity:       2,
			QuantityUnit:   "件",
			WeightTon:      1.25,
			PieceWeightTon: 0.625,
			Unit:           "吨",
		},
	})
	if err != nil {
		t.Fatalf("inventoryReportExcelDownload() error = %v", err)
	}
	if file.Filename != "商品库存报表.xlsx" {
		t.Fatalf("Filename = %q, want 商品库存报表.xlsx", file.Filename)
	}
	if file.ContentType != "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" {
		t.Fatalf("ContentType = %q, want xlsx MIME", file.ContentType)
	}

	entries := unzipXLSXEntries(t, file.Content)
	if !strings.Contains(entries["xl/workbook.xml"], `<sheet name="Data" sheetId="1" r:id="rId1"/>`) {
		t.Fatalf("workbook.xml = %s, want Data sheet", entries["xl/workbook.xml"])
	}
	if !strings.Contains(entries["xl/styles.xml"], `formatCode="0.000"`) {
		t.Fatalf("styles.xml = %s, want 0.000 number format", entries["xl/styles.xml"])
	}

	shared := parseSharedStrings(t, entries["xl/sharedStrings.xml"])
	header := sheetRowValues(t, entries["xl/worksheets/sheet1.xml"], shared, 1)
	wantHeader := []string{
		"商品编码",
		"品牌",
		"材质",
		"类别",
		"规格",
		"长度",
		"仓库",
		"批号",
		"结存数量",
		"数量单位",
		"结存重量(吨)",
		"件重(吨)",
		"单位",
	}
	assertStringSliceEqual(t, header, wantHeader)

	row := sheetRowValues(t, entries["xl/worksheets/sheet1.xml"], shared, 2)
	wantRow := []string{"M-001", "品牌A", "材质A", "类别A", "规格A", "9m", "一号仓", "B-001", "2", "件", "1.25", "0.625", "吨"}
	assertStringSliceEqual(t, row, wantRow)

	sheet := entries["xl/worksheets/sheet1.xml"]
	if !strings.Contains(sheet, `<c r="K2" s="2"><v>1.25</v></c>`) {
		t.Fatalf("sheet1.xml = %s, want K2 numeric value with 0.000 style", sheet)
	}
	if !strings.Contains(sheet, `<c r="L2" s="2"><v>0.625</v></c>`) {
		t.Fatalf("sheet1.xml = %s, want L2 numeric value with 0.000 style", sheet)
	}
}

func TestInventoryReportExcelDownloadKeepsNilPieceWeightBlank(t *testing.T) {
	file, err := inventoryReportExportExcelDownload([]inventoryReportExportRow{
		{
			MaterialCode:   "M-002",
			WeightTon:      3.5,
			PieceWeightTon: nil,
		},
	})
	if err != nil {
		t.Fatalf("inventoryReportExportExcelDownload() error = %v", err)
	}

	entries := unzipXLSXEntries(t, file.Content)
	sheet := entries["xl/worksheets/sheet1.xml"]
	if !strings.Contains(sheet, `<c r="K2" s="2"><v>3.5</v></c>`) {
		t.Fatalf("sheet1.xml = %s, want K2 numeric weight", sheet)
	}
	if !strings.Contains(sheet, `<c r="L2"/>`) {
		t.Fatalf("sheet1.xml = %s, want blank L2 piece weight", sheet)
	}
}

func TestInventoryReportExcelDownloadRejectsTooManyRows(t *testing.T) {
	rows := make([]InventoryReportResponse, inventoryReportMaxExportRows+1)

	_, err := inventoryReportExcelDownload(rows)
	if err == nil {
		t.Fatal("inventoryReportExcelDownload() error = nil, want validation AuthError")
	}
	var authErr AuthError
	if !errors.As(err, &authErr) {
		t.Fatalf("error type = %T, want AuthError", err)
	}
	if authErr.Kind != AuthErrorValidation {
		t.Fatalf("AuthError.Kind = %s, want %s", authErr.Kind, AuthErrorValidation)
	}
}

func unzipXLSXEntries(t *testing.T, content []byte) map[string]string {
	t.Helper()
	reader, err := zip.NewReader(bytes.NewReader(content), int64(len(content)))
	if err != nil {
		t.Fatalf("zip.NewReader() error = %v", err)
	}
	entries := map[string]string{}
	for _, file := range reader.File {
		rc, err := file.Open()
		if err != nil {
			t.Fatalf("open zip entry %q error = %v", file.Name, err)
		}
		data, readErr := io.ReadAll(rc)
		closeErr := rc.Close()
		if readErr != nil {
			t.Fatalf("read zip entry %q error = %v", file.Name, readErr)
		}
		if closeErr != nil {
			t.Fatalf("close zip entry %q error = %v", file.Name, closeErr)
		}
		entries[file.Name] = string(data)
	}
	return entries
}

func parseSharedStrings(t *testing.T, content string) []string {
	t.Helper()
	var sst struct {
		Items []struct {
			Text string `xml:"t"`
		} `xml:"si"`
	}
	if err := xml.Unmarshal([]byte(content), &sst); err != nil {
		t.Fatalf("xml.Unmarshal(sharedStrings) error = %v", err)
	}
	result := make([]string, 0, len(sst.Items))
	for _, item := range sst.Items {
		result = append(result, item.Text)
	}
	return result
}

func sheetRowValues(t *testing.T, content string, shared []string, rowNumber int) []string {
	t.Helper()
	var worksheet struct {
		Rows []struct {
			Number int `xml:"r,attr"`
			Cells  []struct {
				Type  string `xml:"t,attr"`
				Value string `xml:"v"`
			} `xml:"c"`
		} `xml:"sheetData>row"`
	}
	if err := xml.Unmarshal([]byte(content), &worksheet); err != nil {
		t.Fatalf("xml.Unmarshal(sheet) error = %v", err)
	}
	for _, row := range worksheet.Rows {
		if row.Number != rowNumber {
			continue
		}
		values := make([]string, 0, len(row.Cells))
		for _, cell := range row.Cells {
			if cell.Type != "s" {
				values = append(values, cell.Value)
				continue
			}
			index, err := strconv.Atoi(cell.Value)
			if err != nil {
				t.Fatalf("shared string index %q is not numeric", cell.Value)
			}
			if index < 0 || index >= len(shared) {
				t.Fatalf("shared string index %d out of range", index)
			}
			values = append(values, shared[index])
		}
		return values
	}
	t.Fatalf("row %d not found", rowNumber)
	return nil
}

func assertStringSliceEqual(t *testing.T, got []string, want []string) {
	t.Helper()
	if len(got) != len(want) {
		t.Fatalf("values = %#v, want %#v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("values = %#v, want %#v", got, want)
		}
	}
}
