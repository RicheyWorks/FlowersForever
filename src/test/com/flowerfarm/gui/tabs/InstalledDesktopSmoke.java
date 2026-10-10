package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.harness.InstalledDesktopHarness;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Native-input smoke that is intentionally excluded from ordinary CI. */
@DisplayName("Installed desktop human-input smoke")
class InstalledDesktopSmoke {

    @BeforeAll
    static void requireExplicitInstalledDesktop() {
        assertTrue(Boolean.getBoolean("flowerfarm.installed-desktop-smoke"),
                "Run through the Maven installed-desktop-smoke profile");
        assertFalse(GraphicsEnvironment.isHeadless(),
                "The installed desktop smoke needs an interactive graphics session");
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
    }

    @Test
    @DisplayName("operates and verifies every visible funeral-planning control natively")
    void exercisesEventServicesLikeAnInstalledDesktopWorker() {
        Host host = new Host();
        AtomicReference<String> clipboard = new AtomicReference<>();
        EventServicesTab tab = new EventServicesTab(host, clipboard::set);
        Path screenshot = Path.of("target", "installed-desktop-smoke",
                "event-services.png");
        Path before = Path.of("target", "installed-desktop-smoke",
                "event-services-before.png");

        try (InstalledDesktopHarness desktop = InstalledDesktopHarness.open(
                "FlowersForever installed smoke", tab.getUIComponent(),
                new Dimension(1200, 820))) {
            desktop.screenshot(before);

            assertThat(desktop.applicationButtonSelectors()).containsExactlyInAnyOrder(
                    "Clear service filters", "Build selected-service brief",
                    "Copy brief", "Clear brief", "Start CRM order brief");
            assertThat(desktop.editableTextSelectors()).containsExactlyInAnyOrder(
                    "Event service search", "Event or family name", "Event date",
                    "Event venue or locations", "Estimated guest count",
                    "Editable event service brief");
            assertThat(desktop.tableColumns("eventServices.table")).containsExactly(
                    "Service", "Occasion", "Category", "Pricing basis");

            List<InstalledDesktopHarness.FocusTarget> forward =
                    desktop.walkForwardFocusCycle(14);
            assertThat(forward).allMatch(InstalledDesktopHarness.FocusTarget::insideRoot);
            assertThat(InstalledDesktopHarness.uniqueSelectors(forward)).hasSizeGreaterThan(7);
            assertThat(desktop.pressShiftTab().insideRoot()).isTrue();

            desktop.click("Copy brief");
            assertThat(desktop.text("eventServices.feedback"))
                    .isEqualTo("Build a brief before copying");

            desktop.replaceText("Event service search", "not-a-real-service");
            assertThat(desktop.text("Event service search")).isEqualTo("not-a-real-service");
            assertThat(desktop.tableRows("eventServices.table")).isZero();
            desktop.click("Build selected-service brief");
            assertThat(desktop.text("eventServices.feedback"))
                    .isEqualTo("Choose one or more service rows");

            desktop.click("Clear service filters");
            assertThat(desktop.text("Event service search")).isBlank();
            assertThat(desktop.selectedOption("Event occasion filter"))
                    .isEqualTo("All occasions");
            assertThat(desktop.selectedOption("Event service category filter"))
                    .isEqualTo("All categories");
            assertThat(desktop.tableRows("eventServices.table")).isEqualTo(26);

            assertThat(desktop.exerciseEveryOption("Event occasion filter"))
                    .contains("All occasions", "Memorial / funeral", "Wedding");
            assertThat(desktop.exerciseEveryOption("Event service category filter"))
                    .contains("All categories", "Photography & media", "Catering");

            desktop.select("Event occasion filter", "Memorial / funeral")
                    .select("Event service category filter", "Photography & media")
                    .replaceText("Event or family name", "Chen family memorial")
                    .replaceText("Event date", "2026-09-12")
                    .replaceText("Event venue or locations",
                            "Harbor Chapel and reception hall")
                    .replaceText("Estimated guest count", "85");

            assertThat(desktop.text("Event or family name"))
                    .isEqualTo("Chen family memorial");
            assertThat(desktop.text("Event date")).isEqualTo("2026-09-12");
            assertThat(desktop.text("Event venue or locations"))
                    .isEqualTo("Harbor Chapel and reception hall");
            assertThat(desktop.text("Estimated guest count")).isEqualTo("85");

            for (String column : desktop.tableColumns("eventServices.table")) {
                desktop.sortByHeader("eventServices.table", column)
                        .sortByHeader("eventServices.table", column);
            }
            desktop.selectRows("eventServices.table", 0, 1)
                    .click("Build selected-service brief");

            assertThat(desktop.tableRows("eventServices.table")).isEqualTo(2);
            assertThat(desktop.selectedTableValues("eventServices.table", "Service"))
                    .containsExactlyInAnyOrder(
                            "Memorial photography", "Tribute photo and video coordination");
            assertThat(desktop.text("eventServices.feedback")).isEqualTo("2 services in brief");
            assertThat(desktop.text("Editable event service brief")).contains(
                    "Chen family memorial", "2026-09-12",
                    "Harbor Chapel and reception hall", "Estimated guests: 85",
                    "Memorial photography", "Tribute photo and video coordination");

            desktop.click("Copy brief");
            assertThat(desktop.text("eventServices.feedback")).isEqualTo("Brief copied");
            assertThat(clipboard).hasValue(desktop.text("Editable event service brief"));
            assertThat(clipboard.get()).contains(
                    "Memorial photography", "Tribute photo and video coordination");

            desktop.click("Start CRM order brief");
            assertThat(desktop.text("eventServices.feedback"))
                    .isEqualTo("Brief sent to Customers & Orders");
            assertThat(host.selectedTab).isEqualTo("Customers & Orders");
            assertThat(host.eventBrief).isEqualTo(clipboard.get());
            assertThat(host.occasion).isEqualTo("Memorial / funeral");
            assertThat(host.services).contains(
                    "Memorial photography", "Tribute photo and video coordination");
            assertThat(desktop.screenshot(screenshot)).exists();
            assertThat(Files.isRegularFile(screenshot)).isTrue();
            assertThat(desktop.transcript())
                    .anyMatch(line -> line.startsWith("POINTER CLICK"))
                    .anyMatch(line -> line.startsWith("KEYBOARD SELECT"))
                    .anyMatch(line -> line.startsWith("POINTER SORT"))
                    .anyMatch(line -> line.startsWith("POINTER MULTI-ROW"))
                    .anyMatch(line -> line.startsWith("KEYBOARD OPTION MATRIX"))
                    .anyMatch(line -> line.startsWith("TAB ->"))
                    .anyMatch(line -> line.startsWith("SCREENSHOT"));
            assertThat(desktop.transcript()).contains(
                    "POINTER CLICK Clear service filters",
                    "POINTER CLICK Build selected-service brief",
                    "POINTER CLICK Copy brief",
                    "POINTER CLICK Start CRM order brief");
        }
    }

