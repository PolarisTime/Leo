package config

import (
	"log/slog"
	"net"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	defaultAppName = "leo"
	defaultHost    = "0.0.0.0"
	defaultPort    = 11211
)

type Config struct {
	AppName           string
	Host              string
	Port              int
	LogLevel          slog.Level
	Database          DatabaseConfig
	Redis             RedisConfig
	JWT               JWTConfig
	MachineID         int64
	SetupRequired     bool
	AdminConfigured   bool
	CompanyConfigured bool
}

type DatabaseConfig struct {
	URL                      string
	Host                     string
	Port                     int
	Name                     string
	Username                 string
	Password                 string
	MaxConns                 int32
	MinConns                 int32
	ConnectionTimeout        time.Duration
	ValidationTimeout        time.Duration
	MaxConnIdleTime          time.Duration
	MaxConnLifetime          time.Duration
	HealthCheckPeriod        time.Duration
	ApplicationName          string
	StatementCacheCapacity   int
	DescriptionCacheCapacity int
}

type RedisConfig struct {
	Host           string
	Port           int
	Password       string
	Database       int
	Timeout        time.Duration
	ConnectTimeout time.Duration
	PoolSize       int
	MinIdleConns   int
}

type JWTConfig struct {
	Issuer              string
	Secret              string
	AccessExpiration    time.Duration
	RefreshExpiration   time.Duration
	RefreshCookieSecure bool
}

func Load() Config {
	return Config{
		AppName:           envString("SPRING_APPLICATION_NAME", envString("LEO_APP_NAME", defaultAppName)),
		Host:              envString("SERVER_HOST", defaultHost),
		Port:              envInt("SERVER_PORT", defaultPort),
		LogLevel:          envLogLevel("LOG_LEVEL", slog.LevelInfo),
		Database:          loadDatabaseConfig(),
		Redis:             loadRedisConfig(),
		JWT:               loadJWTConfig(),
		MachineID:         int64(envInt("LEO_MACHINE_ID", 0)),
		SetupRequired:     envBool("LEO_SETUP_REQUIRED", true),
		AdminConfigured:   envBool("LEO_ADMIN_CONFIGURED", false),
		CompanyConfigured: envBool("LEO_COMPANY_CONFIGURED", false),
	}
}

func (cfg Config) Addr() string {
	return net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
}

func (cfg DatabaseConfig) ConnString() string {
	if strings.TrimSpace(cfg.URL) != "" {
		return cfg.URL
	}
	databaseURL := &url.URL{
		Scheme: "postgresql",
		Host:   net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port)),
		Path:   cfg.Name,
	}
	if cfg.Password != "" {
		databaseURL.User = url.UserPassword(cfg.Username, cfg.Password)
	} else {
		databaseURL.User = url.User(cfg.Username)
	}
	query := databaseURL.Query()
	query.Set("application_name", cfg.ApplicationName)
	query.Set("sslmode", "disable")
	databaseURL.RawQuery = query.Encode()
	return databaseURL.String()
}

func (cfg RedisConfig) Addr() string {
	return net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
}

func loadDatabaseConfig() DatabaseConfig {
	return DatabaseConfig{
		URL:                      normalizeJdbcURL(envString("SPRING_DATASOURCE_URL", "")),
		Host:                     envString("SPRING_DATASOURCE_HOST", "localhost"),
		Port:                     envInt("SPRING_DATASOURCE_PORT", 5432),
		Name:                     envString("SPRING_DATASOURCE_DB", "leo"),
		Username:                 envString("SPRING_DATASOURCE_USERNAME", "leo"),
		Password:                 envString("SPRING_DATASOURCE_PASSWORD", ""),
		MaxConns:                 int32(envInt("SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE", 20)),
		MinConns:                 int32(envInt("SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE", 5)),
		ConnectionTimeout:        envDuration("SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", 3*time.Second),
		ValidationTimeout:        envDuration("SPRING_DATASOURCE_HIKARI_VALIDATION_TIMEOUT", time.Second),
		MaxConnIdleTime:          envDuration("SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT", 10*time.Minute),
		MaxConnLifetime:          envDuration("SPRING_DATASOURCE_HIKARI_MAX_LIFETIME", 30*time.Minute),
		HealthCheckPeriod:        envDuration("SPRING_DATASOURCE_HIKARI_KEEPALIVE_TIME", 5*time.Minute),
		ApplicationName:          envString("SPRING_DATASOURCE_APPLICATION_NAME", "leo-erp"),
		StatementCacheCapacity:   envInt("SPRING_DATASOURCE_PREPARED_STATEMENT_CACHE_QUERIES", 256),
		DescriptionCacheCapacity: envInt("SPRING_DATASOURCE_PREPARED_STATEMENT_CACHE_QUERIES", 256),
	}
}

func loadRedisConfig() RedisConfig {
	return RedisConfig{
		Host:           envString("SPRING_DATA_REDIS_HOST", "127.0.0.1"),
		Port:           envInt("SPRING_DATA_REDIS_PORT", 16379),
		Password:       envString("SPRING_DATA_REDIS_PASSWORD", ""),
		Database:       envInt("SPRING_DATA_REDIS_DATABASE", 3),
		Timeout:        envDuration("SPRING_DATA_REDIS_TIMEOUT", 2*time.Second),
		ConnectTimeout: envDuration("SPRING_DATA_REDIS_CONNECT_TIMEOUT", time.Second),
		PoolSize:       envInt("SPRING_DATA_REDIS_POOL_MAX_ACTIVE", 32),
		MinIdleConns:   envInt("SPRING_DATA_REDIS_POOL_MIN_IDLE", 4),
	}
}

func loadJWTConfig() JWTConfig {
	return JWTConfig{
		Issuer:              envString("LEO_SECURITY_JWT_ISSUER", "leo-erp"),
		Secret:              envString("LEO_JWT_SECRET", ""),
		AccessExpiration:    envDuration("LEO_SECURITY_JWT_ACCESS_EXPIRATION_MS", 10*time.Minute),
		RefreshExpiration:   envDuration("LEO_SECURITY_JWT_REFRESH_EXPIRATION_MS", 7*24*time.Hour),
		RefreshCookieSecure: envBool("LEO_AUTH_REFRESH_COOKIE_SECURE", false),
	}
}

func envString(key string, fallback string) string {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	return value
}

func envInt(key string, fallback int) int {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		return fallback
	}
	return parsed
}

func envDuration(key string, fallback time.Duration) time.Duration {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	if parsed, err := time.ParseDuration(value); err == nil {
		return parsed
	}
	if parsed, err := strconv.Atoi(value); err == nil {
		return time.Duration(parsed) * time.Millisecond
	}
	return fallback
}

func envBool(key string, fallback bool) bool {
	value := strings.TrimSpace(os.Getenv(key))
	if value == "" {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fallback
	}
	return parsed
}

func normalizeJdbcURL(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	const prefix = "jdbc:"
	if strings.HasPrefix(value, prefix) {
		return strings.TrimPrefix(value, prefix)
	}
	if strings.HasPrefix(value, "postgresql://") || strings.HasPrefix(value, "postgres://") {
		return value
	}
	return "postgresql://" + value
}

func envLogLevel(key string, fallback slog.Level) slog.Level {
	switch strings.ToLower(strings.TrimSpace(os.Getenv(key))) {
	case "debug":
		return slog.LevelDebug
	case "warn", "warning":
		return slog.LevelWarn
	case "error":
		return slog.LevelError
	default:
		return fallback
	}
}
