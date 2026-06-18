package platform

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/leo-erp/leo/internal/config"
	"github.com/redis/go-redis/v9"
)

type DatabaseStatusService struct {
	db    *pgxpool.Pool
	redis *redis.Client
	dbCfg config.DatabaseConfig
	rdCfg config.RedisConfig
}

type DatabaseStatus struct {
	Postgres PostgresStatus `json:"postgres"`
	Redis    RedisStatus    `json:"redis"`
}

type PostgresStatus struct {
	Host              string     `json:"host"`
	Port              int        `json:"port"`
	Database          string     `json:"database"`
	Version           string     `json:"version"`
	TotalConnections  int64      `json:"totalConnections"`
	ActiveConnections int64      `json:"activeConnections"`
	MaxConnections    int64      `json:"maxConnections"`
	DatabaseSize      string     `json:"databaseSize"`
	TableCount        int64      `json:"tableCount"`
	ServerStartTime   *time.Time `json:"serverStartTime"`
	Status            string     `json:"status"`
}

type RedisStatus struct {
	Host             string  `json:"host"`
	Port             int     `json:"port"`
	Database         int     `json:"database"`
	Version          string  `json:"version"`
	UsedMemory       int64   `json:"usedMemory"`
	UsedMemoryPeak   int64   `json:"usedMemoryPeak"`
	TotalKeys        int64   `json:"totalKeys"`
	ConnectedClients int64   `json:"connectedClients"`
	Uptime           string  `json:"uptime"`
	HitCount         int64   `json:"hitCount"`
	MissCount        int64   `json:"missCount"`
	HitRate          float64 `json:"hitRate"`
	Status           string  `json:"status"`
}

type DatabaseMonitoring struct {
	Available   bool                   `json:"available"`
	Status      string                 `json:"status"`
	Overview    PostgresOverview       `json:"overview"`
	Activity    PostgresActivity       `json:"activity"`
	Tuning      PostgresTuningSettings `json:"tuning"`
	TableHealth []TableHealthItem      `json:"tableHealth"`
	IndexHealth []IndexHealthItem      `json:"indexHealth"`
	QueryStats  QueryStats             `json:"queryStats"`
	Redis       RedisMonitoring        `json:"redis"`
}

type PostgresOverview struct {
	TotalConnections             int64   `json:"totalConnections"`
	ActiveConnections            int64   `json:"activeConnections"`
	IdleInTransactionConnections int64   `json:"idleInTransactionConnections"`
	LockWaitSessions             int64   `json:"lockWaitSessions"`
	BlockedSessions              int64   `json:"blockedSessions"`
	LongTransactions             int64   `json:"longTransactions"`
	LongestTransactionSeconds    int64   `json:"longestTransactionSeconds"`
	LongestQuerySeconds          int64   `json:"longestQuerySeconds"`
	XactCommit                   int64   `json:"xactCommit"`
	XactRollback                 int64   `json:"xactRollback"`
	Deadlocks                    int64   `json:"deadlocks"`
	TempFiles                    int64   `json:"tempFiles"`
	TempBytes                    int64   `json:"tempBytes"`
	CacheHitRate                 float64 `json:"cacheHitRate"`
	DatabaseSize                 string  `json:"databaseSize"`
	UptimeSeconds                int64   `json:"uptimeSeconds"`
}

type PostgresActivity struct {
	ActiveSessions            int64 `json:"activeSessions"`
	IdleInTransactionSessions int64 `json:"idleInTransactionSessions"`
	LockWaitSessions          int64 `json:"lockWaitSessions"`
	BlockedSessions           int64 `json:"blockedSessions"`
	LongTransactions          int64 `json:"longTransactions"`
	LongestTransactionSeconds int64 `json:"longestTransactionSeconds"`
	LongestQuerySeconds       int64 `json:"longestQuerySeconds"`
}

