package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SwingHarnessFileDialogTest {
    private static final String TOKEN = "desktop-file-dialog-test-token-123456";
    private static final String SELECTOR = "Farm export file chooser";

    @TempDir Path directory;

    @Test
    void approvesAndCancelsExportsThroughGatewayWithoutNativeWindows() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            AtomicReference<JComponent> modal = new AtomicReference<>();
            AtomicReference<SecondaryLoop> loop = new AtomicReference<>();
            AtomicReference<Path> approved = new AtomicReference<>();
            AtomicInteger exports = new AtomicInteger();
            JPanel root = new JPanel();
            SwingHarnessBridge bridge = new SwingHarnessBridge(
                    attached -> modal.get() == null ? attached : modal.get());
            SwingUtilities.invokeAndWait(() -> {
                JFileChooser files = chooser(directory);
                files.addActionListener(event -> {
                    if (JFileChooser.APPROVE_SELECTION.equals(event.getActionCommand())) {
                        approved.set(files.getSelectedFile().toPath());
                    }
                    loop.get().exit();
                });
                JButton export = new JButton("Export inventory");
                export.addActionListener(event -> {
                    approved.set(null);
                    loop.set(Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop());
                    modal.set(files);
                    try {
                        loop.get().enter();
                        if (approved.get() != null) {
                            Files.writeString(approved.get(), "Name,Quantity\nWorkshop,8\n");
                            exports.incrementAndGet();
                        }
                    } catch (IOException failure) {
                        throw new IllegalStateException(failure);
                    } finally {
                        modal.set(null);
                    }
                });
                root.add(export);
                bridge.attach(root, ignored -> true);
            });
            HarnessGateway gateway = gateway(bridge);
            Path output = directory.resolve("custom-inventory.csv");
            try {
                var opened = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("open-export", "activate-button", Map.of("selector", "Export inventory")));
                assertThat(opened.output()).containsEntry("status", "PENDING");
                assertThat(opened.snapshot().metadata()).containsEntry("modalActive", true);
                assertThat(opened.snapshot().controls()).singleElement()
                        .satisfies(control -> {
                            assertThat(control.selector()).isEqualTo("farm.exportFileChooser");
                            assertThat(control.label()).isEqualTo(SELECTOR);
                            assertThat(control.commandable()).isTrue();
                        });
                var details = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("read-dialog", "read-control", Map.of("selector", SELECTOR)));
                assertThat(details.output()).containsEntry("kind", "file-chooser")
                        .containsEntry("dialogType", "SAVE").containsEntry("title", "Export inventory CSV");

                HarnessCommand approve = command("approve-export", "choose-file",
                        Map.of("selector", SELECTOR, "path", output.toString()));
                assertThat(gateway.execute(TOKEN, SwingHarnessPlugin.ID, approve).success()).isTrue();
                SwingUtilities.invokeAndWait(() -> { });
                assertThat(output).hasContent("Name,Quantity\nWorkshop,8\n");
                assertThat(gateway.execute(TOKEN, SwingHarnessPlugin.ID, approve).replayed()).isTrue();
                assertThat(exports).hasValue(1);

                gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("open-cancelled", "activate-button", Map.of("selector", "Export inventory")));
                assertThat(gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                        command("cancel-export", "cancel-file-dialog", Map.of("selector", SELECTOR)))
                        .success()).isTrue();
                SwingUtilities.invokeAndWait(() -> { });
                assertThat(exports).hasValue(1);
                assertThat(modal.get()).isNull();
            } finally {
                if (loop.get() != null) {
                    loop.get().exit();
                }
            }
        });
    }

    @Test
    void rejectsRelativePathsDirectoriesAndFilesOutsideTheActiveFilter() throws Exception {
        AtomicReference<JFileChooser> chooser = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> chooser.set(chooser(directory)));
        AtomicInteger approvals = new AtomicInteger();
        chooser.get().addActionListener(event -> approvals.incrementAndGet());
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(chooser.get(), ignored -> true);

        assertThatThrownBy(() -> bridge.chooseFile(SELECTOR, "relative.csv"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("absolute");
        assertThatThrownBy(() -> bridge.chooseFile(SELECTOR, directory.toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("file type");
        assertThatThrownBy(() -> bridge.chooseFile(SELECTOR, directory.resolve("wrong.exe").toString()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("active filter");
        assertThat(approvals).hasValue(0);
    }

    private static JFileChooser chooser(Path directory) {
        JFileChooser chooser = new JFileChooser(directory.toFile());
        chooser.setDialogType(JFileChooser.SAVE_DIALOG);
        chooser.setDialogTitle("Export inventory CSV");
        chooser.setName("farm.exportFileChooser");
        chooser.getAccessibleContext().setAccessibleName(SELECTOR);
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        chooser.setSelectedFile(directory.resolve("inventory.csv").toFile());
        return chooser;
    }

    private static HarnessCommand command(String id, String action, Map<String, Object> arguments) {
        return new HarnessCommand(id, action, arguments);
    }

    private static HarnessGateway gateway(SwingHarnessBridge bridge) {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowSensitiveRead(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        return new HarnessGateway(new HarnessRegistry(List.of(new SwingHarnessPlugin(bridge))), properties);
    }
}
