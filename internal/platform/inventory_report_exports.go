package platform

import (
	"archive/zip"
	"bytes"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"html"
	"math"
	"strconv"
	"strings"
)

const (
	inventoryReportExportFileName = "商品库存报表.xlsx"
	inventoryReportMaxExportRows  = 10000
)

func (s InventoryReportService) ExportExcel(ctx context.Context, keyword, warehouseName, category string) (FileDownloadResponse, error) {
	rows, err := s.listExportRows(ctx, InventoryReportFilter{
		Keyword:       keyword,
		WarehouseName: warehouseName,
		Category:      category,
	})
	if err != nil {
		return FileDownloadResponse{}, err
	}
	return inventoryReportExportExcelDownload(rows)
}

func inventoryReportExcelDownload(rows []InventoryReportResponse) (FileDownloadResponse, error) {
	exportRows := make([]inventoryReportExportRow, 0, len(rows))
	for _, row := range rows {
		exportRows = append(exportRows, inventoryReportExportRowFrom(row))
	}
	return inventoryReportExportExcelDownload(exportRows)
}

func inventoryReportExportExcelDownload(rows []inventoryReportExportRow) (FileDownloadResponse, error) {
	if len(rows) > inventoryReportMaxExportRows {
		return FileDownloadResponse{}, NewAuthError(AuthErrorValidation, "导出数据超过限制: "+strconv.Itoa(len(rows))+" 行 (最大 "+strconv.Itoa(inventoryReportMaxExportRows)+" 行)")
	}
	content, err := buildInventoryReportWorkbook(rows)
	if err != nil {
		return FileDownloadResponse{}, err
	}
	return FileDownloadResponse{
		Filename:    inventoryReportExportFileName,
		ContentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
		Content:     content,
	}, nil
}

type inventoryReportExportRow struct {
	MaterialCode   string
	Brand          string
	Material       string
	Category       string
	Spec           string
	Length         string
	WarehouseName  string
	BatchNo        string
	Quantity       int
	QuantityUnit   string
	WeightTon      float64
	PieceWeightTon *float64
	Unit           string
}

func inventoryReportExportRowFrom(row InventoryReportResponse) inventoryReportExportRow {
	return inventoryReportExportRow{
		MaterialCode:   row.MaterialCode,
		Brand:          row.Brand,
		Material:       row.Material,
		Category:       row.Category,
		Spec:           row.Spec,
		Length:         row.Length,
		WarehouseName:  row.WarehouseName,
		BatchNo:        row.BatchNo,
		Quantity:       row.Quantity,
		QuantityUnit:   row.QuantityUnit,
		WeightTon:      row.WeightTon,
		PieceWeightTon: float64Ptr(row.PieceWeightTon),
		Unit:           row.Unit,
	}
}

type inventoryReportExportRecord struct {
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
	PieceWeightTon sql.NullFloat64
	Unit           sql.NullString
}

