package platform

import (
	"crypto/rand"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"hash/fnv"
	"math"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

const (
	defaultPageSize = 20
	maxPageSize     = 200
)

var adminLoginNamePattern = regexp.MustCompile(`^[A-Za-z0-9_.@-]+$`)

func NormalizePageQuery(page, size int, sortBy, direction string) PageQuery {
	if page < 0 {
		page = 0
	}
	if size <= 0 {
		size = defaultPageSize
	}
	if size > maxPageSize {
		size = maxPageSize
	}
	direction = strings.ToLower(strings.TrimSpace(direction))
	if direction != "asc" {
		direction = "desc"
	}
	return PageQuery{
		Page:      page,
		Size:      size,
		SortBy:    strings.TrimSpace(sortBy),
		Direction: direction,
	}
}

func sqlLimitOffset(query PageQuery) (int, int) {
	return query.Size, query.Page * query.Size
}

func sqlOrderBy(sortBy string, direction string, allowed map[string]string, fallback string) string {
	column := allowed[strings.TrimSpace(sortBy)]
	if column == "" {
		column = fallback
	}
	if strings.ToLower(strings.TrimSpace(direction)) == "asc" {
		return column + " ASC"
	}
	return column + " DESC"
}

func likeKeyword(keyword string) string {
	return "%" + strings.ToLower(strings.TrimSpace(keyword)) + "%"
}

func nullableString(value sql.NullString) string {
	if !value.Valid {
		return ""
	}
	return value.String
}

func nullableTime(value sql.NullTime) *time.Time {
	if !value.Valid {
		return nil
	}
	result := value.Time
	return &result
}

func optionalNullString(value string) sql.NullString {
	value = strings.TrimSpace(value)
	if value == "" {
		return sql.NullString{}
	}
	return sql.NullString{String: value, Valid: true}
}

func joinCSV(values []string) string {
	normalized := normalizeStringList(values)
	return strings.Join(normalized, ",")
}

func splitCSV(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if part != "" {
			result = append(result, part)
		}
	}
	return result
}

func normalizeStringList(values []string) []string {
	seen := map[string]struct{}{}
	result := make([]string, 0, len(values))
	for _, value := range values {
		normalized := strings.TrimSpace(value)
		if normalized == "" {
			continue
		}
		if _, ok := seen[normalized]; ok {
			continue
		}
		seen[normalized] = struct{}{}
		result = append(result, normalized)
	}
	return result
}

func normalizedStatus(value string, fallback string) (string, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		value = fallback
	}
	if value != "正常" && value != "禁用" {
		return "", NewAuthError(AuthErrorValidation, "状态不合法")
	}
	return value, nil
}

func normalizeUserStatusForDB(value string) (string, error) {
	status, err := normalizedStatus(value, "正常")
	if err != nil {
		return "", err
	}
	if status == "禁用" {
		return "DISABLED", nil
	}
	return "NORMAL", nil
}

func displayUserStatus(value string) string {
	switch strings.ToUpper(strings.TrimSpace(value)) {
	case "DISABLED":
		return "禁用"
	default:
		return "正常"
	}
}

func hashPermissionID(resource string, action string) int64 {
	h := fnv.New32a()
	_, _ = h.Write([]byte(resource + ":" + action))
	return int64(h.Sum32())
}

func containsLower(source string, keyword string) bool {
	return strings.Contains(strings.ToLower(source), strings.ToLower(strings.TrimSpace(keyword)))
}

func clampExpireDays(value *int64) (*int64, error) {
	if value == nil {
		return nil, nil
	}
	if *value <= 0 {
		return nil, NewAuthError(AuthErrorValidation, "有效天数必须大于0")
	}
	if *value > 3650 {
		return nil, NewAuthError(AuthErrorValidation, "有效天数不能超过3650")
	}
	return value, nil
}

func randomURLToken(prefix string, byteLength int) (string, error) {
	raw := make([]byte, byteLength)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return prefix + base64.RawURLEncoding.EncodeToString(raw), nil
}

func randomInitialPassword() (string, error) {
	raw := make([]byte, 9)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return "Leo@" + hex.EncodeToString(raw)[:12], nil
}

func parseFloatOrZero(value string) float64 {
	parsed, err := strconv.ParseFloat(strings.TrimSpace(value), 64)
	if err != nil || math.IsNaN(parsed) || math.IsInf(parsed, 0) {
		return 0
	}
	return parsed
}

func sortedCatalogActions() []CatalogAction {
	seen := map[string]CatalogAction{}
	for _, entry := range PermissionCatalog() {
		for _, action := range entry.Actions {
			seen[action.Code] = action
		}
	}
	actions := make([]CatalogAction, 0, len(seen))
	for _, action := range seen {
		actions = append(actions, action)
	}
	sort.Slice(actions, func(i, j int) bool {
		return actions[i].Code < actions[j].Code
	})
	return actions
}
