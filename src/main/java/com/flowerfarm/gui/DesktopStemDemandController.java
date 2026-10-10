package com.flowerfarm.gui;

import com.flowerfarm.model.FloralRecipe;
import com.flowerfarm.safety.SafeFiles;
import com.flowerfarm.service.ProductionStudioService;
import com.flowerfarm.service.StemDemand;
import com.flowerfarm.service.StemDemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * File → Stem demand board: stems by crop and date for upcoming orders, the same window
 * as a buying list by event, copy/CSV export, and counting order lines as saved recipes.
 */
@Component
@Profile("!cli & !container & !mcp & !release-smoke")
public class DesktopStemDemandController {

    public record Host(JFrame frame, FarmDialogs dialogs, FarmFileChooser files,
                       FarmClipboard clipboard, Consumer<String> status) {
    }

    private final StemDemandService demand;
    private final ProductionStudioService studio;
    private Host host;

    @Autowired
    public DesktopStemDemandController(StemDemandService demand, ProductionStudioService studio) {
        this.demand = Objects.requireNonNull(demand, "demand");
        this.studio = Objects.requireNonNull(studio, "studio");
    }

    public void attach(Host frameHost) {
        this.host = Objects.requireNonNull(frameHost, "frameHost");
    }

    public void openBoard() {
        open(false);
    }

    /** File → Buying list by event: the same board, opened on the by-event list. */
    public void openBuyingList() {
        open(true);
    }

