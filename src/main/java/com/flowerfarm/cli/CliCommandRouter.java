package com.flowerfarm.cli;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * Ordered CLI command catalog and dispatcher.
 */
public final class CliCommandRouter {

    private final PrintStream output;
    private final Map<String, Command> commands = new LinkedHashMap<>();

    public CliCommandRouter(PrintStream output) {
        this.output = output;
    }

    public CliCommandRouter register(String option, String label, Consumer<Scanner> handler) {
        if (commands.containsKey(option)) {
            throw new IllegalArgumentException("Duplicate CLI option: " + option);
        }
        commands.put(option, new Command(label, handler, false));
        return this;
    }

    public CliCommandRouter exit(String option, String label, Consumer<Scanner> handler) {
        if (commands.containsKey(option)) {
            throw new IllegalArgumentException("Duplicate CLI option: " + option);
        }
        commands.put(option, new Command(label, handler, true));
        return this;
    }

    public void printMenu() {
        output.println("─────────────────────────────────────────");
        commands.forEach((option, command) ->
                output.printf("%2s) %s%n", option, command.label()));
        output.println("─────────────────────────────────────────");
        output.print("Choice: ");
    }

    /**
     * Dispatches one option and returns whether the interactive loop should continue.
     */
    public boolean dispatch(String option, Scanner scanner) {
        Command command = commands.get(option == null ? "" : option.trim());
        if (command == null) {
            output.println("Unknown option. Try again.");
            return true;
        }
        command.handler().accept(scanner);
        return !command.exit();
    }

    public int size() {
        return commands.size();
    }

    private record Command(String label, Consumer<Scanner> handler, boolean exit) {
    }
}
