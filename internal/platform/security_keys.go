package platform

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"os"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
)

const (
	securityKeyStatusActive  = "ACTIVE"
	securityKeyStatusRetired = "RETIRED"
)

type SecurityKeyService struct {
	db          *pgxpool.Pool
	idGenerator *IDGenerator
	jwtSecret   string
}

func NewSecurityKeyService(db *pgxpool.Pool, jwtSecret string, machineID int64) SecurityKeyService {
	return SecurityKeyService{db: db, idGenerator: NewIDGenerator(machineID), jwtSecret: strings.TrimSpace(jwtSecret)}
}

func (s SecurityKeyService) Overview(ctx context.Context) (SecurityKeyOverviewResponse, error) {
	jwt, err := s.securityKeyItem(ctx, jwtSecretType, "JWT 主密钥", 0)
	if err != nil {
		return SecurityKeyOverviewResponse{}, err
	}
	protectedCount, err := s.totpProtectedCount(ctx)
	if err != nil {
		return SecurityKeyOverviewResponse{}, err
	}
	totp, err := s.securityKeyItem(ctx, totpSecretType, "2FA 主密钥", protectedCount)
	if err != nil {
		return SecurityKeyOverviewResponse{}, err
	}
	return SecurityKeyOverviewResponse{JWT: jwt, TOTP: totp}, nil
}

func (s SecurityKeyService) RotateJWT(ctx context.Context) (SecurityKeyRotateResponse, error) {
	if s.db == nil {
		return SecurityKeyRotateResponse{}, errors.New("database client is not configured")
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	defer tx.Rollback(ctx)

	now := time.Now()
	current, err := s.activeSecretMaterialWithQuerier(ctx, tx, jwtSecretType, s.jwtSecret, "LEO_JWT_SECRET")
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	active, err := s.activeSecretRowWithQuerier(ctx, tx, jwtSecretType)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return SecurityKeyRotateResponse{}, err
	}
	nextVersion, err := s.nextSecurityKeyVersionWithQuerier(ctx, tx, jwtSecretType)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	if active.ID == 0 {
		if _, err := s.insertSecuritySecretWithQuerier(ctx, tx, jwtSecretType, "JWT 历史主密钥", nextVersion, current.Value, securityKeyStatusRetired, now, sql.NullTime{Time: now, Valid: true}, "首次轮转时从配置文件导入的历史密钥"); err != nil {
			return SecurityKeyRotateResponse{}, err
		}
		nextVersion++
	} else {
		if err := s.retireSecuritySecretWithQuerier(ctx, tx, active.ID, "JWT 历史主密钥", now); err != nil {
			return SecurityKeyRotateResponse{}, err
		}
	}
	newSecret, err := randomSecuritySecret(64)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	inserted, err := s.insertSecuritySecretWithQuerier(ctx, tx, jwtSecretType, "JWT 主密钥", nextVersion, newSecret, securityKeyStatusActive, now, sql.NullTime{}, "通过系统设置轮转生成")
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	return s.securityKeyRotateResponse(ctx, jwtSecretType, inserted, 0, "JWT 主密钥轮转完成")
}

func (s SecurityKeyService) RotateTOTP(ctx context.Context) (SecurityKeyRotateResponse, error) {
	if s.db == nil {
		return SecurityKeyRotateResponse{}, errors.New("database client is not configured")
	}
	tx, err := s.db.Begin(ctx)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	defer tx.Rollback(ctx)

	now := time.Now()
	current, err := s.activeSecretMaterialWithQuerier(ctx, tx, totpSecretType, os.Getenv("TOTP_ENCRYPTION_KEY"), "TOTP_ENCRYPTION_KEY")
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	newSecret, err := randomSecuritySecret(48)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	processed, err := s.reencryptTotpSecretsWithQuerier(ctx, tx, current.Value, newSecret)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	active, err := s.activeSecretRowWithQuerier(ctx, tx, totpSecretType)
	if err != nil && !errors.Is(err, pgx.ErrNoRows) {
		return SecurityKeyRotateResponse{}, err
	}
	nextVersion, err := s.nextSecurityKeyVersionWithQuerier(ctx, tx, totpSecretType)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	if active.ID == 0 {
		if _, err := s.insertSecuritySecretWithQuerier(ctx, tx, totpSecretType, "2FA 历史主密钥", nextVersion, current.Value, securityKeyStatusRetired, now, sql.NullTime{Time: now, Valid: true}, "首次轮转时从配置文件导入的历史密钥"); err != nil {
			return SecurityKeyRotateResponse{}, err
		}
		nextVersion++
	} else {
		if err := s.retireSecuritySecretWithQuerier(ctx, tx, active.ID, "2FA 历史主密钥", now); err != nil {
			return SecurityKeyRotateResponse{}, err
		}
	}
	inserted, err := s.insertSecuritySecretWithQuerier(ctx, tx, totpSecretType, "2FA 主密钥", nextVersion, newSecret, securityKeyStatusActive, now, sql.NullTime{}, "通过系统设置轮转生成")
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	if err := tx.Commit(ctx); err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	return s.securityKeyRotateResponse(ctx, totpSecretType, inserted, processed, "2FA 主密钥轮转并完成密钥重加密")
}