type PostgresTuningSettings struct {
	MaxConnections                  int64  `json:"maxConnections"`
	TotalConnections                int64  `json:"totalConnections"`
	ActiveConnections               int64  `json:"activeConnections"`
	HikariMaximumPoolSize           int32  `json:"hikariMaximumPoolSize"`
	HikariMinimumIdle               int32  `json:"hikariMinimumIdle"`
	HikariLeakDetectionThresholdMs  int64  `json:"hikariLeakDetectionThresholdMs"`
	StatementTimeout                string `json:"statementTimeout"`
	IdleInTransactionSessionTimeout string `json:"idleInTransactionSessionTimeout"`
	LockTimeout                     string `json:"lockTimeout"`
	TrackIoTiming                   string `json:"trackIoTiming"`
	SharedBuffers                   string `json:"sharedBuffers"`
	EffectiveCacheSize              string `json:"effectiveCacheSize"`
	WorkMem                         string `json:"workMem"`
	MaintenanceWorkMem              string `json:"maintenanceWorkMem"`
	MaxWalSize                      string `json:"maxWalSize"`
	CheckpointTimeout               string `json:"checkpointTimeout"`
	PgStatStatementsTrack           string `json:"pgStatStatementsTrack"`
}

type TableHealthItem struct {
	TableName                 string  `json:"tableName"`
	LiveRows                  int64   `json:"liveRows"`
	DeadRows                  int64   `json:"deadRows"`
	DeadPct                   float64 `json:"deadPct"`
	SeqScan                   int64   `json:"seqScan"`
	IdxScan                   int64   `json:"idxScan"`
	NModSinceAnalyze          int64   `json:"nModSinceAnalyze"`
	HeapCachePct              float64 `json:"heapCachePct"`
	VacuumTriggerRows         int64   `json:"vacuumTriggerRows"`
	AnalyzeTriggerRows        int64   `json:"analyzeTriggerRows"`
	LastAutovacuumAgeSeconds  *int64  `json:"lastAutovacuumAgeSeconds"`
	LastAutoanalyzeAgeSeconds *int64  `json:"lastAutoanalyzeAgeSeconds"`
	AutovacuumStatus          string  `json:"autovacuumStatus"`
	AutovacuumAdvice          string  `json:"autovacuumAdvice"`
	LastVacuum                *string `json:"lastVacuum"`
	LastAutovacuum            *string `json:"lastAutovacuum"`
	LastAnalyze               *string `json:"lastAnalyze"`
	LastAutoanalyze           *string `json:"lastAutoanalyze"`
}

type IndexHealthItem struct {
	IndexName     string `json:"indexName"`
	TableName     string `json:"tableName"`
	Size          string `json:"size"`
	SizeBytes     int64  `json:"sizeBytes"`
	Scans         int64  `json:"scans"`
	TuplesRead    int64  `json:"tuplesRead"`
	TuplesFetched int64  `json:"tuplesFetched"`
	Valid         bool   `json:"valid"`
	Unique        bool   `json:"unique"`
	Primary       bool   `json:"primary"`
}

type QueryStats struct {
	Available bool             `json:"available"`
	Status    string           `json:"status"`
	Items     []QueryStatsItem `json:"items"`
}

type QueryStatsItem struct {
	QueryID      string  `json:"queryId"`
	QueryPreview string  `json:"queryPreview"`
	Calls        int64   `json:"calls"`
	TotalMs      float64 `json:"totalMs"`
	AvgMs        float64 `json:"avgMs"`
	Rows         int64   `json:"rows"`
	CacheHitPct  float64 `json:"cacheHitPct"`
}

type RedisMonitoring struct {
	Memory      RedisMemoryItem      `json:"memory"`
	Clients     RedisClientItem      `json:"clients"`
	Throughput  RedisThroughputItem  `json:"throughput"`
	Keyspace    RedisKeyspaceItem    `json:"keyspace"`
	Persistence RedisPersistenceItem `json:"persistence"`
	Status      string               `json:"status"`
}

