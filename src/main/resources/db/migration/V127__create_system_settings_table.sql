-- Create system_settings table (previously managed outside Flyway)
CREATE TABLE IF NOT EXISTS system_settings (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  setting_code VARCHAR(128) NOT NULL,
  setting_name VARCHAR(255),
  bill_name VARCHAR(100),
  prefix VARCHAR(20) DEFAULT 'SYS',
  date_rule VARCHAR(50) DEFAULT 'NONE',
  serial_length INT DEFAULT 6,
  reset_rule VARCHAR(50) DEFAULT 'NEVER',
  sample_no VARCHAR(500),
  status VARCHAR(20) DEFAULT '正常',
  remark TEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_system_settings_code ON system_settings(setting_code);