    @Test
    @DisplayName("opens, focuses, and cancels installed native boundaries")
    void exercisesInstalledDialogAndFileChooserBoundaries() throws Exception {
        Path smokeDirectory = Path.of("target", "installed-desktop-smoke")
                .toAbsolutePath().normalize();
        Files.createDirectories(smokeDirectory);
        Path suggestedFile = smokeDirectory.resolve("native-chooser-probe.csv");
        Files.deleteIfExists(suggestedFile);
        JComponent boundaryPanel = nativeBoundaryPanel(suggestedFile);
        Path dialogScreenshot = smokeDirectory.resolve("native-dialog.png");
        Path chooserScreenshot = smokeDirectory.resolve("native-file-chooser.png");

        try (InstalledDesktopHarness desktop = InstalledDesktopHarness.open(
                "FlowersForever native boundary smoke", boundaryPanel,
                new Dimension(720, 360))) {
            desktop.clickAndAwaitWindow("Open native message", "Installed dialog probe");
            assertThat(desktop.windowOwnsFocus("Installed dialog probe")).isTrue();
            assertThat(desktop.windowButtonSelectors("Installed dialog probe"))
                    .contains("OK");
            assertThat(desktop.screenshotWindow(
                    "Installed dialog probe", dialogScreenshot)).exists();
            desktop.clickInWindowAndAwaitClosed("Installed dialog probe", "OK");
            assertThat(desktop.text("Native boundary result"))
                    .isEqualTo("Message dialog closed");

            desktop.clickAndAwaitWindow(
                    "Open native save chooser", "Installed chooser probe");
            assertThat(desktop.windowOwnsFocus("Installed chooser probe")).isTrue();
            assertThat(desktop.selectedFileInChooser("Installed chooser probe"))
                    .isEqualTo(suggestedFile);
            assertThat(desktop.windowButtonSelectors("Installed chooser probe"))
                    .contains("Cancel");
            assertThat(desktop.screenshotWindow(
                    "Installed chooser probe", chooserScreenshot)).exists();
            desktop.clickInWindowAndAwaitClosed("Installed chooser probe", "Cancel");

            assertThat(desktop.text("Native boundary result"))
                    .isEqualTo("Save selection cancelled");
            assertThat(suggestedFile).doesNotExist();
            assertThat(desktop.transcript()).contains(
                    "POINTER OPEN WINDOW Open native message -> Installed dialog probe",
                    "POINTER CLOSE WINDOW Installed dialog probe / OK",
                    "POINTER OPEN WINDOW Open native save chooser -> Installed chooser probe",
                    "POINTER CLOSE WINDOW Installed chooser probe / Cancel");
        }
    }

    private static JComponent nativeBoundaryPanel(Path suggestedFile) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel result = new JLabel("Ready for native boundary checks");
        result.setName("nativeBoundary.result");
        result.getAccessibleContext().setAccessibleName("Native boundary result");

        JButton message = new JButton("Open native message");
        message.addActionListener(event -> {
            FarmDialogs.swing().showMessage(panel,
                    "This message uses the installed FlowersForever dialog boundary.",
                    "Installed dialog probe", JOptionPane.INFORMATION_MESSAGE);
            result.setText("Message dialog closed");
        });

        JButton chooser = new JButton("Open native save chooser");
        chooser.addActionListener((ActionEvent event) -> {
            Optional<java.io.File> selected = FarmFileChooser.swing().chooseSave(
                    panel, "Installed chooser probe", suggestedFile.toFile());
            result.setText(selected.isPresent()
                    ? "Save selection approved" : "Save selection cancelled");
        });

        panel.add(new JLabel("Installed FlowersForever native-boundary probe"));
        panel.add(Box.createVerticalStrut(18));
        panel.add(message);
        panel.add(Box.createVerticalStrut(12));
        panel.add(chooser);
        panel.add(Box.createVerticalStrut(24));
        panel.add(result);
        return panel;
    }

    private static final class Host implements TabHost {
        private String selectedTab;
        private String eventBrief;
        private String occasion;
        private String services;

        @Override public void refreshAll() {
        }

        @Override public void selectTab(String tabTitle) {
            selectedTab = tabTitle;
        }

        @Override public void setStatus(String message) {
        }

        @Override public boolean showEventOrderDraft(
                String occasion, String services, String brief) {
            selectedTab = "Customers & Orders";
            this.occasion = occasion;
            this.services = services;
            eventBrief = brief;
            return true;
        }

        @Override public void runTrendAnalysis() {
        }
    }
}
