package platform

import (
	"context"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/leo-erp/leo/internal/config"
)

func NewPostgresPool(ctx context.Context, cfg config.DatabaseConfig) (*pgxpool.Pool, error) {
	poolConfig, err := pgxpool.ParseConfig(cfg.ConnString())
	if err != nil {
		return nil, err
	}
	poolConfig.MaxConns = cfg.MaxConns
	poolConfig.MinConns = cfg.MinConns
	poolConfig.MaxConnIdleTime = cfg.MaxConnIdleTime
	poolConfig.MaxConnLifetime = cfg.MaxConnLifetime
	poolConfig.HealthCheckPeriod = cfg.HealthCheckPeriod
	poolConfig.ConnConfig.ConnectTimeout = cfg.ConnectionTimeout
	poolConfig.ConnConfig.StatementCacheCapacity = cfg.StatementCacheCapacity
	poolConfig.ConnConfig.DescriptionCacheCapacity = cfg.DescriptionCacheCapacity
	return pgxpool.NewWithConfig(ctx, poolConfig)
}
