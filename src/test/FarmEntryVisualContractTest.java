package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.gui.tabs.WorkspaceHomeTab;
import com.flowerfarm.model.FarmWorkspace;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compact entry points must keep the selected farm mix and its actions readable. */
class FarmEntryVisualContractTest {
    private static final Set<FarmWorkspace> MIX =
            new LinkedHashSet<>(Arrays.asList(FarmWorkspace.values()));

    @ParameterizedTest
    @CsvSource({"850, false", "1100, false", "850, true", "1100, true"})
    void combinedLauncherKeepsItsMixAndCardActionsReadable(int width, boolean dark) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            installTheme(dark);
            FarmUi.installThemeDefaults();
            FarmTypeLauncher launcher = new FarmTypeLauncher(FarmWorkspace.ROSE_AND_EVENTS,
                    MIX, true, selected -> { }, () -> { }, () -> { });
            JPanel window = new JPanel(new BorderLayout());
            window.add(launcher);
            window.setSize(width, 500);
            settleLayout(window);
            JTextField search = named(launcher, "farmLauncher.search", JTextField.class);
            search.setText("rose");
            search.setText("");
            settleLayout(window);
            savePreview(window, "launcher-mix-" + width + (dark ? "-dark" : "-light"));

            SoftAssertions softly = new SoftAssertions();
            JLabel mix = named(launcher, "farmLauncher.mixSummary", JLabel.class);
            assertReadableLabel(softly, mix, "Current mix");
            JLabel title = named(launcher, "farm.pageTitle", JLabel.class);
            assertReadableLabel(softly, title, "Launcher title");
            JLabel subtitle = named(launcher, "farm.pageSubtitle", JLabel.class);
            assertReadableLabel(softly, subtitle, "Launcher subtitle");
            for (FarmWorkspace workspace : FarmWorkspace.values()) {
                JPanel card = named(launcher, "farmLauncher.card." + workspace.name(),
                        JPanel.class);
                JButton enter = named(card, "farmLauncher.enter." + workspace.name(),
                        JButton.class);
                Rectangle buttonBounds = relativeBounds(enter, card);
                softly.assertThat(new Rectangle(0, 0, card.getWidth(), card.getHeight())
                                .contains(buttonBounds))
                        .as(workspace + " action remains inside its card").isTrue();
                softly.assertThat(enter.getWidth())
                        .as(workspace + " complete action label")
                        .isGreaterThanOrEqualTo(enter.getPreferredSize().width);
                for (JLabel label : descendants(enter.getParent(), JLabel.class)) {
                    assertReadableLabel(softly, label, workspace + " footer status");
                    softly.assertThat(relativeBounds(label, card).intersects(buttonBounds))
                            .as(workspace + " footer status does not overlap its action")
                            .isFalse();
                }
            }
            softly.assertAll();
        });
    }

    @ParameterizedTest
    @CsvSource({"850, false", "1100, false", "850, true", "1100, true"})
    void combinedWorkspaceKeepsGuidanceAndEveryWorkflowWithinItsViewport(int width, boolean dark)
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            installTheme(dark);
            FarmUi.installThemeDefaults();
            List<String> opened = new ArrayList<>();
            TabHost host = new TabHost() {
                @Override public void refreshAll() { }
                @Override public void selectTab(String title) { opened.add(title); }
                @Override public void setStatus(String message) { }
                @Override public void runTrendAnalysis() { }
            };
            List<String> workflows = List.of("Dashboard", "Market Day", "Season Planner",
                    "Cultivation", "Field plans", "Orchard blocks", "Berry blocks",
                    "Egg collections", "Vegetable plantings", "Harvest Log", "Inventory",
                    "Knowledge Log", "Customers & Orders", "Event Services",
                    "Production Studio", "Reports", "Activity History");
            WorkspaceHomeTab home = new WorkspaceHomeTab(FarmWorkspace.ROSE_AND_EVENTS,
                    workflows, host, "Market vegetables, breeding stock, and seasonal flowers",
                    new LinkedHashSet<>(Arrays.asList(FarmWorkspace.values())));
            JComponent page = home.getUIComponent();
            JPanel window = new JPanel(new BorderLayout());
            window.add(page);
            window.setSize(width, 500);
            settleLayout(window);
            savePreview(window, "workspace-mix-" + width + (dark ? "-dark" : "-light"));

            SoftAssertions softly = new SoftAssertions();
            JLabel subtitle = named(page, "farm.pageSubtitle", JLabel.class);
            assertReadableLabel(softly, subtitle, "Every selected type in workspace heading");
            JScrollPane scroll = named(page, "workspace.scroll", JScrollPane.class);
            softly.assertThat(scroll.getViewport().getView().getWidth())
                    .as("Workspace content fits the viewport when horizontal scrolling is hidden")
                    .isLessThanOrEqualTo(scroll.getViewport().getWidth());
            JTextArea note = named(page, "workspace.scopeNote", JTextArea.class);
            softly.assertThat(note.getHeight()).as("Complete workspace scope note")
                    .isGreaterThanOrEqualTo(note.getPreferredSize().height);
            JPanel destinations = named(page, "workspace.destinations", JPanel.class);
            for (JButton button : descendants(destinations, JButton.class)) {
                Rectangle bounds = relativeBounds(button, scroll.getViewport().getView());
                softly.assertThat(bounds.x).as(button.getText() + " left edge").isNotNegative();
                softly.assertThat(bounds.x + bounds.width)
                        .as(button.getText() + " right edge remains reachable")
                        .isLessThanOrEqualTo(scroll.getViewport().getWidth());
                softly.assertThat(button.getWidth()).as(button.getText() + " full label")
                        .isGreaterThanOrEqualTo(button.getPreferredSize().width);
                button.doClick();
            }
            softly.assertThat(opened).containsAll(workflows);
            softly.assertAll();
        });
    }

    private static void assertReadableLabel(SoftAssertions softly, JLabel label, String context) {
        softly.assertThat(label.getWidth()).as(context + " full width")
                .isGreaterThanOrEqualTo(label.getPreferredSize().width);
        softly.assertThat(label.getHeight()).as(context + " full height")
                .isGreaterThanOrEqualTo(label.getPreferredSize().height);
    }

    private static void installTheme(boolean dark) {
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
    }

    private static Rectangle relativeBounds(Component component, Component ancestor) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), ancestor);
    }

    private static void settleLayout(Container root) {
        for (int pass = 0; pass < 6; pass++) {
            layoutTree(root);
        }
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component component : root.getComponents()) {
            if (component instanceof Container container) {
                layoutTree(container);
            }
        }
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream().filter(component -> name.equals(component.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing component " + name));
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> components = new ArrayList<>();
        if (type.isInstance(root)) {
            components.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                components.addAll(descendants(child, type));
            }
        }
        return components;
    }

    private static void savePreview(Container window, String name) {
        Path output = Path.of("target", "ui-previews", "farm-entry", name + ".png");
        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        window.printAll(graphics);
        graphics.dispose();
        try {
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