type RedisMemoryItem struct {
	UsedMemory         int64   `json:"usedMemory"`
	UsedMemoryPeak     int64   `json:"usedMemoryPeak"`
	MaxMemory          int64   `json:"maxMemory"`
	FragmentationRatio float64 `json:"fragmentationRatio"`
	EvictedKeys        int64   `json:"evictedKeys"`
	ExpiredKeys        int64   `json:"expiredKeys"`
}

type RedisClientItem struct {
	ConnectedClients    int64 `json:"connectedClients"`
	BlockedClients      int64 `json:"blockedClients"`
	RejectedConnections int64 `json:"rejectedConnections"`
}

type RedisThroughputItem struct {
	TotalCommandsProcessed int64   `json:"totalCommandsProcessed"`
	InstantaneousOpsPerSec int64   `json:"instantaneousOpsPerSec"`
	KeyspaceHits           int64   `json:"keyspaceHits"`
	KeyspaceMisses         int64   `json:"keyspaceMisses"`
	HitRate                float64 `json:"hitRate"`
}

type RedisKeyspaceItem struct {
	Database int   `json:"database"`
	Keys     int64 `json:"keys"`
	Expires  int64 `json:"expires"`
	AvgTtlMs int64 `json:"avgTtlMs"`
}

type RedisPersistenceItem struct {
	RdbLastSaveTime        int64  `json:"rdbLastSaveTime"`
	RdbLastBgsaveStatus    string `json:"rdbLastBgsaveStatus"`
	AofEnabled             bool   `json:"aofEnabled"`
	AofLastBgrewriteStatus string `json:"aofLastBgrewriteStatus"`
}

func NewDatabaseStatusService(db *pgxpool.Pool, redis *redis.Client, dbCfg config.DatabaseConfig, rdCfg config.RedisConfig) DatabaseStatusService {
	return DatabaseStatusService{db: db, redis: redis, dbCfg: dbCfg, rdCfg: rdCfg}
}

func (s DatabaseStatusService) Status(ctx context.Context) DatabaseStatus {
	return DatabaseStatus{
		Postgres: s.postgresStatus(ctx),
		Redis:    s.redisStatus(ctx),
	}
}

func (s DatabaseStatusService) Monitoring(ctx context.Context) DatabaseMonitoring {
	redisMonitoring := s.redisMonitoring(ctx)
	postgresStatus := s.postgresStatus(ctx)
	if !strings.EqualFold(postgresStatus.Status, "正常") {
		return DatabaseMonitoring{
			Available:   false,
			Status:      postgresStatus.Status,
			Overview:    emptyPostgresOverview(),
			Activity:    PostgresActivity{},
			Tuning:      emptyPostgresTuning(),
			TableHealth: []TableHealthItem{},
			IndexHealth: []IndexHealthItem{},
			QueryStats:  QueryStats{Available: false, Status: "PostgreSQL 监控不可用", Items: []QueryStatsItem{}},
			Redis:       redisMonitoring,
		}
	}
	overview := s.postgresOverview(ctx, postgresStatus)
	activity := PostgresActivity{
		ActiveSessions:            overview.ActiveConnections,
		IdleInTransactionSessions: overview.IdleInTransactionConnections,
		LockWaitSessions:          overview.LockWaitSessions,
		BlockedSessions:           overview.BlockedSessions,
		LongTransactions:          overview.LongTransactions,
		LongestTransactionSeconds: overview.LongestTransactionSeconds,
		LongestQuerySeconds:       overview.LongestQuerySeconds,
	}
	return DatabaseMonitoring{
		Available:   true,
		Status:      "正常",
		Overview:    overview,
		Activity:    activity,
		Tuning:      s.postgresTuning(ctx, postgresStatus),
		TableHealth: s.tableHealth(ctx),
		IndexHealth: s.indexHealth(ctx),
		QueryStats:  s.queryStats(ctx),
		Redis:       redisMonitoring,
	}
}

