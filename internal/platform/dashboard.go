package platform

import (
	"context"
	"database/sql"
	"errors"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type DashboardService struct {
	db      *pgxpool.Pool
	appName string
}

type DashboardSummary struct {
	AppName            string     `json:"appName"`
	CompanyName        *string    `json:"companyName"`
	UserName           string     `json:"userName"`
	LoginName          string     `json:"loginName"`
	RoleName           string     `json:"roleName"`
	VisibleMenuCount   int64      `json:"visibleMenuCount"`
	ModuleCount        int64      `json:"moduleCount"`
	ActionCount        int64      `json:"actionCount"`
	ActiveSessionCount int64      `json:"activeSessionCount"`
	TotpEnabled        bool       `json:"totpEnabled"`
	LastLoginAt        *time.Time `json:"lastLoginAt"`
	ServerTime         time.Time  `json:"serverTime"`
	MaterialCount      int64      `json:"materialCount"`
	SupplierCount      int64      `json:"supplierCount"`
	CustomerCount      int64      `json:"customerCount"`
}

type dashboardUser struct {
	ID            int64
	LoginName     string
	UserName      string
	TotpEnabled   bool
	LastLoginDate sql.NullTime
}

func NewDashboardService(db *pgxpool.Pool, appName string) DashboardService {
	appName = strings.TrimSpace(appName)
	if appName == "" {
		appName = "leo"
	}
	return DashboardService{db: db, appName: appName}
}

func (s DashboardService) Summary(ctx context.Context, userID int64) (DashboardSummary, error) {
	if s.db == nil {
		return DashboardSummary{}, errors.New("database client is not configured")
	}
	user, err := s.findDashboardUser(ctx, userID)
	if errors.Is(err, pgx.ErrNoRows) {
		return DashboardSummary{}, NewAuthError(AuthErrorNotFound, "用户不存在")
	}
	if err != nil {
		return DashboardSummary{}, err
	}

	roleName, err := s.roleNames(ctx, userID)
	if err != nil {
		return DashboardSummary{}, err
	}
	visibleMenuCount, moduleCount, err := s.visibleMenuStats(ctx, userID)
	if err != nil {
		return DashboardSummary{}, err
	}
	actionCount, err := s.actionCount(ctx, userID)
	if err != nil {
		return DashboardSummary{}, err
	}
	activeSessionCount, err := s.activeSessionCount(ctx, userID)
	if err != nil {
		return DashboardSummary{}, err
	}
	companyName, err := s.companyName(ctx)
	if err != nil {
		return DashboardSummary{}, err
	}
	materialCount, err := s.countDeletedFlagFalse(ctx, "md_material")
	if err != nil {
		return DashboardSummary{}, err
	}
	supplierCount, err := s.countDeletedFlagFalse(ctx, "md_supplier")
	if err != nil {
		return DashboardSummary{}, err
	}
	customerCount, err := s.countDeletedFlagFalse(ctx, "md_customer")
	if err != nil {
		return DashboardSummary{}, err
	}

	var lastLoginAt *time.Time
	if user.LastLoginDate.Valid {
		value := user.LastLoginDate.Time
		lastLoginAt = &value
	}
	return DashboardSummary{
		AppName:            s.appName,
		CompanyName:        companyName,
		UserName:           user.UserName,
		LoginName:          user.LoginName,
		RoleName:           roleName,
		VisibleMenuCount:   visibleMenuCount,
		ModuleCount:        moduleCount,
		ActionCount:        actionCount,
		ActiveSessionCount: activeSessionCount,
		TotpEnabled:        user.TotpEnabled,
		LastLoginAt:        lastLoginAt,
		ServerTime:         time.Now(),
		MaterialCount:      materialCount,
		SupplierCount:      supplierCount,
		CustomerCount:      customerCount,
	}, nil
}

func (s DashboardService) findDashboardUser(ctx context.Context, userID int64) (dashboardUser, error) {
	var user dashboardUser
	err := s.db.QueryRow(ctx, `
		SELECT id, login_name, user_name, totp_enabled, last_login_date
		  FROM sys_user
		 WHERE id = $1
		   AND deleted_flag = false
		 LIMIT 1
	`, userID).Scan(&user.ID, &user.LoginName, &user.UserName, &user.TotpEnabled, &user.LastLoginDate)
	return user, err
}

func (s DashboardService) roleNames(ctx context.Context, userID int64) (string, error) {
	rows, err := s.db.Query(ctx, `
		SELECT DISTINCT role.role_name
		  FROM sys_user_role user_role
		  JOIN sys_role role
		    ON role.id = user_role.role_id
		   AND role.deleted_flag = false
		   AND role.status = '正常'
		 WHERE user_role.user_id = $1
		   AND user_role.deleted_flag = false
		 ORDER BY role.role_name
	`, userID)
	if err != nil {
		return "", err
	}
	defer rows.Close()

	var names []string
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			return "", err
		}
		if strings.TrimSpace(name) != "" {
			names = append(names, strings.TrimSpace(name))
		}
	}
	if err := rows.Err(); err != nil {
		return "", err
	}
	return strings.Join(names, ","), nil
}

func (s DashboardService) visibleMenuStats(ctx context.Context, userID int64) (int64, int64, error) {
	var visibleMenuCount int64
	var moduleCount int64
	err := s.db.QueryRow(ctx, `
		WITH visible_codes AS (
			SELECT DISTINCT permission.resource_code AS menu_code
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
			   AND lower(permission.action_code) IN ('read', 'view')
			UNION
			SELECT 'dashboard'
		)
		SELECT
			count(menu.id),
			count(menu.id) FILTER (
				WHERE menu.menu_type = '菜单'
				  AND COALESCE(menu.route_path, '') <> ''
			)
		  FROM sys_menu menu
		  JOIN visible_codes visible
		    ON visible.menu_code = menu.menu_code
		 WHERE menu.deleted_flag = false
		   AND menu.status = '正常'
	`, userID).Scan(&visibleMenuCount, &moduleCount)
	return visibleMenuCount, moduleCount, err
}

func (s DashboardService) actionCount(ctx context.Context, userID int64) (int64, error) {
	var count int64
	err := s.db.QueryRow(ctx, `
		SELECT count(*)
		  FROM (
			SELECT DISTINCT permission.resource_code, permission.action_code
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
		  ) actions
	`, userID).Scan(&count)
	return count, err
}

func (s DashboardService) activeSessionCount(ctx context.Context, userID int64) (int64, error) {
	var count int64
	err := s.db.QueryRow(ctx, `
		SELECT count(*)
		  FROM auth_refresh_token
		 WHERE user_id = $1
		   AND deleted_flag = false
		   AND revoked_at IS NULL
		   AND expires_at > CURRENT_TIMESTAMP
	`, userID).Scan(&count)
	return count, err
}

func (s DashboardService) companyName(ctx context.Context) (*string, error) {
	var name string
	err := s.db.QueryRow(ctx, `
		SELECT company_name
		  FROM sys_company_setting
		 WHERE deleted_flag = false
		 ORDER BY CASE WHEN status = '正常' THEN 0 ELSE 1 END, id ASC
		 LIMIT 1
	`).Scan(&name)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &name, nil
}

func (s DashboardService) countDeletedFlagFalse(ctx context.Context, table string) (int64, error) {
	var count int64
	err := s.db.QueryRow(ctx, "SELECT count(*) FROM "+table+" WHERE deleted_flag = false").Scan(&count)
	return count, err
}
