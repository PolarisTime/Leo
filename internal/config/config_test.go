package config

import (
	"strings"
	"testing"
	"time"
)

func TestDatabaseConfigConnStringUsesJavaDefaults(t *testing.T) {
	cfg := DatabaseConfig{
		Host:            "localhost",
		Port:            5432,
		Name:            "leo",
		Username:        "leo",
		ApplicationName: "leo-erp",
	}

	connString := cfg.ConnString()
	for _, want := range []string{
		"postgresql://leo@localhost:5432/leo",
		"application_name=leo-erp",
		"sslmode=disable",
	} {
		if !strings.Contains(connString, want) {
			t.Fatalf("connection string %q should contain %q", connString, want)
		}
	}
}

func TestNormalizeJdbcURL(t *testing.T) {
	got := normalizeJdbcURL("jdbc:postgresql://localhost:5432/leo")
	if got != "postgresql://localhost:5432/leo" {
		t.Fatalf("normalized url = %q", got)
	}
}

func TestEnvDurationSupportsJavaMillisecondValues(t *testing.T) {
	t.Setenv("SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", "3000")

	got := envDuration("SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT", time.Second)
	if got != 3*time.Second {
		t.Fatalf("duration = %s, want 3s", got)
	}
}