func (s DatabaseStatusService) postgresStatus(ctx context.Context) PostgresStatus {
	host, port, dbName := s.postgresAddress()
	status := PostgresStatus{Host: host, Port: port, Database: dbName, Version: "未知", DatabaseSize: "未知", Status: "异常: database client is not configured"}
	if s.db == nil {
		return status
	}
	if err := s.db.QueryRow(ctx, "SELECT version()").Scan(&status.Version); err != nil {
		status.Status = "异常: " + err.Error()
		return status
	}
	_ = s.db.QueryRow(ctx, `
		SELECT count(1), COALESCE(sum(CASE WHEN state = 'active' THEN 1 ELSE 0 END), 0)
		  FROM pg_stat_activity
		 WHERE datname = current_database()
	`).Scan(&status.TotalConnections, &status.ActiveConnections)
	_ = s.db.QueryRow(ctx, "SHOW max_connections").Scan(&status.MaxConnections)
	_ = s.db.QueryRow(ctx, "SELECT pg_size_pretty(pg_database_size(current_database()))").Scan(&status.DatabaseSize)
	_ = s.db.QueryRow(ctx, "SELECT count(1) FROM information_schema.tables WHERE table_schema = 'public'").Scan(&status.TableCount)
	var started sql.NullTime
	if err := s.db.QueryRow(ctx, "SELECT pg_postmaster_start_time()").Scan(&started); err == nil && started.Valid {
		startedAt := started.Time
		status.ServerStartTime = &startedAt
	}
	status.Status = "正常"
	return status
}

func (s DatabaseStatusService) redisStatus(ctx context.Context) RedisStatus {
	status := RedisStatus{
		Host:     s.rdCfg.Host,
		Port:     s.rdCfg.Port,
		Database: s.rdCfg.Database,
		Version:  "未知",
		Uptime:   "未知",
		Status:   "异常: redis client is not configured",
	}
	if s.redis == nil {
		return status
	}
	info, err := s.redis.Info(ctx, "server", "memory", "keyspace", "clients", "stats").Result()
	if err != nil {
		status.Status = "异常: " + err.Error()
		return status
	}
	props := parseRedisInfo(info)
	status.Version = props["redis_version"]
	if status.Version == "" {
		status.Version = "未知"
	}
	status.UsedMemory = int64Prop(props, "used_memory")
	status.UsedMemoryPeak = int64Prop(props, "used_memory_peak")
	status.ConnectedClients = int64Prop(props, "connected_clients")
	uptimeSeconds := int64Prop(props, "uptime_in_seconds")
	status.Uptime = formatUptimeSeconds(uptimeSeconds)
	status.HitCount = int64Prop(props, "keyspace_hits")
	status.MissCount = int64Prop(props, "keyspace_misses")
	status.HitRate = roundTwo(calculateRate(status.HitCount, status.MissCount))
	dbInfo := props["db"+strconv.Itoa(s.rdCfg.Database)]
	status.TotalKeys = parseRedisKeyspace(dbInfo, "keys")
	status.Status = "正常"
	return status
}