    private void open(boolean byEvent) {
        if (host == null) {
            return;
        }
        StemDemandPanel panel = new StemDemandPanel(host.status(), demand.today());
        Runnable refresh = () -> refresh(panel);
        panel.onRefresh(refresh);
        refresh.run();
        if (byEvent) {
            panel.showByEvent();
        }

        JButton copy = FarmUi.primaryButton("Copy list");
        copy.setName("stemDemand.copy");
        copy.addActionListener(event -> copy(panel));
        JButton csv = FarmUi.secondaryButton("Save a spreadsheet (CSV)…");
        csv.setName("stemDemand.saveCsv");
        csv.addActionListener(event -> saveCsv(panel));
        JButton copyEvents = FarmUi.secondaryButton("Copy by event");
        copyEvents.setName("stemDemand.copyEvents");
        copyEvents.addActionListener(event -> copyEvents(panel));
        JButton eventsCsv = FarmUi.secondaryButton("Save event buying lists (CSV)…");
        eventsCsv.setName("stemDemand.saveEventsCsv");
        eventsCsv.addActionListener(event -> saveEventsCsv(panel));
        JButton link = FarmUi.secondaryButton("Count line as recipe…");
        link.setName("stemDemand.linkRecipe");
        link.addActionListener(event -> linkRecipe(panel, refresh));
        JButton unlink = FarmUi.secondaryButton("Stop counting");
        unlink.setName("stemDemand.unlinkRecipe");
        unlink.addActionListener(event -> unlinkRecipe(panel, refresh));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, FarmUi.CONTROL_GAP, 0));
        actions.setOpaque(false);
        actions.add(copy);
        actions.add(csv);
        actions.add(copyEvents);
        actions.add(eventsCsv);
        actions.add(link);
        actions.add(unlink);

        JPanel wrap = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        wrap.setName("stemDemand.dialog");
        wrap.add(panel, BorderLayout.CENTER);
        wrap.add(actions, BorderLayout.SOUTH);

        Object[] options = {"Close"};
        host.dialogs().showOption(host.frame(), wrap, StemDemand.TITLE,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }

    void refresh(StemDemandPanel panel) {
        try {
            StemDemandPanel.Window window = panel.window();
            StemDemand.Board board = demand.build(window.from(), window.days(), window.includeDrafts());
            panel.showBoard(board, demand.recipeLinks(board.from(), board.to(), window.includeDrafts()));
        } catch (RuntimeException failed) {
            panel.announce(failed.getMessage());
        }
    }

    private void copy(StemDemandPanel panel) {
        panel.board().ifPresent(board -> {
            host.clipboard().copyText(StemDemand.shareText(board, demand.farmName()));
            panel.announce(StemDemand.statusCopied());
        });
    }

    private void copyEvents(StemDemandPanel panel) {
        panel.board().ifPresent(board -> {
            host.clipboard().copyText(StemDemand.eventsShareText(board, demand.farmName()));
            panel.announce(StemDemand.statusEventsCopied());
        });
    }

    private void saveCsv(StemDemandPanel panel) {
        StemDemand.Board board = panel.board().orElse(null);
        if (board == null) {
            return;
        }
        host.files().chooseSave(host.frame(), "Save the stem buying list as a spreadsheet (CSV)",
                        new File(StemDemand.suggestedCsvFileName(board)))
                .ifPresent(file -> writeCsv(panel, file.toPath(), StemDemand.csv(board), "stem demand CSV",
                        StemDemand::statusSaved));
    }

    private void saveEventsCsv(StemDemandPanel panel) {
        StemDemand.Board board = panel.board().orElse(null);
        if (board == null) {
            return;
        }
        host.files().chooseSave(host.frame(), "Save event buying lists as a spreadsheet (CSV)",
                        new File(StemDemand.suggestedEventsCsvFileName(board)))
                .ifPresent(file -> writeCsv(panel, file.toPath(), StemDemand.eventsCsv(board),
                        "buying list by event CSV", StemDemand::statusEventsSaved));
    }

    private void writeCsv(StemDemandPanel panel, Path path, String csv, String what,
                          java.util.function.UnaryOperator<String> saved) {
        try {
            SafeFiles.writeString(path, csv);
            Path name = path.getFileName();
            panel.announce(saved.apply(name == null ? path.toString() : name.toString()));
        } catch (IOException | RuntimeException failed) {
            host.dialogs().showMessage(host.frame(), FarmUi.actionableError("Saving the " + what, failed),
                    StemDemand.TITLE, JOptionPane.WARNING_MESSAGE);
            panel.announce("Could not save the " + what + ".");
        }
    }

    private void linkRecipe(StemDemandPanel panel, Runnable refresh) {
        StemDemand.NotCounted line = panel.selectedNotCounted().orElse(null);
        StemDemandService.LinkedLine linked = line == null ? panel.selectedLink().orElse(null) : null;
        if (line == null && linked == null) {
            panel.announce("Select an order line under \"Not counted\" or \"Counted as recipes\" first.");
            return;
        }
        List<FloralRecipe> recipes = studio.listRecipes();
        if (recipes.isEmpty()) {
            panel.announce("Save a recipe in Production Studio first, then count this line as that recipe.");
            return;
        }
        Long orderId = line != null ? line.orderId() : linked.orderId();
        Long lineId = line != null ? line.lineId() : linked.lineId();
        String product = line != null ? line.product() : linked.product();
        JComboBox<RecipeChoice> combo = new JComboBox<>(
                recipes.stream().map(RecipeChoice::new).toArray(RecipeChoice[]::new));
        combo.setName("stemDemand.recipe");
        combo.getAccessibleContext().setAccessibleName("Saved recipe");
        JPanel form = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        form.setName("stemDemand.recipeForm");
        form.add(new JLabel("Count " + product + " as one design of this recipe per unit ordered:"),
                BorderLayout.NORTH);
        form.add(combo, BorderLayout.CENTER);
        form.putClientProperty("stemDemand.recipe", combo);
        Object[] options = {"Count as recipe", "Cancel"};
        int choice = host.dialogs().showOption(host.frame(), form, "Count order line as a recipe",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        RecipeChoice picked = (RecipeChoice) combo.getSelectedItem();
        if (choice != 0 || picked == null) {
            return;
        }
        apply(panel, refresh, orderId, lineId, picked.recipe().getId());
    }

    private void unlinkRecipe(StemDemandPanel panel, Runnable refresh) {
        StemDemandService.LinkedLine linked = panel.selectedLink().orElse(null);
        if (linked == null) {
            panel.announce("Select a line under \"Counted as recipes\" to stop counting it.");
            return;
        }
        apply(panel, refresh, linked.orderId(), linked.lineId(), null);
    }

    private void apply(StemDemandPanel panel, Runnable refresh, Long orderId, Long lineId, Long recipeId) {
        try {
            StemDemandService.RecipeLink result = demand.linkRecipe(orderId, lineId, recipeId);
            refresh.run();
            panel.announce(result.recipeName() == null
                    ? "Stopped counting " + result.product() + " as a recipe."
                    : "Counting " + result.product() + " as " + result.recipeName() + ".");
        } catch (RuntimeException failed) {
            host.dialogs().showMessage(host.frame(), failed.getMessage(), StemDemand.TITLE,
                    JOptionPane.WARNING_MESSAGE);
            panel.announce(failed.getMessage());
        }
    }

    private record RecipeChoice(FloralRecipe recipe) {
        @Override
        public String toString() {
            return recipe.getName();
        }
    }
}