func (s InventoryReportService) listExportRows(ctx context.Context, filter InventoryReportFilter) ([]inventoryReportExportRow, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	cte, cteArgs := inventoryReportCTE(ctx)
	where, whereArgs := inventoryReportFilters(filter, len(cteArgs))
	args := append(cteArgs, whereArgs...)
	orderExpression := inventoryReportSortExpression("report", "", "")
	rows, err := s.db.Query(ctx, cte+`
		SELECT
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
			material.piece_weight_ton::float8 AS piece_weight_ton,
			report.unit
		FROM inventory report
		LEFT JOIN md_material material ON material.material_code = report.material_code
			AND material.deleted_flag = FALSE
		`+where+`
		ORDER BY `+orderExpression+`
	`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	content := []inventoryReportExportRow{}
	for rows.Next() {
		var record inventoryReportExportRecord
		err := rows.Scan(
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
			&record.PieceWeightTon,
			&record.Unit,
		)
		if err != nil {
			return nil, err
		}
		content = append(content, inventoryReportExportRowFromRecord(record))
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return content, nil
}

func inventoryReportExportRowFromRecord(record inventoryReportExportRecord) inventoryReportExportRow {
	return inventoryReportExportRow{
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
		PieceWeightTon: nullableFloat64Ptr(record.PieceWeightTon),
		Unit:           nullableString(record.Unit),
	}
}

func buildInventoryReportWorkbook(rows []inventoryReportExportRow) ([]byte, error) {
	sheetRows := make([][]cellValue, 0, len(rows)+1)
	sheetRows = append(sheetRows, []cellValue{
		{kind: cellString, text: "商品编码", style: 1},
		{kind: cellString, text: "品牌", style: 1},
		{kind: cellString, text: "材质", style: 1},
		{kind: cellString, text: "类别", style: 1},
		{kind: cellString, text: "规格", style: 1},
		{kind: cellString, text: "长度", style: 1},
		{kind: cellString, text: "仓库", style: 1},
		{kind: cellString, text: "批号", style: 1},
		{kind: cellString, text: "结存数量", style: 1},
		{kind: cellString, text: "数量单位", style: 1},
		{kind: cellString, text: "结存重量(吨)", style: 1},
		{kind: cellString, text: "件重(吨)", style: 1},
		{kind: cellString, text: "单位", style: 1},
	})
	for _, row := range rows {
		pieceWeightCell := cellValue{kind: cellBlank}
		if row.PieceWeightTon != nil {
			pieceWeightCell = cellValue{kind: cellNumber, number: *row.PieceWeightTon, style: 2}
		}
		sheetRows = append(sheetRows, []cellValue{
			{kind: cellString, text: row.MaterialCode},
			{kind: cellString, text: row.Brand},
			{kind: cellString, text: row.Material},
			{kind: cellString, text: row.Category},
			{kind: cellString, text: row.Spec},
			{kind: cellString, text: row.Length},
			{kind: cellString, text: row.WarehouseName},
			{kind: cellString, text: row.BatchNo},
			{kind: cellNumber, number: float64(row.Quantity)},
			{kind: cellString, text: row.QuantityUnit},
			{kind: cellNumber, number: row.WeightTon, style: 2},
			pieceWeightCell,
			{kind: cellString, text: row.Unit},
		})
	}
	return writeXLSX("Data", sheetRows)
}

type cellKind string

const (
	cellString cellKind = "string"
	cellNumber cellKind = "number"
	cellBlank  cellKind = "blank"
)

type cellValue struct {
	kind   cellKind
	text   string
	number float64
	style  int
}

func writeXLSX(sheetName string, rows [][]cellValue) ([]byte, error) {
	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	add := func(name string, content string) error {
		w, err := zw.Create(name)
		if err != nil {
			return err
		}
		_, err = w.Write([]byte(content))
		return err
	}
	sheets, sharedStrings, err := buildSheetXML(rows)
	if err != nil {
		return nil, err
	}
	if err := add("[Content_Types].xml", xlsxContentTypes); err != nil {
		return nil, err
	}
	if err := add("_rels/.rels", xlsxRels); err != nil {
		return nil, err
	}
	if err := add("docProps/app.xml", xlsxAppXML(sheetName)); err != nil {
		return nil, err
	}
	if err := add("docProps/core.xml", xlsxCoreXML()); err != nil {
		return nil, err
	}
	if err := add("xl/workbook.xml", xlsxWorkbookXML(sheetName)); err != nil {
		return nil, err
	}
	if err := add("xl/_rels/workbook.xml.rels", xlsxWorkbookRels); err != nil {
		return nil, err
	}
	if err := add("xl/styles.xml", xlsxStylesXML); err != nil {
		return nil, err
	}
	if err := add("xl/sharedStrings.xml", sharedStrings); err != nil {
		return nil, err
	}
	if err := add("xl/worksheets/sheet1.xml", sheets); err != nil {
		return nil, err
	}
	if err := zw.Close(); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func buildSheetXML(rows [][]cellValue) (string, string, error) {
	shared := []string{}
	indexes := map[string]int{}
	totalStrings := 0
	var sheet strings.Builder
	sheet.WriteString(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>`)
	sheet.WriteString(`<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">`)
	sheet.WriteString(`<sheetData>`)
	for rowIdx, row := range rows {
		sheet.WriteString(`<row r="`)
		sheet.WriteString(strconv.Itoa(rowIdx + 1))
		sheet.WriteString(`">`)
		for colIdx, cell := range row {
			ref := excelColumnName(colIdx+1) + strconv.Itoa(rowIdx+1)
			switch cell.kind {
			case cellString:
				totalStrings++
				idx, ok := indexes[cell.text]
				if !ok {
					idx = len(shared)
					indexes[cell.text] = idx
					shared = append(shared, cell.text)
				}
				sheet.WriteString(`<c r="`)
				sheet.WriteString(ref)
				if cell.style > 0 {
					sheet.WriteString(`" s="`)
					sheet.WriteString(strconv.Itoa(cell.style))
				}
				sheet.WriteString(`" t="s"><v>`)
				sheet.WriteString(strconv.Itoa(idx))
				sheet.WriteString(`</v></c>`)
			case cellNumber:
				sheet.WriteString(`<c r="`)
				sheet.WriteString(ref)
				if cell.style > 0 {
					sheet.WriteString(`" s="`)
					sheet.WriteString(strconv.Itoa(cell.style))
				}
				sheet.WriteString(`"><v>`)
				sheet.WriteString(formatXLSXNumber(cell.number))
				sheet.WriteString(`</v></c>`)
			case cellBlank:
				sheet.WriteString(`<c r="`)
				sheet.WriteString(ref)
				sheet.WriteString(`"/>`)
			default:
				return "", "", fmt.Errorf("unsupported cell kind: %s", cell.kind)
			}
		}
		sheet.WriteString(`</row>`)
	}
	sheet.WriteString(`</sheetData></worksheet>`)

	var sharedStrings strings.Builder
	sharedStrings.WriteString(`<?xml version="1.0" encoding="UTF-8" standalone="yes"?>`)
	sharedStrings.WriteString(`<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="`)
	sharedStrings.WriteString(strconv.Itoa(totalStrings))
	sharedStrings.WriteString(`" uniqueCount="`)
	sharedStrings.WriteString(strconv.Itoa(len(shared)))
	sharedStrings.WriteString(`">`)
	for _, value := range shared {
		sharedStrings.WriteString(`<si><t xml:space="preserve">`)
		sharedStrings.WriteString(xmlEscape(value))
		sharedStrings.WriteString(`</t></si>`)
	}
	sharedStrings.WriteString(`</sst>`)

	return sheet.String(), sharedStrings.String(), nil
}

func formatXLSXNumber(value float64) string {
	if value == math.Trunc(value) {
		return strconv.FormatInt(int64(value), 10)
	}
	return strconv.FormatFloat(value, 'f', -1, 64)
}

func float64Ptr(value float64) *float64 {
	return &value
}

func nullableFloat64Ptr(value sql.NullFloat64) *float64 {
	if !value.Valid {
		return nil
	}
	result := value.Float64
	return &result
}

func excelColumnName(index int) string {
	name := ""
	for index > 0 {
		index--
		name = string(rune('A'+(index%26))) + name
		index /= 26
	}
	return name
}

func xmlEscape(value string) string {
	return html.EscapeString(value)
}

const xlsxContentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
  <Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
  <Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
</Types>`

const xlsxRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>`

const xlsxWorkbookRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>`

const xlsxStylesXML = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="0.000"/>
  </numFmts>
  <fonts count="2">
    <font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
    <font><b/><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
  </cellXfs>
  <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
  <dxfs count="0"/>
  <tableStyles count="0" defaultTableStyle="TableStyleMedium2" defaultPivotStyle="PivotStyleLight16"/>
</styleSheet>`

func xlsxWorkbookXML(sheetName string) string {
	return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="` + xmlEscape(sheetName) + `" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>`
}

func xlsxAppXML(sheetName string) string {
	return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
  <Application>Leo Go</Application>
  <HeadingPairs>
    <vt:vector size="2" baseType="variant">
      <vt:variant><vt:lpstr>Worksheets</vt:lpstr></vt:variant>
      <vt:variant><vt:i4>1</vt:i4></vt:variant>
    </vt:vector>
  </HeadingPairs>
  <TitlesOfParts>
    <vt:vector size="1" baseType="lpstr">
      <vt:lpstr>` + xmlEscape(sheetName) + `</vt:lpstr>
    </vt:vector>
  </TitlesOfParts>
</Properties>`
}

func xlsxCoreXML() string {
	return `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:creator>Leo Go</dc:creator>
  <cp:lastModifiedBy>Leo Go</cp:lastModifiedBy>
</cp:coreProperties>`
}
