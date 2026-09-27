package com.flowerfarm.gui;

import com.flowerfarm.gui.tabs.KnowledgeLogTab;
import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.reference.PnwPlantCatalog;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.SeasonPlanService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("Compact entry dialog rendered contracts")
class EntryDialogVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "dialogs");

    @ParameterizedTest(name = "{0} with owner {1} by {2}")
    @CsvSource({"light, 660, 500", "dark, 660, 500", "light, 1050, 700", "dark, 1050, 700"})
    @DisplayName("lesson and planting dialogs fit their owner and reveal every field")
    void compactEntryDialogs(String theme, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        SeasonPlanService seasons = mock(SeasonPlanService.class);
        SwingUtilities.invokeAndWait(() -> {
            try {
                if ("dark".equals(theme)) FlatDarkLaf.setup();
                else FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
                CapturingDialogs dialogs = new CapturingDialogs(theme + "-" + width + "x" + height);
                KnowledgeLogTab knowledgeTab = new KnowledgeLogTab(knowledge, null, dialogs);
                JComponent knowledgePage = knowledgeTab.getUIComponent();
                knowledgePage.setSize(width, height);
                ((AbstractButton) named(knowledgePage, "knowledge.record")).doClick();

                SeasonPlannerTab seasonTab = new SeasonPlannerTab(seasons, null, dialogs);
                JComponent seasonPage = seasonTab.getUIComponent();
                seasonPage.setSize(width, height);
                ((AbstractButton) named(seasonPage, "seasonPlanner.add")).doClick();
                assertThat(seasonTab.preparePlantingFromReference(PnwPlantCatalog.all().get(0)))
                        .as("closing the visual review cancels the planting").isFalse();
                assertThat(dialogs.seen).containsExactly(
                        "Record a farm lesson", "Add planting block",
                        "Plan “" + PnwPlantCatalog.all().get(0).commonName() + "”");
            } finally {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
        verifyNoInteractions(knowledge, seasons);
    }

    private static final class CapturingDialogs implements FarmDialogs {
        private final String prefix;
        private final List<String> seen = new ArrayList<>();

        private CapturingDialogs(String prefix) { this.prefix = prefix; }

        @Override public int showConfirm(Component owner, Object message, String title,
                                         int optionType, int messageType) {
            seen.add(title);
            assertThat(message).isInstanceOf(JScrollPane.class);
            JScrollPane scroll = (JScrollPane) message;
            String name = title.startsWith("Record") ? "knowledge-entry"
                    : title.startsWith("Add") ? "planting-entry" : "planting-reference";
            JOptionPane dialog = new JOptionPane(message, messageType, optionType);
            try {
                dialog.setSize(dialog.getPreferredSize());
                settle(dialog);
                assertThat(dialog.getWidth()).as("dialog width fits its owner").isLessThan(owner.getWidth());
                assertThat(dialog.getHeight()).as("dialog leaves room for its title bar")
                        .isLessThanOrEqualTo(owner.getHeight() - 30);
                assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
                assertThat(scroll.getViewport().getView().getWidth())
                        .isEqualTo(scroll.getViewport().getWidth());
                assertControlsFit(scroll.getViewport().getView());
                List<JTextArea> areas = descendants(scroll, JTextArea.class);
                assertThat(areas).isNotEmpty();
                for (JTextArea area : areas) {
                    assertThat(area.getFont().getSize()).isGreaterThanOrEqualTo(14);
                    assertThat(area.getLineWrap()).isTrue();
                }
                render(prefix + "-" + name, dialog);
                List<JComponent> fields = new ArrayList<>();
                fields.addAll(descendants(scroll, JTextField.class));
                fields.addAll(descendants(scroll, JComboBox.class));
                fields.addAll(areas);
                for (JComponent field : fields) {
                    if (!field.isEnabled() || !field.isVisible()) continue;
                    FocusEvent event = new FocusEvent(field, FocusEvent.FOCUS_GAINED);
                    for (var listener : field.getFocusListeners()) listener.focusGained(event);
                    JComponent visible = field;
                    if (field instanceof JTextComponent && field.getParent() instanceof JViewport viewport
                            && viewport.getParent() instanceof JScrollPane reader) {
                        visible = reader;
                    }
                    Rectangle bounds = SwingUtilities.convertRectangle(visible.getParent(),
                            visible.getBounds(), scroll.getViewport().getView());
                    assertThat(scroll.getViewport().getViewRect().contains(bounds))
                            .as("keyboard reveals %s", field.getAccessibleContext().getAccessibleName()).isTrue();
                    FocusEvent lost = new FocusEvent(field, FocusEvent.FOCUS_LOST);
                    for (var listener : field.getFocusListeners()) listener.focusLost(lost);
                }
                if (scroll.getViewport().getView().getHeight() > scroll.getViewport().getHeight()) {
                    assertThat(scroll.getViewport().getViewPosition().y)
                            .as("long form content can be reached").isPositive();
                }
                render(prefix + "-" + name + "-notes", dialog);
            } finally {
                dialog.removeAll();
            }
            return JOptionPane.CANCEL_OPTION;
        }

        @Override public int showOption(Component parent, Object message, String title,
                                        int optionType, int messageType, Icon icon,
                                        Object[] options, Object initialValue) {
            throw new AssertionError("Unexpected option dialog: " + title);
        }

        @Override public void showMessage(Component parent, Object message, String title, int messageType) {
            throw new AssertionError("Unexpected message: " + title);
        }
    }

    private static void assertControlsFit(Component root) {
        for (JTextField field : descendants(root, JTextField.class)) {
            assertThat(field.getWidth()).as("readable %s", field.getAccessibleContext().getAccessibleName())
                    .isGreaterThanOrEqualTo(60);
            assertThat(field.getHeight()).isGreaterThanOrEqualTo(32);
            assertContained(field);
        }
        for (JLabel label : descendants(root, JLabel.class)) {
            if (label.getLabelFor() != null) assertContained(label);
        }
    }

    private static void assertContained(Component component) {
        assertThat(new Rectangle(0, 0, component.getParent().getWidth(), component.getParent().getHeight())
                .contains(component.getBounds())).as("control fits form: %s", component.getName()).isTrue();
    }

    private static void settle(Component root) {
        for (int pass = 0; pass < 5; pass++) layout(root);
    }

    private static void layout(Component root) {
        if (root instanceof Container parent) {
            parent.doLayout();
            for (Component child : parent.getComponents()) layout(child);
        }
    }

    private static void render(String name, JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        try {
            ImageIO.write(image, "png", OUTPUT.resolve(name + ".png").toFile());
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) return root;
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) {
                Component match = named(child, name);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(root)) matches.add(type.cast(root));
        if (root instanceof Container parent) {
            for (Component child : parent.getComponents()) matches.addAll(descendants(child, type));
        }
        return matches;
    }
}