func (s DatabaseStatusService) redisMonitoring(ctx context.Context) RedisMonitoring {
	if s.redis == nil {
		return unavailableRedisMonitoring(s.rdCfg.Database, "异常: redis client is not configured")
	}
	info, err := s.redis.Info(ctx, "memory", "clients", "stats", "keyspace", "persistence").Result()
	if err != nil {
		return unavailableRedisMonitoring(s.rdCfg.Database, "异常: "+err.Error())
	}
	props := parseRedisInfo(info)
	hits := int64Prop(props, "keyspace_hits")
	misses := int64Prop(props, "keyspace_misses")
	dbInfo := props["db"+strconv.Itoa(s.rdCfg.Database)]
	return RedisMonitoring{
		Memory: RedisMemoryItem{
			UsedMemory:         int64Prop(props, "used_memory"),
			UsedMemoryPeak:     int64Prop(props, "used_memory_peak"),
			MaxMemory:          int64Prop(props, "maxmemory"),
			FragmentationRatio: roundTwo(float64Prop(props, "mem_fragmentation_ratio")),
			EvictedKeys:        int64Prop(props, "evicted_keys"),
			ExpiredKeys:        int64Prop(props, "expired_keys"),
		},
		Clients: RedisClientItem{
			ConnectedClients:    int64Prop(props, "connected_clients"),
			BlockedClients:      int64Prop(props, "blocked_clients"),
			RejectedConnections: int64Prop(props, "rejected_connections"),
		},
		Throughput: RedisThroughputItem{
			TotalCommandsProcessed: int64Prop(props, "total_commands_processed"),
			InstantaneousOpsPerSec: int64Prop(props, "instantaneous_ops_per_sec"),
			KeyspaceHits:           hits,
			KeyspaceMisses:         misses,
			HitRate:                roundTwo(calculateRate(hits, misses)),
		},
		Keyspace: RedisKeyspaceItem{
			Database: s.rdCfg.Database,
			Keys:     parseRedisKeyspace(dbInfo, "keys"),
			Expires:  parseRedisKeyspace(dbInfo, "expires"),
			AvgTtlMs: parseRedisKeyspace(dbInfo, "avg_ttl"),
		},
		Persistence: RedisPersistenceItem{
			RdbLastSaveTime:        int64Prop(props, "rdb_last_save_time"),
			RdbLastBgsaveStatus:    stringProp(props, "rdb_last_bgsave_status", "未知"),
			AofEnabled:             int64Prop(props, "aof_enabled") == 1,
			AofLastBgrewriteStatus: stringProp(props, "aof_last_bgrewrite_status", "未知"),
		},
		Status: "正常",
	}
}

func (s DatabaseStatusService) postgresOverview(ctx context.Context, status PostgresStatus) PostgresOverview {
	overview := emptyPostgresOverview()
	overview.TotalConnections = status.TotalConnections
	overview.ActiveConnections = status.ActiveConnections
	overview.DatabaseSize = status.DatabaseSize
	if s.db == nil {
		return overview
	}
	_ = s.db.QueryRow(ctx, `
		SELECT
			COALESCE(sum(CASE WHEN state = 'idle in transaction' THEN 1 ELSE 0 END), 0),
			COALESCE(sum(CASE WHEN wait_event_type = 'Lock' THEN 1 ELSE 0 END), 0),
			COALESCE(sum(CASE WHEN now() - xact_start > interval '5 minutes' THEN 1 ELSE 0 END), 0),
			COALESCE(max(EXTRACT(EPOCH FROM now() - xact_start))::bigint, 0),
			COALESCE(max(EXTRACT(EPOCH FROM now() - query_start))::bigint, 0)
		  FROM pg_stat_activity
		 WHERE datname = current_database()
	`).Scan(&overview.IdleInTransactionConnections, &overview.LockWaitSessions, &overview.LongTransactions, &overview.LongestTransactionSeconds, &overview.LongestQuerySeconds)
	_ = s.db.QueryRow(ctx, `
		SELECT count(*)
		  FROM pg_stat_activity blocked
		 WHERE blocked.wait_event_type = 'Lock'
		   AND blocked.datname = current_database()
	`).Scan(&overview.BlockedSessions)
	_ = s.db.QueryRow(ctx, `
		SELECT xact_commit, xact_rollback, deadlocks, temp_files, temp_bytes,
		       CASE WHEN blks_hit + blks_read = 0 THEN 0
		            ELSE round((blks_hit::numeric / (blks_hit + blks_read)) * 100, 2)
		        END,
		       EXTRACT(EPOCH FROM now() - stats_reset)::bigint
		  FROM pg_stat_database
		 WHERE datname = current_database()
	`).Scan(&overview.XactCommit, &overview.XactRollback, &overview.Deadlocks, &overview.TempFiles, &overview.TempBytes, &overview.CacheHitRate, &overview.UptimeSeconds)
	return overview
}

