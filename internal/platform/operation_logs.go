package platform

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

type OperationLogService struct {
	db *pgxpool.Pool
}

func NewOperationLogService(db *pgxpool.Pool) OperationLogService {
	return OperationLogService{db: db}
}

func (s OperationLogService) Page(ctx context.Context, query PageQuery, filter OperationLogFilter) (PageResponse[OperationLogResponse], error) {
	if s.db == nil {
		return PageResponse[OperationLogResponse]{}, errors.New("database client is not configured")
	}
	where, args, err := operationLogFilters(filter)
	if err != nil {
		return PageResponse[OperationLogResponse]{}, err
	}
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM sys_operation_log WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[OperationLogResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"logNo":         "log_no",
		"operatorName":  "operator_name",
		"loginName":     "login_name",
		"moduleName":    "module_name",
		"actionType":    "action_type",
		"businessNo":    "business_no",
		"requestMethod": "request_method",
		"requestPath":   "request_path",
		"clientIp":      "client_ip",
		"resultStatus":  "result_status",
		"operationTime": "operation_time",
		"authType":      "auth_type",
	}, "operation_time")
	rows, err := s.db.Query(ctx, `
		SELECT id, log_no, operator_name, login_name, COALESCE(auth_type, ''),
		       module_name, action_type, business_no, record_id, module_key,
		       request_method, request_path, client_ip, result_status,
		       operation_time, remark
		  FROM sys_operation_log
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[OperationLogResponse]{}, err
	}
	defer rows.Close()
	content := []OperationLogResponse{}
	for rows.Next() {
		var row OperationLogResponse
		var businessNo, moduleKey, clientIP, remark sql.NullString
		var recordID sql.NullInt64
		if err := rows.Scan(
			&row.ID,
			&row.LogNo,
			&row.OperatorName,
			&row.LoginName,
			&row.AuthType,
			&row.ModuleName,
			&row.ActionType,
			&businessNo,
			&recordID,
			&moduleKey,
			&row.RequestMethod,
			&row.RequestPath,
			&clientIP,
			&row.ResultStatus,
			&row.OperationTime,
			&remark,
		); err != nil {
			return PageResponse[OperationLogResponse]{}, err
		}
		row.BusinessNo = nullableString(businessNo)
		row.ModuleKey = nullableString(moduleKey)
		row.ClientIP = nullableString(clientIP)
		row.Remark = nullableString(remark)
		if recordID.Valid {
			value := recordID.Int64
			row.RecordID = &value
		}
		content = append(content, row)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[OperationLogResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func operationLogFilters(filter OperationLogFilter) (string, []any, error) {
	where := "1 = 1"
	args := []any{}
	if strings.TrimSpace(filter.Keyword) != "" {
		args = append(args, "%"+strings.TrimSpace(filter.Keyword)+"%")
		where += " AND (log_no LIKE $" + strconvArg(len(args)) +
			" OR operator_name LIKE $" + strconvArg(len(args)) +
			" OR login_name LIKE $" + strconvArg(len(args)) +
			" OR COALESCE(business_no, '') LIKE $" + strconvArg(len(args)) +
			" OR request_path LIKE $" + strconvArg(len(args)) +
			" OR COALESCE(remark, '') LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(filter.ModuleName) != "" {
		moduleName := strings.TrimSpace(filter.ModuleName)
		if moduleName == "角色权限配置" {
			args = append(args, []string{"角色权限配置", "角色设置"})
			where += " AND module_name = ANY($" + strconvArg(len(args)) + ")"
		} else {
			args = append(args, moduleName)
			where += " AND module_name = $" + strconvArg(len(args))
		}
	}
	if strings.TrimSpace(filter.ActionType) != "" {
		args = append(args, strings.TrimSpace(filter.ActionType))
		where += " AND action_type = $" + strconvArg(len(args))
	}
	if strings.TrimSpace(filter.ResultStatus) != "" {
		resultStatus := strings.TrimSpace(filter.ResultStatus)
		if resultStatus != "成功" && resultStatus != "失败" {
			return "", nil, NewAuthError(AuthErrorValidation, "resultStatus 不合法")
		}
		args = append(args, resultStatus)
		where += " AND result_status = $" + strconvArg(len(args))
	}
	if strings.TrimSpace(filter.AuthType) != "" {
		args = append(args, strings.TrimSpace(filter.AuthType))
		where += " AND auth_type = $" + strconvArg(len(args))
	}
	if filter.RecordID != nil {
		args = append(args, *filter.RecordID)
		where += " AND record_id = $" + strconvArg(len(args))
	}
	start, err := parseOperationLogDate(filter.StartTime, "startTime")
	if err != nil {
		return "", nil, err
	}
	end, err := parseOperationLogDate(filter.EndTime, "endTime")
	if err != nil {
		return "", nil, err
	}
	if start != nil && end != nil && start.After(*end) {
		return "", nil, NewAuthError(AuthErrorValidation, "startTime 不能晚于 endTime")
	}
	if start != nil {
		args = append(args, *start)
		where += " AND operation_time >= $" + strconvArg(len(args))
	}
	if end != nil {
		endExclusive := end.AddDate(0, 0, 1)
		args = append(args, endExclusive)
		where += " AND operation_time < $" + strconvArg(len(args))
	}
	return where, args, nil
}

func parseOperationLogDate(value string, fieldName string) (*time.Time, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil, nil
	}
	parsed, err := time.Parse("2006-01-02", value)
	if err != nil {
		return nil, NewAuthError(AuthErrorValidation, fieldName+" 格式不合法")
	}
	return &parsed, nil
}
