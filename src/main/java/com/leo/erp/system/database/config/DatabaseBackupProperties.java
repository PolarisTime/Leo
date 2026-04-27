package com.leo.erp.system.database.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "leo.database.backup")
public class DatabaseBackupProperties {

    private boolean autoBackupBeforeImport = true;
    private String storagePath = "/tmp/leo/database-backups";
    private int downloadExpireDays = 7;
    private String pgDumpCommand = "pg_dump";
    private String psqlCommand = "psql";

    public boolean isAutoBackupBeforeImport() {
        return autoBackupBeforeImport;
    }

    public void setAutoBackupBeforeImport(boolean autoBackupBeforeImport) {
        this.autoBackupBeforeImport = autoBackupBeforeImport;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public int getDownloadExpireDays() {
        return downloadExpireDays;
    }

    public void setDownloadExpireDays(int downloadExpireDays) {
        this.downloadExpireDays = downloadExpireDays;
    }

    public String getPgDumpCommand() {
        return pgDumpCommand;
    }

    public void setPgDumpCommand(String pgDumpCommand) {
        this.pgDumpCommand = pgDumpCommand;
    }

    public String getPsqlCommand() {
        return psqlCommand;
    }

    public void setPsqlCommand(String psqlCommand) {
        this.psqlCommand = psqlCommand;
    }
}
