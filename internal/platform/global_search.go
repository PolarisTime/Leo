package platform

import (
	"context"
	"fmt"
	"sort"
	"strconv"
	"strings"

	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	globalSearchMaxTotalLimit = 50
	globalSearchMaxPerModule  = 6
)

type GlobalSearchService struct {
	db *pgxpool.Pool
}

type GlobalSearchResult struct {
	ModuleKey        string `json:"moduleKey"`
	Title            string `json:"title"`
	TrackID          string `json:"trackId"`
	PrimaryNo        string `json:"primaryNo"`
	Summary          string `json:"summary"`
	MatchedByTrackID bool   `json:"matchedByTrackId"`
}

type searchModule struct {
	Key       string
	Title     string
	Table     string
	PrimaryNo string
	Summary   []string
}

var globalSearchModules = []searchModule{
	{Key: "purchase-order", Title: "采购订单", Table: "po_purchase_order", PrimaryNo: "order_no", Summary: []string{"supplier_name", "status"}},
	{Key: "purchase-inbound", Title: "采购入库", Table: "po_purchase_inbound", PrimaryNo: "inbound_no", Summary: []string{"supplier_name", "warehouse_name", "status"}},
	{Key: "sales-order", Title: "销售订单", Table: "so_sales_order", PrimaryNo: "order_no", Summary: []string{"customer_name", "project_name", "status"}},
	{Key: "sales-outbound", Title: "销售出库", Table: "so_sales_outbound", PrimaryNo: "outbound_no", Summary: []string{"customer_name", "project_name", "status"}},
	{Key: "freight-bill", Title: "物流单", Table: "lg_freight_bill", PrimaryNo: "bill_no", Summary: []string{"carrier_name", "customer_name", "project_name", "status"}},
	{Key: "purchase-contract", Title: "采购合同", Table: "ct_purchase_contract", PrimaryNo: "contract_no", Summary: []string{"supplier_name", "buyer_name", "status"}},
	{Key: "sales-contract", Title: "销售合同", Table: "ct_sales_contract", PrimaryNo: "contract_no", Summary: []string{"customer_name", "project_name", "status"}},
	{Key: "supplier-statement", Title: "供应商对账单", Table: "st_supplier_statement", PrimaryNo: "statement_no", Summary: []string{"supplier_name", "status"}},
	{Key: "customer-statement", Title: "客户对账单", Table: "st_customer_statement", PrimaryNo: "statement_no", Summary: []string{"customer_name", "project_name", "status"}},
	{Key: "freight-statement", Title: "物流对账单", Table: "st_freight_statement", PrimaryNo: "statement_no", Summary: []string{"carrier_name", "status"}},
	{Key: "receipt", Title: "收款单", Table: "fm_receipt", PrimaryNo: "receipt_no", Summary: []string{"customer_name", "project_name", "status"}},
	{Key: "payment", Title: "付款单", Table: "fm_payment", PrimaryNo: "payment_no", Summary: []string{"counterparty_name", "business_type", "status"}},
	{Key: "invoice-receipt", Title: "收票单", Table: "fm_invoice_receipt", PrimaryNo: "receive_no", Summary: []string{"supplier_name", "status"}},
	{Key: "invoice-issue", Title: "开票单", Table: "fm_invoice_issue", PrimaryNo: "issue_no", Summary: []string{"customer_name", "status"}},
}

func NewGlobalSearchService(db *pgxpool.Pool) GlobalSearchService {
	return GlobalSearchService{db: db}
}

func (s GlobalSearchService) Search(ctx context.Context, userID int64, keyword string, limit int, moduleKeys []string) ([]GlobalSearchResult, error) {
	keyword = strings.TrimSpace(keyword)
	if keyword == "" {
		return []GlobalSearchResult{}, nil
	}
	if s.db == nil {
		return []GlobalSearchResult{}, fmt.Errorf("database client is not configured")
	}
	if limit <= 0 {
		limit = 20
	}
	if limit > globalSearchMaxTotalLimit {
		limit = globalSearchMaxTotalLimit
	}
	perModuleLimit := limit
	if perModuleLimit > globalSearchMaxPerModule {
		perModuleLimit = globalSearchMaxPerModule
	}
	requested := normalizeModuleKeySet(moduleKeys)
	trackIDSearch := isLikelyTrackID(keyword)
	results := []GlobalSearchResult{}
	for _, module := range globalSearchModules {
		if len(requested) > 0 {
			if _, ok := requested[module.Key]; !ok {
				continue
			}
		}
		if ok, err := s.canReadModule(ctx, userID, module.Key); err != nil {
			return nil, err
		} else if !ok {
			continue
		}
		moduleResults, err := s.searchModule(ctx, module, keyword, trackIDSearch, perModuleLimit)
		if err != nil {
			continue
		}
		results = append(results, moduleResults...)
		if trackIDSearch && len(results) > 0 {
			break
		}
	}
	sort.Slice(results, func(i, j int) bool {
		if results[i].MatchedByTrackID != results[j].MatchedByTrackID {
			return results[i].MatchedByTrackID
		}
		return strings.ToLower(results[i].PrimaryNo) < strings.ToLower(results[j].PrimaryNo)
	})
	if len(results) > limit {
		results = results[:limit]
	}
	return results, nil
}

