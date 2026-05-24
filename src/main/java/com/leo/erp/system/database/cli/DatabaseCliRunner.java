package com.leo.erp.system.database.cli;

import com.leo.erp.system.database.service.DatabaseBackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@ConditionalOnProperty(name = "leo.database.cli.enabled", havingValue = "true")
public class DatabaseCliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCliRunner.class);

    private final DatabaseBackupService backupService;

    public DatabaseCliRunner(DatabaseBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    public void run(ApplicationArguments args) throws java.io.IOException, InterruptedException {
        if (args.containsOption("db-export")) {
            String path = args.getOptionValues("db-export").get(0);
            Path target = Path.of(path);
            log.info("CLI 数据库导出: {}", target.toAbsolutePath());
            backupService.exportBackup(target);
            log.info("CLI 数据库导出完成: {}", target.toAbsolutePath());
            return;
        }

        if (args.containsOption("db-import")) {
            String path = args.getOptionValues("db-import").get(0);
            Path source = Path.of(path);
            log.info("CLI 数据库导入: {}", source.toAbsolutePath());
            backupService.importBackup(source);
            log.info("CLI 数据库导入完成: {}", source.toAbsolutePath());
        }
    }
}
