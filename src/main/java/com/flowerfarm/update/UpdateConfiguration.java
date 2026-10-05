package com.flowerfarm.update;

import com.flowerfarm.service.SyncHistoryService;
import org.flywaydb.core.Flyway;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Optional;

/**
 * Wires the updater. Creating these beans makes no network call and writes no
 * files; the desktop starts a check only after its window is open, and only
 * when an update source and a publisher key are built in.
 */
@Configuration(proxyBeanMethods = false)
public class UpdateConfiguration {

    @Bean
    UpdateSettings updateSettings(Environment environment) {
        return UpdateSettings.from(environment::getProperty);
    }

    /** Writes update attempts to Activity History; never lets a logging failure stop an update. */
    @Bean
    UpdateActivityLog updateActivityLog(ObjectProvider<SyncHistoryService> history) {
        return (operation, success, message) -> {
            SyncHistoryService service = history.getIfAvailable();
            if (service == null) {
                return;
            }
            try {
                service.record(UpdateActivityLog.SOURCE, operation, success, message, null, null);
            } catch (RuntimeException unavailable) {
                LoggerFactory.getLogger(UpdateConfiguration.class)
                        .warn("Could not record update activity: {}", unavailable.getMessage());
            }
        };
    }

    @Bean
    UpdateHealthCheck updateHealthCheck(DataSource dataSource, ObjectProvider<Flyway> flyway) {
        return new DatabaseHealthCheck(dataSource, Optional.ofNullable(flyway.getIfAvailable()));
    }

    @Bean
    UpdateCoordinator updateCoordinator(UpdateSettings settings,
                                        ObjectProvider<UpdateActivityLog> activity) {
        TrustedKeys keys = TrustedKeys.fromClasspath();
        SignedManifestVerifier verifier = new SignedManifestVerifier(keys);
        String agent = "FlowersForever/" + settings.currentVersion() + " (update check)";
        UpdateTransport transport = new HttpUpdateTransport(settings.connectTimeout(),
                settings.requestTimeout(), settings.allowLoopbackHttp(), agent);
        Optional<UpdatePlatform> platform = UpdatePlatform.forOperatingSystem(System.getProperty("os.name"));
        UpdateStateStore store = new UpdateStateStore(settings.updatesDirectory());
        PublisherVerifier publisher = settings.authenticodeSubject().isBlank()
                ? PublisherVerifier.none() : new AuthenticodePublisherVerifier(settings.authenticodeSubject());
        UpdateChecker checker = new UpdateChecker(settings, verifier, transport, platform, store::failedVersions);
        PackageDownloader downloader = new PackageDownloader(settings.updatesDirectory(), transport, verifier,
                publisher);
        return new UpdateCoordinator(settings, checker, downloader, new DataBackupService(Clock.systemDefaultZone()),
                store, PlatformInstallers.forThisProcess(platform, settings.launcherPath()), platform,
                activity.getIfAvailable(UpdateActivityLog::none), Clock.systemUTC());
    }

    @Bean
    UpdateFirstLaunchVerifier updateFirstLaunchVerifier(ObjectProvider<UpdateLaunchGuard> guard,
                                                        UpdateHealthCheck healthCheck,
                                                        ObjectProvider<UpdateActivityLog> activity,
                                                        DataSource dataSource) {
        return new UpdateFirstLaunchVerifier(guard, healthCheck, activity, dataSource);
    }

}
