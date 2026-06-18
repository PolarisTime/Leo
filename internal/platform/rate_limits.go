package platform

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"

	"github.com/jackc/pgx/v5/pgxpool"
)

type RateLimitAdminService struct {
	db *pgxpool.Pool
}

func NewRateLimitAdminService(db *pgxpool.Pool) RateLimitAdminService {
	return RateLimitAdminService{db: db}
}

func (s RateLimitAdminService) ListRules(ctx context.Context) ([]map[string]any, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, rule_key, rule_type, rate, capacity,
		       tokens_per_request, priority, enabled, created_at, updated_at
		  FROM sys_rate_limit_rule
		 ORDER BY priority, rule_key
	`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := []map[string]any{}
	for rows.Next() {
		var (
			id               int64
			ruleKey          string
			ruleType         string
			rate             float64
			capacity         int
			tokensPerRequest int
			priority         int
			enabled          bool
			createdAt        sql.NullTime
			updatedAt        sql.NullTime
		)
		if err := rows.Scan(
			&id, &ruleKey, &ruleType, &rate, &capacity,
			&tokensPerRequest, &priority, &enabled, &createdAt, &updatedAt,
		); err != nil {
			return nil, err
		}
		result = append(result, map[string]any{
			"id":                 strconv.FormatInt(id, 10),
			"rule_key":           ruleKey,
			"rule_type":          ruleType,
			"rate":               rate,
			"capacity":           capacity,
			"tokens_per_request": tokensPerRequest,
			"priority":           priority,
			"enabled":            enabled,
			"created_at":         rateLimitLocalDateTimeMillis(createdAt),
			"updated_at":         rateLimitLocalDateTimeMillis(updatedAt),
		})
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return result, nil
}

func (s RateLimitAdminService) UpdateRule(ctx context.Context, id int64, body map[string]any) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	if value, ok := body["enabled"]; ok {
		enabled, _ := value.(bool)
		_, err := s.db.Exec(ctx, "UPDATE sys_rate_limit_rule SET enabled = $1 WHERE id = $2", enabled, id)
		if err != nil {
			return err
		}
	}
	if value, ok := body["rate"]; ok {
		rate, err := rateLimitFloat64(value)
		if err != nil {
			return err
		}
		_, err = s.db.Exec(ctx, "UPDATE sys_rate_limit_rule SET rate = $1 WHERE id = $2", rate, id)
		if err != nil {
			return err
		}
	}
	if value, ok := body["capacity"]; ok {
		capacity, err := rateLimitInt(value)
		if err != nil {
			return err
		}
		_, err = s.db.Exec(ctx, "UPDATE sys_rate_limit_rule SET capacity = $1 WHERE id = $2", capacity, id)
		if err != nil {
			return err
		}
	}
	if value, ok := body["tokens_per_request"]; ok {
		tokensPerRequest, err := rateLimitInt(value)
		if err != nil {
			return err
		}
		_, err = s.db.Exec(ctx, "UPDATE sys_rate_limit_rule SET tokens_per_request = $1 WHERE id = $2", tokensPerRequest, id)
		if err != nil {
			return err
		}
	}
	return nil
}

func rateLimitLocalDateTimeMillis(value sql.NullTime) any {
	if !value.Valid {
		return nil
	}
	return javaLocalDateTimeMillis(value.Time)
}

func rateLimitFloat64(value any) (float64, error) {
	switch typed := value.(type) {
	case json.Number:
		return typed.Float64()
	case float64:
		return typed, nil
	case float32:
		return float64(typed), nil
	case int:
		return float64(typed), nil
	case int8:
		return float64(typed), nil
	case int16:
		return float64(typed), nil
	case int32:
		return float64(typed), nil
	case int64:
		return float64(typed), nil
	case uint:
		return float64(typed), nil
	case uint8:
		return float64(typed), nil
	case uint16:
		return float64(typed), nil
	case uint32:
		return float64(typed), nil
	case uint64:
		return float64(typed), nil
	default:
		return 0, fmt.Errorf("rate limit value is not a JSON number: %T", value)
	}
}

func rateLimitInt(value any) (int, error) {
	number, err := rateLimitFloat64(value)
	if err != nil {
		return 0, err
	}
	return int(number), nil
}
