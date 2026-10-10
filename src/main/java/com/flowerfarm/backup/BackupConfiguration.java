package com.flowerfarm.backup;

import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.update.DataBackupService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Wires in-app backups. Creating these beans touches no files and starts no
 * threads; the desktop window starts automatic backups once it is open.
 */
@Configuration(proxyBeanMethods = false)
public class BackupConfiguration {

    @Bean
    BackupSettings backupSettings(Environment environment) {
        return BackupSettings.from(environment::getProperty);
    }

    @Bean
    BackupPreferencesStore backupPreferencesStore(BackupSettings settings) {
        return new BackupPreferencesStore(settings.stateDirectory());
    }

    @Bean
    RestoreRecords restoreRecords(BackupSettings settings) {
        return new RestoreRecords(settings.stateDirectory(), settings.dataDirectory());
    }

    @Bean
    LiveBackupService liveBackupService(BackupSettings settings, DataSource dataSource) {
        return new LiveBackupService(settings, DatabaseSnapshot.h2(dataSource),
                new DataBackupService(Clock.systemDefaultZone()));
    }

    @Bean
    RestoreService restoreService(BackupSettings settings, Environment environment) {
        return new RestoreService(settings, new DataBackupService(Clock.systemDefaultZone()),
                environment.getProperty("spring.datasource.username", "sa"),
                environment.getProperty("spring.datasource.password", ""),
                environment.getProperty("spring.flyway.locations", "classpath:db/migration/h2").split(","));
    }

    /** Writes backups and restores to Activity History; a logging failure never stops a backup. */
    @Bean
    BackupActivityLog backupActivityLog(ObjectProvider<SyncHistoryService> history) {
        return (operation, success, message) -> {
            SyncHistoryService service = history.getIfAvailable();
            if (service == null) {
                return;
            }
            try {
                service.record(BackupActivityLog.SOURCE, operation, success, message, null, null);
            } catch (RuntimeException unavailable) {
                LoggerFactory.getLogger(BackupConfiguration.class)
                        .warn("Could not record backup activity: {}", unavailable.getMessage());
            }
        };
    }

    @Bean
    AutomaticBackupScheduler automaticBackupScheduler(LiveBackupService backups, BackupPreferencesStore preferences,
                                                      BackupActivityLog activity) {
        return new AutomaticBackupScheduler(backups, preferences, activity, Clock.systemUTC());
    }
}