func (s DatabaseStatusService) postgresTuning(ctx context.Context, status PostgresStatus) PostgresTuningSettings {
	tuning := emptyPostgresTuning()
	tuning.MaxConnections = status.MaxConnections
	tuning.TotalConnections = status.TotalConnections
	tuning.ActiveConnections = status.ActiveConnections
	tuning.HikariMaximumPoolSize = s.dbCfg.MaxConns
	tuning.HikariMinimumIdle = s.dbCfg.MinConns
	if s.db == nil {
		return tuning
	}
	settings := []struct {
		name   string
		target *string
	}{
		{"statement_timeout", &tuning.StatementTimeout},
		{"idle_in_transaction_session_timeout", &tuning.IdleInTransactionSessionTimeout},
		{"lock_timeout", &tuning.LockTimeout},
		{"track_io_timing", &tuning.TrackIoTiming},
		{"shared_buffers", &tuning.SharedBuffers},
		{"effective_cache_size", &tuning.EffectiveCacheSize},
		{"work_mem", &tuning.WorkMem},
		{"maintenance_work_mem", &tuning.MaintenanceWorkMem},
		{"max_wal_size", &tuning.MaxWalSize},
		{"checkpoint_timeout", &tuning.CheckpointTimeout},
		{"pg_stat_statements.track", &tuning.PgStatStatementsTrack},
	}
	for _, setting := range settings {
		var value string
		if err := s.db.QueryRow(ctx, "SELECT current_setting($1, true)", setting.name).Scan(&value); err == nil && strings.TrimSpace(value) != "" {
			*setting.target = value
		}
	}
	return tuning
}

func (s DatabaseStatusService) tableHealth(ctx context.Context) []TableHealthItem {
	if s.db == nil {
		return []TableHealthItem{}
	}
	rows, err := s.db.Query(ctx, `
		SELECT relname,
		       n_live_tup,
		       n_dead_tup,
		       CASE WHEN n_live_tup + n_dead_tup = 0 THEN 0
		            ELSE round((n_dead_tup::numeric / (n_live_tup + n_dead_tup)) * 100, 2)
		        END,
		       seq_scan,
		       idx_scan,
		       n_mod_since_analyze,
		       CASE WHEN heap_blks_hit + heap_blks_read = 0 THEN 0
		            ELSE round((heap_blks_hit::numeric / (heap_blks_hit + heap_blks_read)) * 100, 2)
		        END,
		       GREATEST(50, floor(n_live_tup * 0.2))::bigint,
		       GREATEST(50, floor(n_live_tup * 0.1))::bigint,
		       last_vacuum,
		       last_autovacuum,
		       last_analyze,
		       last_autoanalyze
		  FROM pg_stat_user_tables stats
		  LEFT JOIN pg_statio_user_tables io
		    ON io.relid = stats.relid
		 ORDER BY n_dead_tup DESC, relname
		 LIMIT 20
	`)
	if err != nil {
		return []TableHealthItem{}
	}
	defer rows.Close()

	items := []TableHealthItem{}
	for rows.Next() {
		var item TableHealthItem
		var lastVacuum, lastAutovacuum, lastAnalyze, lastAutoanalyze sql.NullTime
		if err := rows.Scan(
			&item.TableName,
			&item.LiveRows,
			&item.DeadRows,
			&item.DeadPct,
			&item.SeqScan,
			&item.IdxScan,
			&item.NModSinceAnalyze,
			&item.HeapCachePct,
			&item.VacuumTriggerRows,
			&item.AnalyzeTriggerRows,
			&lastVacuum,
			&lastAutovacuum,
			&lastAnalyze,
			&lastAutoanalyze,
		); err != nil {
			return []TableHealthItem{}
		}
		item.LastVacuum = formatNullableTime(lastVacuum)
		item.LastAutovacuum = formatNullableTime(lastAutovacuum)
		item.LastAnalyze = formatNullableTime(lastAnalyze)
		item.LastAutoanalyze = formatNullableTime(lastAutoanalyze)
		item.LastAutovacuumAgeSeconds = ageSeconds(lastAutovacuum)
		item.LastAutoanalyzeAgeSeconds = ageSeconds(lastAutoanalyze)
		item.AutovacuumStatus = "正常"
		item.AutovacuumAdvice = "无需处理"
		if item.DeadRows > item.VacuumTriggerRows {
			item.AutovacuumStatus = "需关注"
			item.AutovacuumAdvice = "建议检查 autovacuum 是否及时执行"
		}
		items = append(items, item)
	}
	return items
}

