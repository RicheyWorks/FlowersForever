package com.flowerfarm.update;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Wraps the desktop start. Before Spring: honour a pending rollback or block.
 * During Spring: a startup failure on the first launch after an update rolls
 * back. Returns the started context, or empty when the process should exit.
 */
public final class UpdateStartup {

    public record Result<T>(Optional<T> started, int exitCode) {
    }

    private UpdateStartup() {
    }

    /** Builds the guard from JVM properties; AppDataBootstrap must already have run. */
    public static UpdateLaunchGuard guardForThisProcess() {
        UpdateSettings settings = UpdateSettings.fromSystemProperties();
        Optional<UpdatePlatform> platform = UpdatePlatform.forOperatingSystem(System.getProperty("os.name"));
        return new UpdateLaunchGuard(settings, new UpdateStateStore(settings.updatesDirectory()),
                new DataBackupService(Clock.systemDefaultZone()),
                PlatformInstallers.forThisProcess(platform, settings.launcherPath()), platform,
                Clock.systemUTC());
    }

    public static <T> Result<T> start(UpdateLaunchGuard guard, Callable<T> startSpring,
                                      Runnable closeDatabase, Consumer<String> tell) throws Exception {
        UpdateState.Decision decision = guard.beforeStart();
        if (decision == UpdateState.Decision.BLOCKED) {
            tell.accept(guard.peekNotice().orElse(UpdateMessages.rollbackNeedsHelp(
                    AppVersion.parse("0.0.0"), null, "update recovery is incomplete")));
            return new Result<>(Optional.empty(), 2);
        }
        if (decision == UpdateState.Decision.ROLLBACK_NOW) {
            return rollBack(guard, "the first start after the update did not finish", tell);
        }
        try {
            return new Result<>(Optional.of(startSpring.call()), 0);
        } catch (Exception | Error startupFailure) {
            if (guard.decision() != UpdateState.Decision.VERIFY_FIRST_LAUNCH) {
                throw startupFailure;
            }
            closeDatabase.run();
            return rollBack(guard, reason(startupFailure), tell);
        }
    }

    private static <T> Result<T> rollBack(UpdateLaunchGuard guard, String reason, Consumer<String> tell) {
        UpdateLaunchGuard.RollbackOutcome outcome = guard.rollBack(reason);
        tell.accept(outcome.message());
        return new Result<>(Optional.empty(), outcome.installerStarted() ? 0 : 2);
    }

    static String reason(Throwable failure) {
        Throwable cause = failure;
        for (int depth = 0; depth < 10; depth++) {
            if (cause instanceof UpdateHealthCheckFailedException) {
                return cause.getMessage();
            }
            if (cause.getCause() == null || cause.getCause() == cause) {
                break;
            }
            cause = cause.getCause();
        }
        String type = cause.getClass().getSimpleName();
        String message = cause.getMessage() == null ? "" : ": " + cause.getMessage();
        String text = "startup failed (" + type + message + ")";
        return text.length() > 300 ? text.substring(0, 300) + "…)" : text;
    }
}
