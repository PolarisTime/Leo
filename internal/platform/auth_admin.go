package platform

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/bcrypt"
)

const (
	apiKeyRawPrefix      = "leo_"
	apiKeyRandomBytes    = 32
	apiKeyPrefixLength   = 8
	apiKeyStatusActive   = "有效"
	apiKeyStatusDisabled = "已禁用"
	apiKeyStatusExpired  = "已过期"
	apiKeyUsageAll       = "全部接口"
	apiKeyUsageReadOnly  = "只读接口"
	apiKeyUsageBusiness  = "业务接口"
	refreshRevokeManual  = "MANUAL"
)

type UserAccountAdminService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
	auth        *AuthService
}

func NewUserAccountAdminService(db *pgxpool.Pool, auth *AuthService, machineID int64) UserAccountAdminService {
	return UserAccountAdminService{db: db, auth: auth, idGenerator: NewIDGenerator(machineID)}
}

func (s UserAccountAdminService) Page(ctx context.Context, query PageQuery, keyword string, status string) (PageResponse[UserAccountAdminResponse], error) {
	if s.db == nil {
		return PageResponse[UserAccountAdminResponse]{}, errors.New("database client is not configured")
	}
	where, args, err := userAccountFilters(keyword, status)
	if err != nil {
		return PageResponse[UserAccountAdminResponse]{}, err
	}
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM sys_user user_account LEFT JOIN sys_department department ON department.id = user_account.department_id WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[UserAccountAdminResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"loginName":      "user_account.login_name",
		"userName":       "user_account.user_name",
		"mobile":         "user_account.mobile",
		"departmentName": "department.department_name",
		"status":         "user_account.status",
	}, "user_account.id")
	rows, err := s.db.Query(ctx, `
		SELECT user_account.id, user_account.login_name, user_account.user_name,
		       COALESCE(user_account.mobile, ''), user_account.department_id,
		       COALESCE(department.department_name, ''),
		       COALESCE(user_account.data_scope, ''), COALESCE(user_account.permission_summary, ''),
		       user_account.last_login_date, user_account.status, COALESCE(user_account.remark, ''),
		       COALESCE(user_account.totp_enabled, false)
		  FROM sys_user user_account
		  LEFT JOIN sys_department department
		    ON department.id = user_account.department_id
		   AND department.deleted_flag = false
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[UserAccountAdminResponse]{}, err
	}
	defer rows.Close()
	content := []UserAccountAdminResponse{}
	for rows.Next() {
		row, err := scanUserAccountAdmin(rows)
		if err != nil {
			return PageResponse[UserAccountAdminResponse]{}, err
		}
		content = append(content, row)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[UserAccountAdminResponse]{}, err
	}
	if err := s.fillUserRoles(ctx, content); err != nil {
		return PageResponse[UserAccountAdminResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s UserAccountAdminService) Detail(ctx context.Context, id int64) (UserAccountAdminResponse, error) {
	if s.db == nil {
		return UserAccountAdminResponse{}, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT user_account.id, user_account.login_name, user_account.user_name,
		       COALESCE(user_account.mobile, ''), user_account.department_id,
		       COALESCE(department.department_name, ''),
		       COALESCE(user_account.data_scope, ''), COALESCE(user_account.permission_summary, ''),
		       user_account.last_login_date, user_account.status, COALESCE(user_account.remark, ''),
		       COALESCE(user_account.totp_enabled, false)
		  FROM sys_user user_account
		  LEFT JOIN sys_department department
		    ON department.id = user_account.department_id
		   AND department.deleted_flag = false
		 WHERE user_account.id = $1
		   AND user_account.deleted_flag = false
		 LIMIT 1
	`, id)
	if err != nil {
		return UserAccountAdminResponse{}, err
	}
	defer rows.Close()
	if !rows.Next() {
		if err := rows.Err(); err != nil {
			return UserAccountAdminResponse{}, err
		}
		return UserAccountAdminResponse{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	row, err := scanUserAccountAdmin(rows)
	if err != nil {
		return UserAccountAdminResponse{}, err
	}
	if rows.Next() {
		return UserAccountAdminResponse{}, NewAuthError(AuthErrorInternal, "用户数据异常")
	}
	content := []UserAccountAdminResponse{row}
	if err := s.fillUserRoles(ctx, content); err != nil {
		return UserAccountAdminResponse{}, err
	}
	return content[0], nil
}

func (s UserAccountAdminService) CheckLoginNameAvailability(ctx context.Context, loginName string, excludeUserID *int64) (LoginNameAvailabilityResponse, error) {
	if s.db == nil {
		return LoginNameAvailabilityResponse{}, errors.New("database client is not configured")
	}
	loginName = normalizeLoginName(loginName)
	if loginName == "" {
		return LoginNameAvailabilityResponse{Available: false, Message: "登录账号不能为空"}, nil
	}
	if !adminLoginNamePattern.MatchString(loginName) {
		return LoginNameAvailabilityResponse{Available: false, Message: "登录账号格式不正确"}, nil
	}
	args := []any{loginName}
	where := "lower(login_name) = $1 AND deleted_flag = false"
	if excludeUserID != nil && *excludeUserID > 0 {
		args = append(args, *excludeUserID)
		where += " AND id <> $2"
	}
	var exists bool
	if err := s.db.QueryRow(ctx, "SELECT EXISTS (SELECT 1 FROM sys_user WHERE "+where+")", args...).Scan(&exists); err != nil {
		return LoginNameAvailabilityResponse{}, err
	}
	if exists {
		return LoginNameAvailabilityResponse{Available: false, Message: "登录账号已存在"}, nil
	}
	return LoginNameAvailabilityResponse{Available: true, Message: "登录账号可用"}, nil
}

func (s UserAccountAdminService) Create(ctx context.Context, operatorID int64, request UserAccountAdminRequest) (UserAccountCreateResponse, error) {
	if s.db == nil {
		return UserAccountCreateResponse{}, errors.New("database client is not configured")
	}
	loginName, err := normalizeAdminLoginName(request.LoginName)
	if err != nil {
		return UserAccountCreateResponse{}, err
	}
	if available, err := s.CheckLoginNameAvailability(ctx, loginName, nil); err != nil {
		return UserAccountCreateResponse{}, err
	} else if !available.Available {
		return UserAccountCreateResponse{}, NewAuthError(AuthErrorBusiness, available.Message)
	}
	initialPassword := strings.TrimSpace(request.Password)
	if initialPassword == "" {
		initialPassword, err = randomInitialPassword()
		if err != nil {
			return UserAccountCreateResponse{}, err
		}
	}
	hash, err := bcrypt.GenerateFromPassword([]byte(initialPassword), bcrypt.DefaultCost)
	if err != nil {
		return UserAccountCreateResponse{}, err
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return UserAccountCreateResponse{}, err
	}
	defer tx.Rollback(ctx)
	userID := s.idGenerator.Next()
	status, err := normalizeUserStatusForDB(request.Status)
	if err != nil {
		return UserAccountCreateResponse{}, err
	}
	_, err = tx.Exec(ctx, `
		INSERT INTO sys_user (
			id, login_name, password_hash, user_name, mobile, department_id,
			data_scope, permission_summary, status, remark, created_by, created_name,
			deleted_flag, require_totp_setup, version, preferences_json
		) VALUES ($1, $2, $3, $4, $5, $6, $7, '', $8, $9, $10, 'system', false, false, 0, '{}'::jsonb)
	`, userID, loginName, string(hash), strings.TrimSpace(request.UserName), optionalNullString(request.Mobile), request.DepartmentID, normalizeDataScopeForDisplay(request.DataScope), status, strings.TrimSpace(request.Remark), operatorID)
	if err != nil {
		return UserAccountCreateResponse{}, err
	}
	if err := s.replaceUserRolesWithQuerier(ctx, tx, userID, request.RoleIDs, request.RoleNames); err != nil {
		return UserAccountCreateResponse{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return UserAccountCreateResponse{}, err
	}
	user, err := s.Detail(ctx, userID)
	if err != nil {
		return UserAccountCreateResponse{}, err
	}
	return UserAccountCreateResponse{User: user, InitialPassword: initialPassword}, nil
}

func (s UserAccountAdminService) Update(ctx context.Context, id int64, request UserAccountAdminRequest) (UserAccountAdminResponse, error) {
	if s.db == nil {
		return UserAccountAdminResponse{}, errors.New("database client is not configured")
	}
	if _, err := s.Detail(ctx, id); err != nil {
		return UserAccountAdminResponse{}, err
	}
	loginName, err := normalizeAdminLoginName(request.LoginName)
	if err != nil {
		return UserAccountAdminResponse{}, err
	}
	if available, err := s.CheckLoginNameAvailability(ctx, loginName, &id); err != nil {
		return UserAccountAdminResponse{}, err
	} else if !available.Available {
		return UserAccountAdminResponse{}, NewAuthError(AuthErrorBusiness, available.Message)
	}
	status, err := normalizeUserStatusForDB(request.Status)
	if err != nil {
		return UserAccountAdminResponse{}, err
	}
	if err := s.assertNotLastActiveAdmin(ctx, id, status, request.RoleIDs, request.RoleNames, false); err != nil {
		return UserAccountAdminResponse{}, err
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return UserAccountAdminResponse{}, err
	}
	defer tx.Rollback(ctx)
	_, err = tx.Exec(ctx, `
		UPDATE sys_user
		   SET login_name = $1,
		       user_name = $2,
		       mobile = $3,
		       department_id = $4,
		       data_scope = $5,
		       status = $6,
		       remark = $7,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $8
		   AND deleted_flag = false
	`, loginName, strings.TrimSpace(request.UserName), optionalNullString(request.Mobile), request.DepartmentID, normalizeDataScopeForDisplay(request.DataScope), status, strings.TrimSpace(request.Remark), id)
	if err != nil {
		return UserAccountAdminResponse{}, err
	}
	if request.RoleIDs != nil || request.RoleNames != nil {
		if err := s.replaceUserRolesWithQuerier(ctx, tx, id, request.RoleIDs, request.RoleNames); err != nil {
			return UserAccountAdminResponse{}, err
		}
	}
	if err := tx.Commit(ctx); err != nil {
		return UserAccountAdminResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s UserAccountAdminService) Delete(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	if err := s.assertNotLastActiveAdmin(ctx, id, "DISABLED", nil, nil, true); err != nil {
		return err
	}
	command, err := s.db.Exec(ctx, `
		UPDATE sys_user
		   SET deleted_flag = true,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id)
	if err != nil {
		return err
	}
	if command.RowsAffected() == 0 {
		return NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	return nil
}

func (s UserAccountAdminService) Preferences(ctx context.Context, userID int64) (UserAccountPreferencesPayload, error) {
	if s.db == nil {
		return UserAccountPreferencesPayload{}, errors.New("database client is not configured")
	}
	var raw sql.NullString
	err := s.db.QueryRow(ctx, `
		SELECT COALESCE(preferences_json::text, '{}')
		  FROM sys_user
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, userID).Scan(&raw)
	if errors.Is(err, pgx.ErrNoRows) {
		return UserAccountPreferencesPayload{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	if err != nil {
		return UserAccountPreferencesPayload{}, err
	}
	return readPreferencesPayload(raw.String), nil
}

func (s UserAccountAdminService) SavePreferences(ctx context.Context, userID int64, request UserAccountPreferencesPayload) (UserAccountPreferencesPayload, error) {
	if s.db == nil {
		return UserAccountPreferencesPayload{}, errors.New("database client is not configured")
	}
	normalized := normalizePreferencesPayloadFromRequest(request)
	raw, err := json.Marshal(normalized)
	if err != nil {
		return UserAccountPreferencesPayload{}, err
	}
	command, err := s.db.Exec(ctx, `
		UPDATE sys_user
		   SET preferences_json = $1::jsonb,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $2
		   AND deleted_flag = false
	`, string(raw), userID)
	if err != nil {
		return UserAccountPreferencesPayload{}, err
	}
	if command.RowsAffected() == 0 {
		return UserAccountPreferencesPayload{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	return normalized, nil
}

func (s UserAccountAdminService) Setup2FA(ctx context.Context, id int64) (TotpSetupResponse, error) {
	if s.auth == nil {
		return TotpSetupResponse{}, NewAuthError(AuthErrorInternal, "认证服务未配置")
	}
	return s.auth.SetupTotp(ctx, id)
}

func (s UserAccountAdminService) Enable2FA(ctx context.Context, id int64, request TotpEnableRequest) (UserAccountAdminResponse, error) {
	if s.auth == nil {
		return UserAccountAdminResponse{}, NewAuthError(AuthErrorInternal, "认证服务未配置")
	}
	if _, err := s.auth.EnableTotp(ctx, id, request.TotpCode); err != nil {
		return UserAccountAdminResponse{}, err
	}
	return s.Detail(ctx, id)
}

func (s UserAccountAdminService) Disable2FA(ctx context.Context, id int64) (UserAccountAdminResponse, error) {
	if s.auth == nil {
		return UserAccountAdminResponse{}, NewAuthError(AuthErrorInternal, "认证服务未配置")
	}
	if _, err := s.auth.DisableTotp(ctx, id); err != nil {
		return UserAccountAdminResponse{}, err
	}
	return s.Detail(ctx, id)
}

type RefreshTokenAdminService struct {
	db    *pgxpool.Pool
	redis *redis.Client
}

func NewRefreshTokenAdminService(db *pgxpool.Pool, redis *redis.Client) RefreshTokenAdminService {
	return RefreshTokenAdminService{db: db, redis: redis}
}

func (s RefreshTokenAdminService) Page(ctx context.Context, query PageQuery, keyword string) (PageResponse[RefreshTokenAdminResponse], error) {
	if s.db == nil {
		return PageResponse[RefreshTokenAdminResponse]{}, errors.New("database client is not configured")
	}
	where := "token.deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, likeKeyword(keyword))
		where += " AND (lower(token.token_id) LIKE $" + strconvArg(len(args)) +
			" OR lower(COALESCE(token.login_ip, '')) LIKE $" + strconvArg(len(args)) +
			" OR lower(COALESCE(token.device_info, '')) LIKE $" + strconvArg(len(args)) + ")"
	}
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM auth_refresh_token token WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[RefreshTokenAdminResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"tokenId":   "token.token_id",
		"loginIp":   "token.login_ip",
		"expiresAt": "token.expires_at",
		"createdAt": "token.created_at",
	}, "token.id")
	rows, err := s.db.Query(ctx, `
		SELECT token.id, token.user_id, COALESCE(user_account.login_name, token.user_id::text),
		       COALESCE(user_account.user_name, '--'), token.token_id,
		       COALESCE(token.login_ip, ''), COALESCE(token.device_info, ''),
		       token.created_at, token.expires_at, token.revoked_at
		  FROM auth_refresh_token token
		  LEFT JOIN sys_user user_account
		    ON user_account.id = token.user_id
		   AND user_account.deleted_flag = false
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[RefreshTokenAdminResponse]{}, err
	}
	defer rows.Close()
	content := []RefreshTokenAdminResponse{}
	for rows.Next() {
		item, err := scanRefreshTokenAdmin(rows)
		if err != nil {
			return PageResponse[RefreshTokenAdminResponse]{}, err
		}
		if lastActiveAt, ok := s.sessionLastActive(ctx, item.TokenID); ok {
			item.LastActiveAt = &lastActiveAt
		}
		item.Online = item.Status == "有效" && item.LastActiveAt != nil
		content = append(content, item)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[RefreshTokenAdminResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s RefreshTokenAdminService) Summary(ctx context.Context) (RefreshTokenSessionSummaryResponse, error) {
	if s.db == nil {
		return RefreshTokenSessionSummaryResponse{}, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT user_id, token_id
		  FROM auth_refresh_token
		 WHERE deleted_flag = false
		   AND revoked_at IS NULL
		   AND expires_at > CURRENT_TIMESTAMP
	`)
	if err != nil {
		return RefreshTokenSessionSummaryResponse{}, err
	}
	defer rows.Close()
	var active int64
	onlineUsers := map[int64]struct{}{}
	var onlineSessions int64
	for rows.Next() {
		var userID int64
		var tokenID string
		if err := rows.Scan(&userID, &tokenID); err != nil {
			return RefreshTokenSessionSummaryResponse{}, err
		}
		active++
		if _, ok := s.sessionLastActive(ctx, tokenID); ok {
			onlineSessions++
			onlineUsers[userID] = struct{}{}
		}
	}
	if err := rows.Err(); err != nil {
		return RefreshTokenSessionSummaryResponse{}, err
	}
	return RefreshTokenSessionSummaryResponse{
		OnlineUsers:    int64(len(onlineUsers)),
		OnlineSessions: onlineSessions,
		ActiveSessions: active,
	}, nil
}

func (s RefreshTokenAdminService) Revoke(ctx context.Context, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	tokenID, err := s.refreshTokenID(ctx, id)
	if err != nil {
		return err
	}
	command, err := s.db.Exec(ctx, `
		UPDATE auth_refresh_token
		   SET revoked_at = CURRENT_TIMESTAMP,
		       revoke_reason = $1,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $2
		   AND deleted_flag = false
		   AND revoked_at IS NULL
	`, refreshRevokeManual, id)
	if err != nil {
		return err
	}
	if command.RowsAffected() == 0 {
		return NewAuthError(AuthErrorBusiness, "令牌已被禁用")
	}
	s.clearSession(ctx, tokenID)
	return nil
}

func (s RefreshTokenAdminService) RevokeAll(ctx context.Context) (int, error) {
	if s.db == nil {
		return 0, errors.New("database client is not configured")
	}
	rows, err := s.db.Query(ctx, `
		SELECT id, token_id
		  FROM auth_refresh_token
		 WHERE deleted_flag = false
		   AND revoked_at IS NULL
	`)
	if err != nil {
		return 0, err
	}
	defer rows.Close()
	type token struct {
		id      int64
		tokenID string
	}
	tokens := []token{}
	for rows.Next() {
		var item token
		if err := rows.Scan(&item.id, &item.tokenID); err != nil {
			return 0, err
		}
		tokens = append(tokens, item)
	}
	if err := rows.Err(); err != nil {
		return 0, err
	}
	if len(tokens) == 0 {
		return 0, nil
	}
	ids := make([]int64, 0, len(tokens))
	for _, item := range tokens {
		ids = append(ids, item.id)
	}
	command, err := s.db.Exec(ctx, `
		UPDATE auth_refresh_token
		   SET revoked_at = CURRENT_TIMESTAMP,
		       revoke_reason = $1,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = ANY($2)
		   AND deleted_flag = false
		   AND revoked_at IS NULL
	`, refreshRevokeManual, ids)
	if err != nil {
		return 0, err
	}
	for _, item := range tokens {
		s.clearSession(ctx, item.tokenID)
	}
	return int(command.RowsAffected()), nil
}

type ApiKeyAdminService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
}

func NewApiKeyAdminService(db *pgxpool.Pool, machineID int64) ApiKeyAdminService {
	return ApiKeyAdminService{db: db, idGenerator: NewIDGenerator(machineID)}
}

func (s ApiKeyAdminService) Page(ctx context.Context, operatorID int64, query PageQuery, keyword string, userID *int64, status string, usageScope string) (PageResponse[ApiKeyResponse], error) {
	if s.db == nil {
		return PageResponse[ApiKeyResponse]{}, errors.New("database client is not configured")
	}
	if userID != nil && !s.isAdmin(ctx, operatorID) && *userID != operatorID {
		return PageResponse[ApiKeyResponse]{}, NewAuthError(AuthErrorForbidden, "只能查看自己的 API Key")
	}
	effectiveUserID := userID
	if !s.isAdmin(ctx, operatorID) {
		effectiveUserID = &operatorID
	}
	where, args, err := apiKeyFilters(keyword, effectiveUserID, status, usageScope)
	if err != nil {
		return PageResponse[ApiKeyResponse]{}, err
	}
	var total int64
	if err := s.db.QueryRow(ctx, "SELECT count(*) FROM auth_api_key key WHERE "+where, args...).Scan(&total); err != nil {
		return PageResponse[ApiKeyResponse]{}, err
	}
	limit, offset := sqlLimitOffset(query)
	args = append(args, limit, offset)
	orderBy := sqlOrderBy(query.SortBy, query.Direction, map[string]string{
		"keyName":   "key.key_name",
		"keyPrefix": "key.key_prefix",
		"createdAt": "key.created_at",
		"expiresAt": "key.expires_at",
		"status":    "key.status",
	}, "key.id")
	rows, err := s.db.Query(ctx, `
		SELECT key.id, key.user_id, COALESCE(user_account.login_name, key.user_id::text),
		       COALESCE(user_account.user_name, '--'), key.key_name, key.usage_scope,
		       COALESCE(key.allowed_resources, ''), COALESCE(key.allowed_actions, ''),
		       key.key_prefix, key.created_at, key.expires_at, key.last_used_at, key.status
		  FROM auth_api_key key
		  LEFT JOIN sys_user user_account
		    ON user_account.id = key.user_id
		   AND user_account.deleted_flag = false
		 WHERE `+where+`
		 ORDER BY `+orderBy+`
		 LIMIT $`+strconvArg(len(args)-1)+` OFFSET $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return PageResponse[ApiKeyResponse]{}, err
	}
	defer rows.Close()
	content := []ApiKeyResponse{}
	for rows.Next() {
		item, err := scanApiKey(rows, "")
		if err != nil {
			return PageResponse[ApiKeyResponse]{}, err
		}
		content = append(content, item)
	}
	if err := rows.Err(); err != nil {
		return PageResponse[ApiKeyResponse]{}, err
	}
	return NewPageResponse(content, total, query), nil
}

func (s ApiKeyAdminService) Detail(ctx context.Context, operatorID int64, id int64) (ApiKeyResponse, error) {
	item, err := s.detail(ctx, id, "")
	if err != nil {
		return ApiKeyResponse{}, err
	}
	if !s.isAdmin(ctx, operatorID) && item.UserID != operatorID {
		return ApiKeyResponse{}, NewAuthError(AuthErrorForbidden, "只能查看自己的 API Key")
	}
	return item, nil
}

func (s ApiKeyAdminService) UserOptions(ctx context.Context, keyword string) ([]ApiKeyUserOptionResponse, error) {
	if s.db == nil {
		return nil, errors.New("database client is not configured")
	}
	args := []any{}
	where := "deleted_flag = false AND status = 'NORMAL' AND totp_enabled = true AND totp_secret IS NOT NULL"
	if strings.TrimSpace(keyword) != "" {
		args = append(args, likeKeyword(keyword))
		where += " AND (lower(login_name) LIKE $1 OR lower(user_name) LIKE $1 OR lower(COALESCE(mobile, '')) LIKE $1)"
	}
	args = append(args, 20)
	rows, err := s.db.Query(ctx, `
		SELECT id, login_name, user_name, COALESCE(mobile, '')
		  FROM sys_user
		 WHERE `+where+`
		 ORDER BY login_name ASC
		 LIMIT $`+strconvArg(len(args))+`
	`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := []ApiKeyUserOptionResponse{}
	for rows.Next() {
		var item ApiKeyUserOptionResponse
		if err := rows.Scan(&item.ID, &item.LoginName, &item.UserName, &item.Mobile); err != nil {
			return nil, err
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s ApiKeyAdminService) ResourceOptions(ctx context.Context) ([]ApiKeyResourceOptionResponse, error) {
	result := make([]ApiKeyResourceOptionResponse, 0, len(PermissionCatalog()))
	for _, entry := range PermissionCatalog() {
		result = append(result, ApiKeyResourceOptionResponse{Code: entry.Code, Title: entry.Title, Group: entry.Group})
	}
	return result, nil
}

func (s ApiKeyAdminService) ActionOptions(ctx context.Context) ([]ApiKeyActionOptionResponse, error) {
	actions := sortedCatalogActions()
	result := make([]ApiKeyActionOptionResponse, 0, len(actions))
	for _, action := range actions {
		result = append(result, ApiKeyActionOptionResponse{Code: action.Code, Title: action.Title})
	}
	return result, nil
}

func (s ApiKeyAdminService) Generate(ctx context.Context, operatorID int64, userID int64, request ApiKeyRequest) (ApiKeyResponse, error) {
	if s.db == nil {
		return ApiKeyResponse{}, errors.New("database client is not configured")
	}
	admin := s.isAdmin(ctx, operatorID)
	if !admin && operatorID != userID {
		return ApiKeyResponse{}, NewAuthError(AuthErrorForbidden, "只能为当前登录用户生成 API Key")
	}
	if err := s.assertUserCanReceiveKey(ctx, userID); err != nil {
		return ApiKeyResponse{}, err
	}
	resources, err := normalizeAllowedResources(request.AllowedResources)
	if err != nil {
		return ApiKeyResponse{}, err
	}
	actions, err := normalizeAllowedActions(request.AllowedActions)
	if err != nil {
		return ApiKeyResponse{}, err
	}
	if err := s.ensurePermissionUpperBound(ctx, userID, resources, actions, "目标用户权限不足，不能生成包含该资源或动作的 API Key"); err != nil {
		return ApiKeyResponse{}, err
	}
	if operatorID != userID {
		if err := s.ensurePermissionUpperBound(ctx, operatorID, resources, actions, "当前操作者权限不足，不能生成包含该资源或动作的 API Key"); err != nil {
			return ApiKeyResponse{}, err
		}
	}
	expireDays, err := clampExpireDays(request.ExpireDays)
	if err != nil {
		return ApiKeyResponse{}, err
	}
	usageScope, err := normalizeApiKeyUsageScope(request.UsageScope)
	if err != nil {
		return ApiKeyResponse{}, err
	}
	keyName := strings.TrimSpace(request.KeyName)
	if keyName == "" {
		return ApiKeyResponse{}, NewAuthError(AuthErrorValidation, "密钥名称不能为空")
	}
	rawKey, err := randomURLToken(apiKeyRawPrefix, apiKeyRandomBytes)
	if err != nil {
		return ApiKeyResponse{}, err
	}
	keyPrefix := rawKey
	if len(keyPrefix) > apiKeyPrefixLength {
		keyPrefix = keyPrefix[:apiKeyPrefixLength]
	}
	var expiresAt *time.Time
	if expireDays != nil {
		value := time.Now().AddDate(0, 0, int(*expireDays))
		expiresAt = &value
	}
	id := s.idGenerator.Next()
	_, err = s.db.Exec(ctx, `
		INSERT INTO auth_api_key (
			id, user_id, key_name, key_prefix, key_hash, usage_scope,
			allowed_resources, allowed_actions, expires_at, status,
			created_by, created_name, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, 'system', false)
	`, id, userID, keyName, keyPrefix, hashToken(rawKey), usageScope, joinCSV(resources), joinCSV(actions), expiresAt, apiKeyStatusActive, operatorID)
	if err != nil {
		return ApiKeyResponse{}, err
	}
	return s.detail(ctx, id, rawKey)
}

func (s ApiKeyAdminService) Revoke(ctx context.Context, operatorID int64, id int64) error {
	if s.db == nil {
		return errors.New("database client is not configured")
	}
	item, err := s.Detail(ctx, operatorID, id)
	if err != nil {
		return err
	}
	if item.Status == apiKeyStatusDisabled {
		return NewAuthError(AuthErrorBusiness, "API Key 已被禁用")
	}
	if item.Status == apiKeyStatusExpired {
		return NewAuthError(AuthErrorBusiness, "API Key 已过期")
	}
	_, err = s.db.Exec(ctx, `
		UPDATE auth_api_key
		   SET status = $1,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $2
		   AND deleted_flag = false
	`, apiKeyStatusDisabled, id)
	return err
}

type rowScanner interface {
	Scan(dest ...any) error
}

func userAccountFilters(keyword string, status string) (string, []any, error) {
	where := "user_account.deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, likeKeyword(keyword))
		where += " AND (lower(user_account.login_name) LIKE $" + strconvArg(len(args)) +
			" OR lower(user_account.user_name) LIKE $" + strconvArg(len(args)) +
			" OR lower(COALESCE(user_account.mobile, '')) LIKE $" + strconvArg(len(args)) +
			" OR lower(COALESCE(department.department_name, '')) LIKE $" + strconvArg(len(args)) + ")"
	}
	if strings.TrimSpace(status) != "" {
		dbStatus, err := normalizeUserStatusForDB(status)
		if err != nil {
			return "", nil, NewAuthError(AuthErrorValidation, "用户状态不合法")
		}
		args = append(args, dbStatus)
		where += " AND user_account.status = $" + strconvArg(len(args))
	}
	return where, args, nil
}

func scanUserAccountAdmin(scanner rowScanner) (UserAccountAdminResponse, error) {
	var row UserAccountAdminResponse
	var departmentID sql.NullInt64
	var lastLogin sql.NullTime
	var status string
	if err := scanner.Scan(
		&row.ID,
		&row.LoginName,
		&row.UserName,
		&row.Mobile,
		&departmentID,
		&row.DepartmentName,
		&row.DataScope,
		&row.PermissionSummary,
		&lastLogin,
		&status,
		&row.Remark,
		&row.TotpEnabled,
	); err != nil {
		return UserAccountAdminResponse{}, err
	}
	if departmentID.Valid {
		value := departmentID.Int64
		row.DepartmentID = &value
	}
	row.LastLoginDate = nullableTime(lastLogin)
	row.Status = displayUserStatus(status)
	row.RoleNames = []string{}
	row.RoleIDs = []int64{}
	return row, nil
}

func (s UserAccountAdminService) fillUserRoles(ctx context.Context, users []UserAccountAdminResponse) error {
	if len(users) == 0 {
		return nil
	}
	ids := make([]int64, 0, len(users))
	indexByID := map[int64]int{}
	for i := range users {
		ids = append(ids, users[i].ID)
		indexByID[users[i].ID] = i
	}
	rows, err := s.db.Query(ctx, `
		SELECT user_role.user_id, role.id, role.role_name
		  FROM sys_user_role user_role
		  JOIN sys_role role
		    ON role.id = user_role.role_id
		   AND role.deleted_flag = false
		 WHERE user_role.deleted_flag = false
		   AND user_role.user_id = ANY($1)
		 ORDER BY role.role_name ASC
	`, ids)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var userID, roleID int64
		var roleName string
		if err := rows.Scan(&userID, &roleID, &roleName); err != nil {
			return err
		}
		if index, ok := indexByID[userID]; ok {
			users[index].RoleIDs = append(users[index].RoleIDs, roleID)
			users[index].RoleNames = append(users[index].RoleNames, roleName)
		}
	}
	return rows.Err()
}

type roleWriter interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconnCommandTag, error)
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}

type pgconnCommandTag interface {
	RowsAffected() int64
}

func (s UserAccountAdminService) replaceUserRolesWithQuerier(ctx context.Context, tx pgx.Tx, userID int64, roleIDs []int64, roleNames []string) error {
	resolvedRoleIDs, err := s.resolveRoleIDs(ctx, tx, roleIDs, roleNames)
	if err != nil {
		return err
	}
	if _, err := tx.Exec(ctx, `
		UPDATE sys_user_role
		   SET deleted_flag = true,
		       updated_at = CURRENT_TIMESTAMP
		 WHERE user_id = $1
		   AND deleted_flag = false
	`, userID); err != nil {
		return err
	}
	for _, roleID := range resolvedRoleIDs {
		_, err := tx.Exec(ctx, `
			INSERT INTO sys_user_role (
				id, user_id, role_id, created_by, created_name, deleted_flag
			) VALUES ($1, $2, $3, 0, 'system', false)
			ON CONFLICT (user_id, role_id) DO UPDATE
			   SET deleted_flag = false,
			       updated_at = CURRENT_TIMESTAMP
		`, s.idGenerator.Next(), userID, roleID)
		if err != nil {
			return err
		}
	}
	return nil
}

func (s UserAccountAdminService) resolveRoleIDs(ctx context.Context, tx pgx.Tx, roleIDs []int64, roleNames []string) ([]int64, error) {
	seen := map[int64]struct{}{}
	result := []int64{}
	for _, id := range roleIDs {
		if id <= 0 {
			continue
		}
		if _, ok := seen[id]; ok {
			continue
		}
		var exists bool
		err := tx.QueryRow(ctx, `
			SELECT EXISTS (
				SELECT 1 FROM sys_role
				 WHERE id = $1
				   AND deleted_flag = false
			)
		`, id).Scan(&exists)
		if err != nil {
			return nil, err
		}
		if !exists {
			return nil, NewAuthError(AuthErrorValidation, "角色不存在")
		}
		seen[id] = struct{}{}
		result = append(result, id)
	}
	for _, name := range roleNames {
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		var id int64
		err := tx.QueryRow(ctx, `
			SELECT id
			  FROM sys_role
			 WHERE role_name = $1
			   AND deleted_flag = false
			 LIMIT 1
		`, name).Scan(&id)
		if errors.Is(err, pgx.ErrNoRows) {
			return nil, NewAuthError(AuthErrorValidation, "角色不存在")
		}
		if err != nil {
			return nil, err
		}
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		result = append(result, id)
	}
	return result, nil
}

func (s UserAccountAdminService) assertNotLastActiveAdmin(ctx context.Context, userID int64, nextStatus string, nextRoleIDs []int64, nextRoleNames []string, deleting bool) error {
	currentAdmin, err := s.userHasAdminRole(ctx, userID)
	if err != nil {
		return err
	}
	if !currentAdmin {
		return nil
	}
	keepsAdmin := !deleting && nextStatus == "NORMAL"
	if keepsAdmin && (nextRoleIDs != nil || nextRoleNames != nil) {
		keepsAdmin, err = s.rolePayloadContainsAdmin(ctx, nextRoleIDs, nextRoleNames)
		if err != nil {
			return err
		}
	}
	if keepsAdmin {
		return nil
	}
	var otherCount int64
	err = s.db.QueryRow(ctx, `
		SELECT count(DISTINCT user_account.id)
		  FROM sys_user user_account
		  JOIN sys_user_role user_role
		    ON user_role.user_id = user_account.id
		   AND user_role.deleted_flag = false
		  JOIN sys_role role
		    ON role.id = user_role.role_id
		   AND role.deleted_flag = false
		 WHERE user_account.deleted_flag = false
		   AND user_account.status = 'NORMAL'
		   AND role.role_code = 'ADMIN'
		   AND user_account.id <> $1
	`, userID).Scan(&otherCount)
	if err != nil {
		return err
	}
	if otherCount == 0 {
		return NewAuthError(AuthErrorBusiness, "至少保留一个正常状态的系统管理员")
	}
	return nil
}

func (s UserAccountAdminService) userHasAdminRole(ctx context.Context, userID int64) (bool, error) {
	var exists bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_user_role user_role
			  JOIN sys_role role
			    ON role.id = user_role.role_id
			   AND role.deleted_flag = false
			 WHERE user_role.user_id = $1
			   AND user_role.deleted_flag = false
			   AND role.role_code = 'ADMIN'
		)
	`, userID).Scan(&exists)
	return exists, err
}

func (s UserAccountAdminService) rolePayloadContainsAdmin(ctx context.Context, roleIDs []int64, roleNames []string) (bool, error) {
	args := []any{}
	whereParts := []string{}
	if len(roleIDs) > 0 {
		args = append(args, roleIDs)
		whereParts = append(whereParts, "id = ANY($"+strconvArg(len(args))+")")
	}
	if len(roleNames) > 0 {
		names := normalizeStringList(roleNames)
		args = append(args, names)
		whereParts = append(whereParts, "role_name = ANY($"+strconvArg(len(args))+")")
	}
	if len(whereParts) == 0 {
		return false, nil
	}
	var exists bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1 FROM sys_role
			 WHERE deleted_flag = false
			   AND role_code = 'ADMIN'
			   AND (`+strings.Join(whereParts, " OR ")+`)
		)
	`, args...).Scan(&exists)
	return exists, err
}

func normalizeAdminLoginName(value string) (string, error) {
	value = normalizeLoginName(value)
	if value == "" {
		return "", NewAuthError(AuthErrorValidation, "登录账号不能为空")
	}
	if !adminLoginNamePattern.MatchString(value) {
		return "", NewAuthError(AuthErrorValidation, "登录账号格式不正确")
	}
	return value, nil
}

func normalizeDataScopeForDisplay(value string) string {
	switch normalizeDataScope(value) {
	case "all":
		return "全部数据"
	case "department":
		return "本部门"
	default:
		return "本人"
	}
}

func readPreferencesPayload(raw string) UserAccountPreferencesPayload {
	if strings.TrimSpace(raw) == "" {
		return UserAccountPreferencesPayload{Pages: map[string]UserListColumnSettingsPayload{}}
	}
	var payload UserAccountPreferencesPayload
	if err := json.Unmarshal([]byte(raw), &payload); err != nil {
		return UserAccountPreferencesPayload{Pages: map[string]UserListColumnSettingsPayload{}}
	}
	return normalizePreferencesPayloadFromRequest(payload)
}

func normalizePreferencesPayload(payload UserAccountPreferencesPayload) UserAccountPreferencesPayload {
	return normalizePreferencesPayloadFromRequest(payload)
}

func normalizePreferencesPayloadFromRequest(payload UserAccountPreferencesPayload) UserAccountPreferencesPayload {
	result := UserAccountPreferencesPayload{Pages: map[string]UserListColumnSettingsPayload{}}
	for key, settings := range payload.Pages {
		key = strings.TrimSpace(key)
		if key == "" {
			continue
		}
		result.Pages[key] = UserListColumnSettingsPayload{
			OrderedKeys: normalizeStringList(settings.OrderedKeys),
			HiddenKeys:  normalizeStringList(settings.HiddenKeys),
		}
	}
	return result
}

func scanRefreshTokenAdmin(scanner rowScanner) (RefreshTokenAdminResponse, error) {
	var item RefreshTokenAdminResponse
	var revokedAt sql.NullTime
	if err := scanner.Scan(
		&item.ID,
		&item.UserID,
		&item.LoginName,
		&item.UserName,
		&item.TokenID,
		&item.LoginIP,
		&item.DeviceInfo,
		&item.CreatedAt,
		&item.ExpiresAt,
		&revokedAt,
	); err != nil {
		return RefreshTokenAdminResponse{}, err
	}
	item.RevokedAt = nullableTime(revokedAt)
	item.Status = refreshTokenStatus(item.ExpiresAt, item.RevokedAt)
	return item, nil
}

func refreshTokenStatus(expiresAt time.Time, revokedAt *time.Time) string {
	if revokedAt != nil {
		return "已禁用"
	}
	if expiresAt.Before(time.Now()) {
		return "已过期"
	}
	return "有效"
}

func (s RefreshTokenAdminService) refreshTokenID(ctx context.Context, id int64) (string, error) {
	var tokenID string
	var revokedAt sql.NullTime
	err := s.db.QueryRow(ctx, `
		SELECT token_id, revoked_at
		  FROM auth_refresh_token
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, id).Scan(&tokenID, &revokedAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return "", NewAuthError(AuthErrorNotFound, "令牌不存在")
	}
	if err != nil {
		return "", err
	}
	if revokedAt.Valid {
		return "", NewAuthError(AuthErrorBusiness, "令牌已被禁用")
	}
	return tokenID, nil
}

func (s RefreshTokenAdminService) sessionLastActive(ctx context.Context, tokenID string) (time.Time, bool) {
	if s.redis == nil {
		return time.Time{}, false
	}
	value, err := s.redis.Get(ctx, sessionActivityPrefix+strings.TrimSpace(tokenID)).Result()
	if err != nil {
		return time.Time{}, false
	}
	millis, err := strconv.ParseInt(strings.TrimSpace(value), 10, 64)
	if err != nil {
		return time.Time{}, false
	}
	return time.UnixMilli(millis), true
}

func (s RefreshTokenAdminService) clearSession(ctx context.Context, tokenID string) {
	if s.redis == nil {
		return
	}
	tokenID = strings.TrimSpace(tokenID)
	if tokenID == "" {
		return
	}
	_ = s.redis.Del(ctx, sessionActivityPrefix+tokenID, accessTokenSessionBlacklistPrefix+tokenID).Err()
}

func apiKeyFilters(keyword string, userID *int64, status string, usageScope string) (string, []any, error) {
	where := "key.deleted_flag = false"
	args := []any{}
	if strings.TrimSpace(keyword) != "" {
		args = append(args, likeKeyword(keyword))
		where += " AND (lower(key.key_name) LIKE $" + strconvArg(len(args)) +
			" OR lower(key.key_prefix) LIKE $" + strconvArg(len(args)) + ")"
	}
	if userID != nil {
		args = append(args, *userID)
		where += " AND key.user_id = $" + strconvArg(len(args))
	}
	if strings.TrimSpace(usageScope) != "" {
		normalized, err := normalizeApiKeyUsageScope(usageScope)
		if err != nil {
			return "", nil, err
		}
		args = append(args, normalized)
		where += " AND key.usage_scope = $" + strconvArg(len(args))
	}
	switch strings.TrimSpace(status) {
	case "":
	case apiKeyStatusActive:
		where += " AND key.status = '有效' AND (key.expires_at IS NULL OR key.expires_at > CURRENT_TIMESTAMP)"
	case apiKeyStatusExpired:
		where += " AND key.status = '有效' AND key.expires_at IS NOT NULL AND key.expires_at <= CURRENT_TIMESTAMP"
	case apiKeyStatusDisabled:
		where += " AND key.status = '已禁用'"
	default:
		return "", nil, NewAuthError(AuthErrorValidation, "API Key 状态不合法")
	}
	return where, args, nil
}

func scanApiKey(scanner rowScanner, rawKey string) (ApiKeyResponse, error) {
	var item ApiKeyResponse
	var allowedResources, allowedActions string
	var expiresAt, lastUsedAt sql.NullTime
	if err := scanner.Scan(
		&item.ID,
		&item.UserID,
		&item.LoginName,
		&item.UserName,
		&item.KeyName,
		&item.UsageScope,
		&allowedResources,
		&allowedActions,
		&item.KeyPrefix,
		&item.CreatedAt,
		&expiresAt,
		&lastUsedAt,
		&item.Status,
	); err != nil {
		return ApiKeyResponse{}, err
	}
	item.AllowedResources = splitCSV(allowedResources)
	item.AllowedActions = splitCSV(allowedActions)
	item.ExpiresAt = nullableTime(expiresAt)
	item.LastUsedAt = nullableTime(lastUsedAt)
	item.RawKey = rawKey
	item.Status = resolveApiKeyStatus(item.Status, item.ExpiresAt)
	return item, nil
}

func resolveApiKeyStatus(status string, expiresAt *time.Time) string {
	if strings.TrimSpace(status) == apiKeyStatusDisabled {
		return apiKeyStatusDisabled
	}
	if expiresAt != nil && !expiresAt.After(time.Now()) {
		return apiKeyStatusExpired
	}
	return apiKeyStatusActive
}

func (s ApiKeyAdminService) detail(ctx context.Context, id int64, rawKey string) (ApiKeyResponse, error) {
	if s.db == nil {
		return ApiKeyResponse{}, errors.New("database client is not configured")
	}
	var item ApiKeyResponse
	err := s.db.QueryRow(ctx, `
		SELECT key.id, key.user_id, COALESCE(user_account.login_name, key.user_id::text),
		       COALESCE(user_account.user_name, '--'), key.key_name, key.usage_scope,
		       COALESCE(key.allowed_resources, ''), COALESCE(key.allowed_actions, ''),
		       key.key_prefix, key.created_at, key.expires_at, key.last_used_at, key.status
		  FROM auth_api_key key
		  LEFT JOIN sys_user user_account
		    ON user_account.id = key.user_id
		   AND user_account.deleted_flag = false
		 WHERE key.id = $1
		   AND key.deleted_flag = false
		 LIMIT 1
	`, id).Scan(
		&item.ID,
		&item.UserID,
		&item.LoginName,
		&item.UserName,
		&item.KeyName,
		&item.UsageScope,
		(*stringListScanner)(&item.AllowedResources),
		(*stringListScanner)(&item.AllowedActions),
		&item.KeyPrefix,
		&item.CreatedAt,
		nullableTimeScanTarget{target: &item.ExpiresAt},
		nullableTimeScanTarget{target: &item.LastUsedAt},
		&item.Status,
	)
	if errors.Is(err, pgx.ErrNoRows) {
		return ApiKeyResponse{}, NewAuthError(AuthErrorNotFound, "API Key 不存在")
	}
	if err != nil {
		return ApiKeyResponse{}, err
	}
	item.RawKey = rawKey
	item.Status = resolveApiKeyStatus(item.Status, item.ExpiresAt)
	return item, nil
}

type stringListScanner []string

func (s *stringListScanner) Scan(value any) error {
	switch typed := value.(type) {
	case nil:
		*s = []string{}
	case string:
		*s = splitCSV(typed)
	case []byte:
		*s = splitCSV(string(typed))
	default:
		*s = []string{}
	}
	return nil
}

type nullableTimeScanTarget struct {
	target **time.Time
}

func (s nullableTimeScanTarget) Scan(value any) error {
	switch typed := value.(type) {
	case nil:
		*s.target = nil
	case time.Time:
		t := typed
		*s.target = &t
	default:
		*s.target = nil
	}
	return nil
}

func (s ApiKeyAdminService) isAdmin(ctx context.Context, userID int64) bool {
	var exists bool
	err := s.db.QueryRow(ctx, `
		SELECT EXISTS (
			SELECT 1
			  FROM sys_user_role user_role
			  JOIN sys_role role
			    ON role.id = user_role.role_id
			   AND role.deleted_flag = false
			 WHERE user_role.user_id = $1
			   AND user_role.deleted_flag = false
			   AND role.role_code = 'ADMIN'
		)
	`, userID).Scan(&exists)
	return err == nil && exists
}

func (s ApiKeyAdminService) assertUserCanReceiveKey(ctx context.Context, userID int64) error {
	var status string
	var totpSecret sql.NullString
	var totpEnabled bool
	err := s.db.QueryRow(ctx, `
		SELECT status, totp_secret, COALESCE(totp_enabled, false)
		  FROM sys_user
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, userID).Scan(&status, &totpSecret, &totpEnabled)
	if errors.Is(err, pgx.ErrNoRows) {
		return NewAuthError(AuthErrorNotFound, "目标用户不存在")
	}
	if err != nil {
		return err
	}
	if status != "NORMAL" {
		return NewAuthError(AuthErrorBusiness, "目标用户已禁用，不能生成 API Key")
	}
	if !totpEnabled || strings.TrimSpace(totpSecret.String) == "" {
		return NewAuthError(AuthErrorBusiness, "目标用户未启用2FA，不能生成 API Key")
	}
	return nil
}

func (s ApiKeyAdminService) ensurePermissionUpperBound(ctx context.Context, userID int64, allowedResources []string, allowedActions []string, message string) error {
	permissions, err := userPermissionActionSet(ctx, s.db, userID)
	if err != nil {
		return err
	}
	for _, resource := range allowedResources {
		actions := permissions[resource]
		if len(actions) == 0 {
			return NewAuthError(AuthErrorForbidden, message)
		}
		for _, action := range allowedActions {
			if _, ok := actions[action]; !ok || !catalogAllows(resource, action) {
				return NewAuthError(AuthErrorForbidden, message)
			}
		}
	}
	return nil
}

func userPermissionActionSet(ctx context.Context, db *pgxpool.Pool, userID int64) (map[string]map[string]struct{}, error) {
	rows, err := db.Query(ctx, `
		SELECT permission.resource_code, permission.action_code
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
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := map[string]map[string]struct{}{}
	for rows.Next() {
		var resource, action string
		if err := rows.Scan(&resource, &action); err != nil {
			return nil, err
		}
		resource = normalizeResourceCode(resource)
		action = normalizeActionCode(action)
		if resource == "" || action == "" {
			continue
		}
		if _, ok := result[resource]; !ok {
			result[resource] = map[string]struct{}{}
		}
		result[resource][action] = struct{}{}
	}
	return result, rows.Err()
}

func normalizeAllowedResources(values []string) ([]string, error) {
	values = normalizeStringList(values)
	if len(values) == 0 {
		return nil, NewAuthError(AuthErrorValidation, "API Key 允许访问资源不能为空")
	}
	seen := map[string]struct{}{}
	result := []string{}
	for _, value := range values {
		value = normalizeResourceCode(value)
		if !catalogHasResource(value) {
			return nil, NewAuthError(AuthErrorValidation, "API Key 允许访问资源不合法")
		}
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	return result, nil
}

func normalizeApiKeyResources(values []string) ([]string, error) {
	return normalizeAllowedResources(values)
}

func normalizeAllowedActions(values []string) ([]string, error) {
	values = normalizeStringList(values)
	if len(values) == 0 {
		return nil, NewAuthError(AuthErrorValidation, "API Key 允许动作不能为空")
	}
	seen := map[string]struct{}{}
	result := []string{}
	for _, value := range values {
		value = normalizeActionCode(value)
		if !catalogHasAction(value) {
			return nil, NewAuthError(AuthErrorValidation, "API Key 允许动作不合法")
		}
		if _, ok := seen[value]; ok {
			continue
		}
		seen[value] = struct{}{}
		result = append(result, value)
	}
	return result, nil
}

func normalizeApiKeyActions(values []string) ([]string, error) {
	return normalizeAllowedActions(values)
}

func catalogHasResource(resource string) bool {
	for _, entry := range PermissionCatalog() {
		if entry.Code == resource {
			return true
		}
	}
	return false
}

func catalogHasAction(action string) bool {
	for _, option := range sortedCatalogActions() {
		if option.Code == action {
			return true
		}
	}
	return false
}

func catalogAllows(resource string, action string) bool {
	for _, entry := range PermissionCatalog() {
		if entry.Code != resource {
			continue
		}
		for _, allowed := range entry.Actions {
			if allowed.Code == action {
				return true
			}
		}
		return false
	}
	return false
}

func normalizeApiKeyUsageScope(value string) (string, error) {
	value = strings.TrimSpace(value)
	switch value {
	case apiKeyUsageAll, apiKeyUsageReadOnly, apiKeyUsageBusiness:
		return value, nil
	case "":
		return "", NewAuthError(AuthErrorValidation, "API Key 使用范围不能为空")
	default:
		return "", NewAuthError(AuthErrorValidation, "API Key 使用范围不合法")
	}
}