func (s DatabaseStatusService) indexHealth(ctx context.Context) []IndexHealthItem {
	if s.db == nil {
		return []IndexHealthItem{}
	}
	rows, err := s.db.Query(ctx, `
		SELECT indexrelname,
		       relname,
		       pg_size_pretty(pg_relation_size(indexrelid)),
		       pg_relation_size(indexrelid),
		       idx_scan,
		       idx_tup_read,
		       idx_tup_fetch,
		       idx.indisvalid,
		       idx.indisunique,
		       idx.indisprimary
		  FROM pg_stat_user_indexes stats
		  JOIN pg_index idx
		    ON idx.indexrelid = stats.indexrelid
		 ORDER BY pg_relation_size(indexrelid) DESC, indexrelname
		 LIMIT 20
	`)
	if err != nil {
		return []IndexHealthItem{}
	}
	defer rows.Close()

	items := []IndexHealthItem{}
	for rows.Next() {
		var item IndexHealthItem
		if err := rows.Scan(&item.IndexName, &item.TableName, &item.Size, &item.SizeBytes, &item.Scans, &item.TuplesRead, &item.TuplesFetched, &item.Valid, &item.Unique, &item.Primary); err != nil {
			return []IndexHealthItem{}
		}
		items = append(items, item)
	}
	return items
}

func (s DatabaseStatusService) queryStats(ctx context.Context) QueryStats {
	if s.db == nil {
		return QueryStats{Available: false, Status: "PostgreSQL 监控不可用", Items: []QueryStatsItem{}}
	}
	var exists bool
	if err := s.db.QueryRow(ctx, "SELECT to_regclass('public.pg_stat_statements') IS NOT NULL").Scan(&exists); err != nil || !exists {
		return QueryStats{Available: false, Status: "pg_stat_statements 未启用", Items: []QueryStatsItem{}}
	}
	rows, err := s.db.Query(ctx, `
		SELECT queryid::text,
		       regexp_replace(left(query, 160), '\s+', ' ', 'g'),
		       calls,
		       round(total_exec_time::numeric, 2),
		       round(mean_exec_time::numeric, 2),
		       rows,
		       0::numeric
		  FROM pg_stat_statements
		 ORDER BY total_exec_time DESC
		 LIMIT 10
	`)
	if err != nil {
		return QueryStats{Available: false, Status: "pg_stat_statements 查询失败", Items: []QueryStatsItem{}}
	}
	defer rows.Close()

	items := []QueryStatsItem{}
	for rows.Next() {
		var item QueryStatsItem
		if err := rows.Scan(&item.QueryID, &item.QueryPreview, &item.Calls, &item.TotalMs, &item.AvgMs, &item.Rows, &item.CacheHitPct); err != nil {
			return QueryStats{Available: false, Status: "pg_stat_statements 查询失败", Items: []QueryStatsItem{}}
		}
		items = append(items, item)
	}
	return QueryStats{Available: true, Status: "正常", Items: items}
}

