package platform

import (
	"github.com/leo-erp/leo/internal/config"
	"github.com/redis/go-redis/v9"
)

func NewRedisClient(cfg config.RedisConfig) *redis.Client {
	return redis.NewClient(&redis.Options{
		Addr:         cfg.Addr(),
		Password:     cfg.Password,
		DB:           cfg.Database,
		DialTimeout:  cfg.ConnectTimeout,
		ReadTimeout:  cfg.Timeout,
		WriteTimeout: cfg.Timeout,
		PoolSize:     cfg.PoolSize,
		MinIdleConns: cfg.MinIdleConns,
	})
}