func (s GlobalSearchService) canReadModule(ctx context.Context, userID int64, resource string) (bool, error) {
	var allowed bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_user_role user_role
			  JOIN sys_role role
			    ON role.id = user_role.role_id
			   AND role.deleted_flag = false
			   AND role.status = '正常'
			  JOIN sys_role_permission permission
			    ON permission.role_id = role.id
			   AND permission.deleted_flag = false
			 WHERE user_role.user_id = $1
			   AND user_role.deleted_flag = false
			   AND lower(permission.resource_code) = $2
			   AND lower(permission.action_code) IN ('read', 'view')
		)
	`, userID, normalizeResourceCode(resource)).Scan(&allowed)
	return allowed, err
}

func (s GlobalSearchService) searchModule(ctx context.Context, module searchModule, keyword string, trackIDSearch bool, limit int) ([]GlobalSearchResult, error) {
	selectedColumns := append([]string{module.PrimaryNo}, module.Summary...)
	args := []any{}
	where := "deleted_flag = false"
	if trackIDSearch {
		id, err := strconv.ParseInt(keyword, 10, 64)
		if err != nil {
			return []GlobalSearchResult{}, nil
		}
		where += " AND id = $1"
		args = append(args, id)
	} else {
		likeArgs := []string{}
		for _, col := range selectedColumns {
			args = append(args, "%"+keyword+"%")
			likeArgs = append(likeArgs, fmt.Sprintf("COALESCE(%s::text, '') ILIKE $%d", col, len(args)))
		}
		where += " AND (" + strings.Join(likeArgs, " OR ") + ")"
	}
	args = append(args, limit)
	query := fmt.Sprintf(
		"SELECT id, %s FROM %s WHERE %s ORDER BY %s LIMIT $%d",
		strings.Join(selectedColumns, ", "),
		module.Table,
		where,
		module.PrimaryNo,
		len(args),
	)
	rows, err := s.db.Query(ctx, query, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	results := []GlobalSearchResult{}
	for rows.Next() {
		values := make([]*string, len(selectedColumns))
		scanTargets := make([]any, 0, len(selectedColumns)+1)
		var id int64
		scanTargets = append(scanTargets, &id)
		for i := range values {
			scanTargets = append(scanTargets, &values[i])
		}
		if err := rows.Scan(scanTargets...); err != nil {
			return nil, err
		}
		primaryNo := stringValue(values[0])
		if primaryNo == "" {
			primaryNo = strconv.FormatInt(id, 10)
		}
		summary := []string{}
		for _, value := range values[1:] {
			text := stringValue(value)
			if text != "" {
				summary = append(summary, text)
			}
			if len(summary) >= 3 {
				break
			}
		}
		results = append(results, GlobalSearchResult{
			ModuleKey:        module.Key,
			Title:            module.Title,
			TrackID:          strconv.FormatInt(id, 10),
			PrimaryNo:        primaryNo,
			Summary:          strings.Join(summary, " / "),
			MatchedByTrackID: trackIDSearch && strconv.FormatInt(id, 10) == keyword,
		})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return results, nil
}

func normalizeModuleKeySet(moduleKeys []string) map[string]struct{} {
	result := map[string]struct{}{}
	for _, item := range moduleKeys {
		for _, part := range strings.Split(item, ",") {
			part = strings.TrimSpace(part)
			if part != "" {
				result[part] = struct{}{}
			}
		}
	}
	return result
}

func isLikelyTrackID(value string) bool {
	value = strings.TrimSpace(value)
	if len(value) < 12 {
		return false
	}
	for _, r := range value {
		if r < '0' || r > '9' {
			return false
		}
	}
	return true
}

func stringValue(value *string) string {
	if value == nil {
		return ""
	}
	return strings.TrimSpace(*value)
}
