package platform

import (
	"context"
	"log/slog"
	"syscall"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/redis/go-redis/v9"
)

type HealthService struct {
	db     *pgxpool.Pool
	redis  *redis.Client
	logger *slog.Logger
}

type HealthCheck struct {
	Status  string `json:"status"`
	FreeGB  uint64 `json:"freeGb"`
	TotalGB uint64 `json:"totalGb"`
}

type Health struct {
	DB    HealthCheck
	Redis HealthCheck
	Disk  HealthCheck
}

func NewHealthService(db *pgxpool.Pool, redis *redis.Client, logger *slog.Logger) HealthService {
	return HealthService{db: db, redis: redis, logger: logger}
}

func (s HealthService) Check(ctx context.Context) Health {
	return Health{
		DB:    s.checkDatabase(ctx),
		Redis: s.checkRedis(ctx),
		Disk:  checkDisk(),
	}
}

func (s HealthService) checkDatabase(ctx context.Context) HealthCheck {
	if s.db == nil {
		return HealthCheck{Status: "DOWN"}
	}
	var value int
	if err := s.db.QueryRow(ctx, "SELECT 1").Scan(&value); err != nil || value != 1 {
		if err != nil && s.logger != nil {
			s.logger.Warn("health database check failed", "error", err)
		}
		return HealthCheck{Status: "DOWN"}
	}
	return HealthCheck{Status: "UP"}
}

func (s HealthService) checkRedis(ctx context.Context) HealthCheck {
	if s.redis == nil {
		return HealthCheck{Status: "DOWN"}
	}
	if err := s.redis.Ping(ctx).Err(); err != nil {
		if s.logger != nil {
			s.logger.Warn("health redis check failed", "error", err)
		}
		return HealthCheck{Status: "DOWN"}
	}
	return HealthCheck{Status: "UP"}
}

func checkDisk() HealthCheck {
	var stat syscall.Statfs_t
	if err := syscall.Statfs("/", &stat); err != nil {
		return HealthCheck{Status: "DOWN"}
	}

	const gib = 1024 * 1024 * 1024
	freeGB := stat.Bavail * uint64(stat.Bsize) / gib
	totalGB := stat.Blocks * uint64(stat.Bsize) / gib
	status := "UP"
	if freeGB < 1 {
		status = "WARN"
	}
	return HealthCheck{Status: status, FreeGB: freeGB, TotalGB: totalGB}
}