func (s SecurityKeyService) securityKeyItem(ctx context.Context, secretType string, keyName string, protectedCount int) (SecurityKeyItemResponse, error) {
	material, err := s.activeSecretMaterial(ctx, secretType, s.fallbackSecret(secretType), s.fallbackName(secretType))
	if err != nil {
		return SecurityKeyItemResponse{}, err
	}
	retiredCount, err := s.retiredSecurityKeyCount(ctx, secretType)
	if err != nil {
		return SecurityKeyItemResponse{}, err
	}
	remark := "当前使用数据库托管主密钥，轮转后立即对新会话生效"
	if material.Source == secretSourceConfig {
		remark = "当前使用配置文件主密钥，执行轮转后会切换为数据库托管"
	}
	return SecurityKeyItemResponse{
		KeyCode:              secretType,
		KeyName:              keyName,
		Source:               material.Source,
		ActiveVersion:        material.Version,
		ActiveFingerprint:    fingerprintSecret(material.Value),
		ActivatedAt:          nullableTime(material.ActivatedAt),
		RetiredVersionCount:  retiredCount,
		ProtectedRecordCount: protectedCount,
		Remark:               remark,
	}, nil
}

func (s SecurityKeyService) activeSecretMaterial(ctx context.Context, secretType string, fallback string, fallbackName string) (secretMaterialRecord, error) {
	if s.db == nil {
		return s.configSecretMaterial(secretType, fallback, fallbackName)
	}
	return s.activeSecretMaterialWithQuerier(ctx, s.db, secretType, fallback, fallbackName)
}

func (s SecurityKeyService) activeSecretMaterialWithQuerier(ctx context.Context, q securityKeyQuerier, secretType string, fallback string, fallbackName string) (secretMaterialRecord, error) {
	row, err := s.activeSecretRowWithQuerier(ctx, q, secretType)
	if err == nil {
		return secretMaterialRecord{
			Source:      secretSourceDB,
			Version:     row.KeyVersion,
			Value:       row.SecretValue,
			ActivatedAt: sql.NullTime{Time: row.ActivatedAt, Valid: true},
			RetiredAt:   row.RetiredAt,
		}, nil
	}
	if !errors.Is(err, pgx.ErrNoRows) {
		return secretMaterialRecord{}, err
	}
	return s.configSecretMaterial(secretType, fallback, fallbackName)
}

func (s SecurityKeyService) configSecretMaterial(secretType string, fallback string, fallbackName string) (secretMaterialRecord, error) {
	fallback = strings.TrimSpace(fallback)
	if fallback == "" {
		return secretMaterialRecord{}, NewAuthError(AuthErrorInternal, missingSecretMessage(secretType, fallbackName))
	}
	if secretType == jwtSecretType && len(fallback) < 32 {
		return secretMaterialRecord{}, NewAuthError(AuthErrorInternal, "启动兜底环境变量 LEO_JWT_SECRET 长度不能少于 32 位")
	}
	return secretMaterialRecord{Source: secretSourceConfig, Version: 0, Value: fallback}, nil
}

