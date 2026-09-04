package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;

import javax.swing.SwingUtilities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Bounded, process-local receipts for callbacks which can enter a Swing modal loop. */
final class SwingActionJournal {
    private static final int MAX_RECEIPTS = 256;
    private static final int MAX_PENDING = 16;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    HarnessExecution dispatch(HarnessCommand command, Supplier<HarnessExecution> action) {
        Entry entry;
        synchronized (entries) {
            Entry existing = entries.get(command.requestId());
            if (existing != null) {
                requireSameCommand(existing, command);
                return acceptedReceipt(existing);
            }
            long pending = entries.values().stream().filter(item -> !item.result.isDone()).count();
            if (pending >= MAX_PENDING) {
                throw new IllegalStateException("Too many pending desktop actions; resolve them first");
            }
            while (entries.size() >= MAX_RECEIPTS) {
                String oldest = entries.entrySet().stream()
                        .filter(item -> item.getValue().result.isDone())
                        .map(Map.Entry::getKey).findFirst().orElseThrow();
                entries.remove(oldest);
            }
            entry = new Entry(command);
            entries.put(command.requestId(), entry);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                entry.result.complete(action.get());
            } catch (Throwable failure) {
                // Messages may contain private field values. Status exposes only the failure class.
                entry.result.complete(new HarnessExecution(false, "Desktop action failed",
                        Map.of("failureType", failure.getClass().getSimpleName())));
            }
        });
        if (!SwingUtilities.isEventDispatchThread()) {
            try {
                entry.result.get(150, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                // Accepted work remains tracked; a modal loop is expected to outlive this grace period.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                // Dispatch already happened. Return its receipt instead of implying a safe retry.
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Could not inspect desktop action", exception);
            }
        }
        entry.acceptedReceipt = receipt(entry);
        return entry.acceptedReceipt;
    }

    Optional<HarnessExecution> replay(HarnessCommand command) {
        synchronized (entries) {
            Entry existing = entries.get(command.requestId());
            if (existing == null) {
                return Optional.empty();
            }
            requireSameCommand(existing, command);
            return Optional.of(acceptedReceipt(existing));
        }
    }

    HarnessExecution status(String requestId) {
        synchronized (entries) {
            Entry entry = entries.get(requestId);
            if (entry == null) {
                throw new IllegalArgumentException("No retained desktop action '" + requestId + "'");
            }
            HarnessExecution state = receipt(entry);
            Map<String, Object> summary = new LinkedHashMap<>();
            for (String key : List.of("actionRequestId", "status", "accepted", "completed", "failureType")) {
                if (state.output().containsKey(key)) {
                    summary.put(key, state.output().get(key));
                }
            }
            String status = String.valueOf(summary.get("status"));
            String message = switch (status) {
                case "PENDING" -> "Accepted; action still pending, poll action-status";
                case "COMPLETED" -> "Desktop action completed";
                default -> "Desktop action failed";
            };
            return new HarnessExecution(state.success(), message, summary);
        }
    }

    List<String> pendingIds() {
        synchronized (entries) {
            return entries.entrySet().stream().filter(entry -> !entry.getValue().result.isDone())
                    .map(Map.Entry::getKey).toList();
        }
    }

    private static void requireSameCommand(Entry entry, HarnessCommand command) {
        if (!entry.command.equals(command)) {
            throw new IllegalArgumentException("requestId '" + command.requestId()
                    + "' was already used for a different desktop command");
        }
    }

    private static HarnessExecution receipt(Entry entry) {
        HarnessExecution result = entry.result.isDone() ? entry.result.join() : null;
        Map<String, Object> output = new LinkedHashMap<>(result == null ? Map.of() : result.output());
        output.put("actionRequestId", entry.command.requestId());
        output.put("accepted", true);
        output.put("completed", result != null);
        output.put("status", result == null ? "PENDING" : result.success() ? "COMPLETED" : "FAILED");
        return new HarnessExecution(result == null || result.success(), result == null
                ? "Accepted; action still pending, poll action-status" : result.message(), output);
    }

    private static HarnessExecution acceptedReceipt(Entry entry) {
        return entry.acceptedReceipt == null ? receipt(entry) : entry.acceptedReceipt;
    }

    private static final class Entry {
        private final HarnessCommand command;
        private final CompletableFuture<HarnessExecution> result = new CompletableFuture<>();
        private volatile HarnessExecution acceptedReceipt;

        private Entry(HarnessCommand command) {
            this.command = command;
        }
    }
}
