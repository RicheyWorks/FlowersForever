package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessExecution;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessPluginCapability;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPluginKind;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.HarnessValueType;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Default provider-neutral adapter for the installed FlowersForever desktop. */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class SwingHarnessPlugin implements HarnessPlugin {

    public static final String ID = "swing-desktop";

    private static final HarnessPluginDescriptor DESCRIPTOR = new HarnessPluginDescriptor(
            ID,
            "FlowersForever Swing desktop",
            "Observe and operate the installed desktop through accessible selectors.",
            "1.9",
            HarnessPluginKind.APPLICATION,
            Set.of(HarnessPluginCapability.OBSERVATION,
                    HarnessPluginCapability.COMMANDS,
                    HarnessPluginCapability.VISUAL_PERCEPTION),
            List.of(
                    new HarnessActionSpec("navigate", "Open a named farm workflow tab.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("tab", HarnessValueType.STRING,
                                    "Visible primary workflow tab title."))),
                    new HarnessActionSpec("replace-text",
                            "Replace one editable, non-password field draft.",
                            HarnessRisk.DRAFT, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible field selector."),
                            argument("value", HarnessValueType.STRING,
                                    "Complete replacement text, at most 8,000 characters."))),
                    new HarnessActionSpec("choose-option",
                            "Choose a visible option, or enter custom text when the selector is editable.",
                            HarnessRisk.DRAFT, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible combo-box selector."),
                            argument("value", HarnessValueType.STRING,
                                    "Visible option or custom editable text, at most 8,000 characters."))),
                    new HarnessActionSpec("select-tab",
                            "Select a nested tab by its visible title.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible tab-set selector."),
                            argument("tab", HarnessValueType.STRING,
                                    "Exact visible nested-tab title."))),
                    new HarnessActionSpec("select-row", "Select one visible table row.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("table", HarnessValueType.STRING,
                                    "Unique accessible name or production table name."),
                            argument("row", HarnessValueType.INTEGER,
                                    "Zero-based row index in the current table view."))),
                    new HarnessActionSpec("select-rows",
                            "Select several visible table rows.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("table", HarnessValueType.STRING,
                                    "Unique accessible name or production table name."),
                            arrayArgument("rows", HarnessValueType.INTEGER,
                                    "Non-empty array of zero-based current-view row indexes."))),
                    new HarnessActionSpec("sort-table",
                            "Sort a table by a visible column title.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("table", HarnessValueType.STRING,
                                    "Unique accessible name or production table name."),
                            argument("column", HarnessValueType.STRING,
                                    "Exact visible column title."),
                            argument("direction", HarnessValueType.STRING,
                                    "ASCENDING or DESCENDING."))),
                    new HarnessActionSpec("select-list-row",
                            "Select one visible list row.", HarnessRisk.NAVIGATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible list selector."),
                            argument("row", HarnessValueType.INTEGER,
                                    "Zero-based row index."))),
                    new HarnessActionSpec("set-spinner",
                            "Set an enabled spinner through its displayed editor value.",
                            HarnessRisk.MUTATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible spinner selector."),
                            argument("value", HarnessValueType.STRING,
                                    "Displayed value parsed by the production spinner editor."))),
                    new HarnessActionSpec("set-slider",
                            "Set an enabled slider within its published bounds.",
                            HarnessRisk.MUTATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible slider selector."),
                            argument("value", HarnessValueType.INTEGER,
                                    "Integer value within the slider range."))),
                    new HarnessActionSpec("activate-button",
                            "Activate a button; classified destructive because selectors are generic.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible name or visible button text."))),
                    new HarnessActionSpec("submit-field",
                            "Submit a single-line field through its production Enter listener.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Unique accessible non-password field selector."))),
                    new HarnessActionSpec("double-click-row",
                            "Double-click one visible table row through its production mouse listener.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            argument("table", HarnessValueType.STRING,
                                    "Unique accessible name or production table name."),
                            argument("row", HarnessValueType.INTEGER,
                                    "Zero-based row index in the current table view."))),
                    new HarnessActionSpec("activate-menu",
                            "Activate a terminal desktop menu command by its exact visible path.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            arrayArgument("path", HarnessValueType.STRING,
                                    "Menu labels from the top-level menu through the command."))),
                    new HarnessActionSpec("invoke-shortcut",
                            "Invoke one visible installed application shortcut.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            argument("shortcut", HarnessValueType.STRING,
                                    "Swing keystroke such as F5 or ctrl ENTER."))),
                    new HarnessActionSpec("read-control",
                            "Read a bounded page of one visible non-password control.",
                            HarnessRisk.SENSITIVE_READ, List.of(
                            optional("selector", HarnessValueType.STRING,
                                    "Unique accessible control selector; provide this or a menu path."),
                            new HarnessArgumentSpec("path", HarnessValueType.ARRAY,
                                    "Exact terminal menu path; an alternative to selector.", false, HarnessValueType.STRING),
                            optional("offset", HarnessValueType.INTEGER,
                                    "Zero-based row, item, or UTF-16 text offset; defaults to zero."),
                            optional("limit", HarnessValueType.INTEGER,
                                    "Page size: 1-200 collection items or 1-8000 text characters."),
                            optional("column-offset", HarnessValueType.INTEGER,
                                    "First current-view table column, defaults to zero."),
                            optional("column-limit", HarnessValueType.INTEGER,
                                    "Table column page size, 1-50; at most 2000 cells per response."))),
                    new HarnessActionSpec("capture-screen",
                            "Capture a bounded PNG of the attached application window.",
                            HarnessRisk.SENSITIVE_READ, List.of()),
                    new HarnessActionSpec("navigate-route",
                            "Open an exact primary and nested workflow route.",
                            HarnessRisk.NAVIGATE, List.of(
                            arrayArgument("path", HarnessValueType.STRING,
                                    "Exact route path published by snapshot metadata."))),
                    new HarnessActionSpec("action-status",
                            "Read completion of an accepted desktop action without exposing field values.",
                            HarnessRisk.READ, List.of(
                            argument("request-id", HarnessValueType.STRING,
                                    "Original actionRequestId returned by an accepted desktop action."))),
                    new HarnessActionSpec("choose-file",
                            "Select an absolute path and approve the active application file dialog.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Accessible file-chooser selector from the active dialog."),
                            argument("path", HarnessValueType.STRING,
                                    "Absolute local file path to select."))),
                    new HarnessActionSpec("cancel-file-dialog",
                            "Cancel the active application file dialog without approving a path.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Accessible file-chooser selector from the active dialog."))),
                    new HarnessActionSpec("scroll", "Move one application scroll area to an exact position.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Scroll-area selector or a uniquely named control inside it."),
                            argument("axis", HarnessValueType.STRING, "HORIZONTAL or VERTICAL."),
                            argument("value", HarnessValueType.INTEGER,
                                    "Position within the bounds returned by read-layout."))),
                    new HarnessActionSpec("set-divider", "Position one workspace divider.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Split-pane selector or a uniquely named control inside it."),
                            argument("value", HarnessValueType.INTEGER,
                                    "Divider position within the bounds returned by read-layout."))),
                    new HarnessActionSpec("set-toggle", "Set a checkbox or toggle through its normal click listener.",
                            HarnessRisk.DESTRUCTIVE, List.of(
                            optional("selector", HarnessValueType.STRING, "Unique toggle selector; provide this or a menu path."),
                            new HarnessArgumentSpec("path", HarnessValueType.ARRAY,
                                    "Exact toggle menu path; an alternative to selector.", false, HarnessValueType.STRING),
                            argument("selected", HarnessValueType.BOOLEAN, "Desired checked or selected state."))),
                    new HarnessActionSpec("choose-option-index", "Choose an exact option when labels are repeated.",
                            HarnessRisk.DRAFT, List.of(
                            argument("selector", HarnessValueType.STRING, "Unique combo-box selector."),
                            argument("index", HarnessValueType.INTEGER,
                                    "Zero-based current option index returned by read-control."))),
                    new HarnessActionSpec("clear-selection", "Clear all selected rows in a table or list.",
                            HarnessRisk.NAVIGATE, List.of(
                            argument("selector", HarnessValueType.STRING, "Unique table or list selector."))),
                    new HarnessActionSpec("read-screen", "Read a page of visible application text and status messages.",
                            HarnessRisk.SENSITIVE_READ, List.of(
                            optional("offset", HarnessValueType.INTEGER,
                                    "Zero-based UTF-16 offset in visible screen text; defaults to zero."),
                            optional("limit", HarnessValueType.INTEGER, "Text page size, 1-8000 characters."))),
                    new HarnessActionSpec("read-layout", "Read scroll or divider positions and usable bounds.",
                            HarnessRisk.READ, List.of(
                            argument("selector", HarnessValueType.STRING,
                                    "Named layout pane or a uniquely named control inside it."),
                            argument("kind", HarnessValueType.STRING, "scroll or split."))),
                    new HarnessActionSpec("read-item", "Read a complete cell or option label in bounded text pages.",
                            HarnessRisk.SENSITIVE_READ, List.of(
                            argument("selector", HarnessValueType.STRING, "Unique table, list, or combo selector."),
                            argument("index", HarnessValueType.INTEGER, "Zero-based current-view row or option index."),
                            optional("column", HarnessValueType.INTEGER, "Required zero-based view column for tables."),
                            optional("offset", HarnessValueType.INTEGER, "UTF-16 text offset; defaults to zero."),
                            optional("limit", HarnessValueType.INTEGER, "Text page size, 1-8000 characters."))),
                    new HarnessActionSpec("edit-text", "Insert, append, or replace a bounded segment of an editable draft.",
                            HarnessRisk.DRAFT, List.of(
                            argument("selector", HarnessValueType.STRING, "Unique non-password field selector."),
                            argument("offset", HarnessValueType.INTEGER, "Zero-based UTF-16 insertion/replacement offset."),
                            argument("length", HarnessValueType.INTEGER, "Number of existing characters to replace; zero inserts."),
                            argument("value", HarnessValueType.STRING, "Replacement text, at most 8000 characters."))))
                    .stream().map(SwingHarnessPlugin::withObservationContext).toList());

    private final SwingHarnessBridge bridge;

    public SwingHarnessPlugin(SwingHarnessBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public HarnessPluginDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public HarnessSnapshot observe() {
        return bridge.snapshot(ID);
    }

    @Override
    public HarnessExecution execute(HarnessCommand command) {
        if (lifecycleAction(command.action())) {
            return bridge.dispatch(command, target -> executeNow(command, target));
        }
        return executeNow(command, bridge);
    }

    @Override
    public java.util.Optional<HarnessExecution> replay(HarnessCommand command) {
        return lifecycleAction(command.action()) ? bridge.replay(command) : java.util.Optional.empty();
    }

    private static boolean lifecycleAction(String action) {
        return switch (action) {
            case "activate-button", "submit-field", "double-click-row", "activate-menu",
                    "invoke-shortcut", "navigate", "navigate-route", "replace-text",
                    "choose-option", "select-tab", "select-row", "select-rows", "sort-table",
                    "select-list-row", "set-spinner", "set-slider", "choose-file",
                    "cancel-file-dialog", "scroll", "set-divider", "set-toggle",
                    "choose-option-index", "clear-selection", "edit-text" -> true;
            default -> false;
        };
    }

    private static HarnessActionSpec withObservationContext(HarnessActionSpec action) {
        if (!lifecycleAction(action.name()) && !Set.of("read-control", "read-screen", "read-layout", "read-item")
                .contains(action.name())) {
            return action;
        }
        HarnessArgumentSpec context = new HarnessArgumentSpec(
                "expected-context", HarnessValueType.STRING,
                "Optional contextToken from a ready observation or structured read; reject changed UI before execution.",
                false);
        return new HarnessActionSpec(action.name(), action.description(), action.risk(),
                action.effect(), action.idempotent(), action.requiresFreshness(),
                Stream.concat(action.arguments().stream(), Stream.of(context)).toList());
    }

    private HarnessExecution executeNow(HarnessCommand command, SwingHarnessBridge bridge) {
        return switch (command.action()) {
            case "edit-text" -> {
                Object value = command.arguments().get("value");
                if (!(value instanceof String text)) {
                    throw new IllegalArgumentException("Text value must be a string");
                }
                bridge.editText(command.text("selector"), command.integer("offset"), command.integer("length"), text);
                yield HarnessExecution.success("Edited application draft segment");
            }
            case "read-item" -> HarnessExecution.success("Read application collection item", bridge.readItem(command));
            case "scroll" -> {
                bridge.scroll(command.text("selector"), command.text("axis"), command.integer("value"));
                yield HarnessExecution.success("Positioned application scroll area");
            }
            case "set-divider" -> {
                bridge.setDivider(command.text("selector"), command.integer("value"));
                yield HarnessExecution.success("Positioned application workspace divider");
            }
            case "set-toggle" -> {
                Object selected = command.arguments().get("selected");
                if (!(selected instanceof Boolean state)) {
                    throw new IllegalArgumentException("selected must be a boolean");
                }
                bridge.setToggle(command, state);
                yield HarnessExecution.success("Set application toggle state");
            }
            case "choose-option-index" -> {
                bridge.chooseOptionIndex(command.text("selector"), command.integer("index"));
                yield HarnessExecution.success("Selected application option by index");
            }
            case "clear-selection" -> {
                bridge.clearSelection(command.text("selector"));
                yield HarnessExecution.success("Cleared application row selection");
            }
            case "read-screen" -> HarnessExecution.success("Read visible application text", bridge.readScreen(command));
            case "read-layout" -> HarnessExecution.success("Read application layout", bridge.readLayout(command));
            case "action-status" -> bridge.actionStatus(command.text("request-id"));
            case "choose-file" -> {
                bridge.chooseFile(command.text("selector"), command.text("path"));
                yield HarnessExecution.success("Approved the selected application file");
            }
            case "cancel-file-dialog" -> {
                bridge.cancelFileDialog(command.text("selector"));
                yield HarnessExecution.success("Cancelled the application file dialog");
            }
            case "navigate" -> {
                String tab = command.text("tab");
                bridge.navigate(tab);
                yield HarnessExecution.success("Opened desktop tab '" + tab + "'",
                        Map.of("tab", tab));
            }
            case "replace-text" -> {
                String selector = command.text("selector");
                bridge.replaceText(selector, String.valueOf(command.arguments().get("value")));
                yield HarnessExecution.success("Replaced draft field '" + selector + "'");
            }
            case "choose-option" -> {
                String selector = command.text("selector");
                String value = (String) command.arguments().get("value");
                bridge.chooseOption(selector, value);
                yield HarnessExecution.success("Selected '" + value + "' in '" + selector + "'");
            }
            case "select-tab" -> {
                String selector = command.text("selector");
                String tab = command.text("tab");
                bridge.selectTab(selector, tab);
                yield HarnessExecution.success("Selected tab '" + tab + "' in '"
                        + selector + "'");
            }
            case "select-row" -> {
                String table = command.text("table");
                int row = command.integer("row");
                bridge.selectRow(table, row);
                yield HarnessExecution.success("Selected row " + row + " in '" + table + "'");
            }
            case "select-rows" -> {
                String table = command.text("table");
                List<Integer> rows = command.integers("rows");
                bridge.selectRows(table, rows);
                yield HarnessExecution.success("Selected " + rows.size() + " row(s) in '"
                        + table + "'", Map.of("rows", rows));
            }
            case "sort-table" -> {
                String table = command.text("table");
                String column = command.text("column");
                String direction = command.text("direction");
                bridge.sortTable(table, column, direction);
                yield HarnessExecution.success("Sorted '" + table + "' by '" + column
                        + "' " + direction.toUpperCase(java.util.Locale.ROOT));
            }
            case "select-list-row" -> {
                String selector = command.text("selector");
                int row = command.integer("row");
                bridge.selectListRow(selector, row);
                yield HarnessExecution.success("Selected row " + row + " in list '"
                        + selector + "'");
            }
            case "set-spinner" -> {
                String selector = command.text("selector");
                String value = command.text("value");
                bridge.setSpinner(selector, value);
                yield HarnessExecution.success("Set spinner '" + selector + "'");
            }
            case "set-slider" -> {
                String selector = command.text("selector");
                int value = command.integer("value");
                bridge.setSlider(selector, value);
                yield HarnessExecution.success("Set slider '" + selector + "' to " + value);
            }
            case "activate-button" -> {
                String selector = command.text("selector");
                bridge.activateButton(selector);
                yield HarnessExecution.success("Activated button '" + selector + "'");
            }
            case "submit-field" -> {
                String selector = command.text("selector");
                bridge.submitField(selector);
                yield HarnessExecution.success("Submitted field '" + selector + "'");
            }
            case "double-click-row" -> {
                String table = command.text("table");
                int row = command.integer("row");
                bridge.doubleClickRow(table, row);
                yield HarnessExecution.success("Double-clicked row " + row + " in '"
                        + table + "'");
            }
            case "activate-menu" -> {
                List<String> path = command.texts("path");
                bridge.activateMenu(path);
                yield HarnessExecution.success("Activated menu command '"
                        + String.join(" > ", path) + "'");
            }
            case "invoke-shortcut" -> {
                String shortcut = command.text("shortcut");
                bridge.invokeShortcut(shortcut);
                yield HarnessExecution.success("Invoked shortcut '" + shortcut + "'");
            }
            case "read-control" -> {
                yield HarnessExecution.success("Read application control",
                        bridge.readControl(command));
            }
            case "capture-screen" -> {
                SwingHarnessBridge.ScreenCapture capture = bridge.captureScreen();
                yield HarnessExecution.success("Captured attached application window",
                        Map.of("width", capture.width(), "height", capture.height(),
                                "mediaType", capture.mediaType(),
                                "encoding", capture.encoding(),
                                "data", capture.data(), "sha256", capture.sha256()));
            }
            case "navigate-route" -> {
                List<String> path = command.texts("path");
                bridge.navigateRoute(path);
                yield HarnessExecution.success("Opened desktop route '"
                        + String.join(" > ", path) + "'", Map.of("path", path));
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported Swing harness action '" + command.action() + "'");
        };
    }

    private static HarnessArgumentSpec argument(
            String name, HarnessValueType type, String description) {
        return new HarnessArgumentSpec(name, type, description, true);
    }

    private static HarnessArgumentSpec optional(
            String name, HarnessValueType type, String description) {
        return new HarnessArgumentSpec(name, type, description, false);
    }

    private static HarnessArgumentSpec arrayArgument(
            String name, HarnessValueType itemType, String description) {
        return new HarnessArgumentSpec(
                name, HarnessValueType.ARRAY, description, true, itemType);
    }
}