func (s SecurityKeyService) activeSecretRowWithQuerier(ctx context.Context, q securityKeyQuerier, secretType string) (securitySecretRecord, error) {
	var row securitySecretRecord
	err := q.QueryRow(ctx, `
		SELECT id, secret_type, secret_name, key_version, secret_value, status,
		       activated_at, retired_at, COALESCE(remark, '')
		  FROM sys_security_secret
		 WHERE secret_type = $1
		   AND status = $2
		   AND deleted_flag = false
		 ORDER BY key_version DESC
		 LIMIT 1
	`, secretType, securityKeyStatusActive).Scan(
		&row.ID, &row.SecretType, &row.SecretName, &row.KeyVersion, &row.SecretValue,
		&row.Status, &row.ActivatedAt, &row.RetiredAt, &row.Remark,
	)
	return row, err
}

func (s SecurityKeyService) nextSecurityKeyVersionWithQuerier(ctx context.Context, q securityKeyQuerier, secretType string) (int, error) {
	var version int
	err := q.QueryRow(ctx, `
		SELECT key_version
		  FROM sys_security_secret
		 WHERE secret_type = $1
		   AND deleted_flag = false
		 ORDER BY key_version DESC
		 LIMIT 1
	`, secretType).Scan(&version)
	if errors.Is(err, pgx.ErrNoRows) {
		return 1, nil
	}
	if err != nil {
		return 0, err
	}
	return version + 1, nil
}

func (s SecurityKeyService) insertSecuritySecretWithQuerier(ctx context.Context, q securityKeyQuerier, secretType string, secretName string, version int, value string, status string, activatedAt time.Time, retiredAt sql.NullTime, remark string) (securitySecretRecord, error) {
	id := s.idGenerator.Next()
	_, err := q.Exec(ctx, `
		INSERT INTO sys_security_secret (
			id, secret_type, secret_name, key_version, secret_value, status,
			activated_at, retired_at, remark, created_by, created_name, deleted_flag
		) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, 0, 'system', false)
	`, id, secretType, secretName, version, value, status, activatedAt, retiredAt, optionalNullString(remark))
	if err != nil {
		return securitySecretRecord{}, err
	}
	return securitySecretRecord{
		ID:          id,
		SecretType:  secretType,
		SecretName:  secretName,
		KeyVersion:  version,
		SecretValue: value,
		Status:      status,
		ActivatedAt: activatedAt,
		RetiredAt:   retiredAt,
		Remark:      remark,
	}, nil
}

func (s SecurityKeyService) retireSecuritySecretWithQuerier(ctx context.Context, q securityKeyQuerier, id int64, secretName string, retiredAt time.Time) error {
	_, err := q.Exec(ctx, `
		UPDATE sys_security_secret
		   SET status = $2,
		       secret_name = $3,
		       retired_at = $4,
		       updated_by = 0,
		       updated_name = 'system',
		       updated_at = CURRENT_TIMESTAMP
		 WHERE id = $1
		   AND deleted_flag = false
	`, id, securityKeyStatusRetired, secretName, retiredAt)
	return err
}

func (s SecurityKeyService) retiredSecurityKeyCount(ctx context.Context, secretType string) (int, error) {
	if s.db == nil {
		return 0, errors.New("database client is not configured")
	}
	var count int
	err := s.db.QueryRow(ctx, `
		SELECT count(*)
		  FROM sys_security_secret
		 WHERE secret_type = $1
		   AND status = $2
		   AND deleted_flag = false
	`, secretType, securityKeyStatusRetired).Scan(&count)
	return count, err
}

func (s SecurityKeyService) totpProtectedCount(ctx context.Context) (int, error) {
	if s.db == nil {
		return 0, errors.New("database client is not configured")
	}
	var count int
	err := s.db.QueryRow(ctx, `
		SELECT count(*)
		  FROM sys_user
		 WHERE totp_secret IS NOT NULL
		   AND deleted_flag = false
	`).Scan(&count)
	return count, err
}