func (s DatabaseStatusService) postgresAddress() (string, int, string) {
	if strings.TrimSpace(s.dbCfg.URL) != "" {
		parsed, err := url.Parse(s.dbCfg.URL)
		if err == nil {
			port := s.dbCfg.Port
			if parsed.Port() != "" {
				if parsedPort, err := strconv.Atoi(parsed.Port()); err == nil {
					port = parsedPort
				}
			}
			name := strings.TrimPrefix(parsed.Path, "/")
			if name == "" {
				name = s.dbCfg.Name
			}
			return parsed.Hostname(), port, name
		}
	}
	return s.dbCfg.Host, s.dbCfg.Port, s.dbCfg.Name
}

func emptyPostgresOverview() PostgresOverview {
	return PostgresOverview{DatabaseSize: "未知"}
}

func emptyPostgresTuning() PostgresTuningSettings {
	return PostgresTuningSettings{
		StatementTimeout:                "未知",
		IdleInTransactionSessionTimeout: "未知",
		LockTimeout:                     "未知",
		TrackIoTiming:                   "未知",
		SharedBuffers:                   "未知",
		EffectiveCacheSize:              "未知",
		WorkMem:                         "未知",
		MaintenanceWorkMem:              "未知",
		MaxWalSize:                      "未知",
		CheckpointTimeout:               "未知",
		PgStatStatementsTrack:           "未知",
	}
}

func unavailableRedisMonitoring(database int, status string) RedisMonitoring {
	return RedisMonitoring{
		Memory:      RedisMemoryItem{},
		Clients:     RedisClientItem{},
		Throughput:  RedisThroughputItem{},
		Keyspace:    RedisKeyspaceItem{Database: database},
		Persistence: RedisPersistenceItem{RdbLastBgsaveStatus: "未知", AofLastBgrewriteStatus: "未知"},
		Status:      status,
	}
}

func parseRedisInfo(info string) map[string]string {
	props := map[string]string{}
	for _, line := range strings.Split(info, "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		key, value, ok := strings.Cut(line, ":")
		if ok {
			props[strings.TrimSpace(key)] = strings.TrimSpace(value)
		}
	}
	return props
}

func parseRedisKeyspace(value string, key string) int64 {
	for _, part := range strings.Split(value, ",") {
		name, raw, ok := strings.Cut(strings.TrimSpace(part), "=")
		if !ok || name != key {
			continue
		}
		parsed, _ := strconv.ParseInt(raw, 10, 64)
		return parsed
	}
	return 0
}

func int64Prop(props map[string]string, key string) int64 {
	parsed, _ := strconv.ParseInt(strings.TrimSpace(props[key]), 10, 64)
	return parsed
}

func float64Prop(props map[string]string, key string) float64 {
	parsed, _ := strconv.ParseFloat(strings.TrimSpace(props[key]), 64)
	return parsed
}

func stringProp(props map[string]string, key string, fallback string) string {
	value := strings.TrimSpace(props[key])
	if value == "" {
		return fallback
	}
	return value
}

func calculateRate(hitCount, missCount int64) float64 {
	total := hitCount + missCount
	if total == 0 {
		return 0
	}
	return float64(hitCount) / float64(total)
}

func roundTwo(value float64) float64 {
	rounded, err := strconv.ParseFloat(fmt.Sprintf("%.2f", value), 64)
	if err != nil {
		return value
	}
	return rounded
}

func formatUptimeSeconds(seconds int64) string {
	if seconds <= 0 {
		return "未知"
	}
	days := seconds / 86400
	hours := (seconds % 86400) / 3600
	minutes := (seconds % 3600) / 60
	if days > 0 {
		return fmt.Sprintf("%dd %dh", days, hours)
	}
	if hours > 0 {
		return fmt.Sprintf("%dh %dm", hours, minutes)
	}
	return fmt.Sprintf("%dm", minutes)
}

func formatNullableTime(value sql.NullTime) *string {
	if !value.Valid {
		return nil
	}
	formatted := value.Time.Format("2006-01-02 15:04:05")
	return &formatted
}

func ageSeconds(value sql.NullTime) *int64 {
	if !value.Valid {
		return nil
	}
	seconds := int64(time.Since(value.Time).Seconds())
	return &seconds
}

var _ = errors.Is