func (s SecurityKeyService) reencryptTotpSecretsWithQuerier(ctx context.Context, q securityKeyQuerier, oldSecret string, newSecret string) (int, error) {
	rows, err := q.Query(ctx, `
		SELECT id, login_name, totp_secret, COALESCE(totp_enabled, false)
		  FROM sys_user
		 WHERE totp_secret IS NOT NULL
		   AND deleted_flag = false
	`)
	if err != nil {
		return 0, err
	}
	defer rows.Close()
	type update struct {
		id     int64
		secret sql.NullString
	}
	updates := []update{}
	invalidEnabled := []string{}
	for rows.Next() {
		var id int64
		var loginName string
		var encrypted sql.NullString
		var enabled bool
		if err := rows.Scan(&id, &loginName, &encrypted, &enabled); err != nil {
			return 0, err
		}
		if !encrypted.Valid || strings.TrimSpace(encrypted.String) == "" {
			if enabled {
				invalidEnabled = append(invalidEnabled, loginName)
			} else {
				updates = append(updates, update{id: id})
			}
			continue
		}
		plain, err := decryptTotpSecretWithMaterial(encrypted.String, oldSecret)
		if err != nil {
			if enabled {
				invalidEnabled = append(invalidEnabled, loginName)
			} else {
				updates = append(updates, update{id: id})
			}
			continue
		}
		rotated, err := encryptTotpSecretWithMaterial(plain, newSecret)
		if err != nil {
			return 0, err
		}
		updates = append(updates, update{id: id, secret: sql.NullString{String: rotated, Valid: true}})
	}
	if err := rows.Err(); err != nil {
		return 0, err
	}
	if len(invalidEnabled) > 0 {
		return 0, NewAuthError(AuthErrorBusiness, "以下账号的2FA密钥无法使用当前主密钥解密，请先禁用并重新生成后再轮转: "+strings.Join(invalidEnabled, ", "))
	}
	for _, item := range updates {
		if _, err := q.Exec(ctx, `
			UPDATE sys_user
			   SET totp_secret = $2,
			       updated_at = CURRENT_TIMESTAMP
			 WHERE id = $1
		`, item.id, item.secret); err != nil {
			return 0, err
		}
	}
	return len(updates), nil
}

func (s SecurityKeyService) securityKeyRotateResponse(ctx context.Context, keyCode string, active securitySecretRecord, processed int, remark string) (SecurityKeyRotateResponse, error) {
	retired, err := s.retiredSecurityKeyCount(ctx, keyCode)
	if err != nil {
		return SecurityKeyRotateResponse{}, err
	}
	return SecurityKeyRotateResponse{
		KeyCode:              keyCode,
		Source:               secretSourceDB,
		ActiveVersion:        active.KeyVersion,
		ActiveFingerprint:    fingerprintSecret(active.SecretValue),
		RotatedAt:            active.ActivatedAt,
		ProcessedRecordCount: processed,
		RetiredVersionCount:  retired,
		Remark:               remark,
	}, nil
}

func (s SecurityKeyService) fallbackSecret(secretType string) string {
	if secretType == jwtSecretType {
		return s.jwtSecret
	}
	return os.Getenv("TOTP_ENCRYPTION_KEY")
}

func (s SecurityKeyService) fallbackName(secretType string) string {
	if secretType == jwtSecretType {
		return "LEO_JWT_SECRET"
	}
	return "TOTP_ENCRYPTION_KEY"
}

type securitySecretRecord struct {
	ID          int64
	SecretType  string
	SecretName  string
	KeyVersion  int
	SecretValue string
	Status      string
	ActivatedAt time.Time
	RetiredAt   sql.NullTime
	Remark      string
}

type secretMaterialRecord struct {
	Source      string
	Version     int
	Value       string
	ActivatedAt sql.NullTime
	RetiredAt   sql.NullTime
}

type securityKeyQuerier interface {
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

func randomSecuritySecret(byteSize int) (string, error) {
	raw := make([]byte, byteSize)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func fingerprintSecret(value string) string {
	sum := sha256.Sum256([]byte(value))
	return strings.ToUpper(hex.EncodeToString(sum[:])[:12])
}

func decryptTotpSecretWithMaterial(encryptedSecret string, secret string) (string, error) {
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(encryptedSecret))
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(deriveAESKey(secret))
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	if len(raw) < gcm.NonceSize() {
		return "", errors.New("invalid encrypted secret")
	}
	plain, err := gcm.Open(nil, raw[:gcm.NonceSize()], raw[gcm.NonceSize():], nil)
	if err != nil {
		return "", err
	}
	return string(plain), nil
}

func encryptTotpSecretWithMaterial(plainSecret string, secret string) (string, error) {
	block, err := aes.NewCipher(deriveAESKey(secret))
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	ciphertext := gcm.Seal(nil, nonce, []byte(strings.TrimSpace(plainSecret)), nil)
	return base64.StdEncoding.EncodeToString(append(nonce, ciphertext...)), nil
}
