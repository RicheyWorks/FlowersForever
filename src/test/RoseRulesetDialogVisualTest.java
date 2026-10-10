package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.lsystem.LSystem;
import com.flowerfarm.lsystem.RoseLSystemPresets;
import com.flowerfarm.service.InventoryService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Compact rose ruleset dialog rendered contracts")
class RoseRulesetDialogVisualTest {
    private static final Path OUTPUT = Path.of("target", "ui-previews", "dialogs");
    private static final String LONG_NAME = "Nootka_Rose_North_Field_"
            + "Wedding_Collection_Long_Stem_Pink_".repeat(5) + "2026.json";

    @ParameterizedTest(name = "{0} with owner {1} by {2}")
    @CsvSource({"light, 660, 500", "dark, 660, 500", "light, 420, 360", "dark, 420, 360"})
    @DisplayName("long ruleset names fit compact dialogs and remain readable and copyable")
    void rendersCompactRulesetDialogs(String theme, int width, int height) throws Exception {
        Files.createDirectories(OUTPUT);
        SwingUtilities.invokeAndWait(() -> {
            try {
                if ("dark".equals(theme)) FlatDarkLaf.setup();
                else FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
                JPanel owner = new JPanel();
                owner.setSize(width, height);
                String alternative = LONG_NAME.replace("2026.json", "2027.json");
                String literalName = "<html>Rose & garden</html>.json";
                JComboBox<String> choices = new JComboBox<>(
                        new String[] {LONG_NAME, alternative, literalName});
                choices.getAccessibleContext().setAccessibleName("Saved ruleset to load");
                JScrollPane load = RoseVisualizerTab.rulesetLoadDialog(owner, choices);
                JOptionPane loadDialog = layoutDialog(owner, load, JOptionPane.QUESTION_MESSAGE);
                JTextArea filename = named(load, "rose.selectedRulesetFilename", JTextArea.class);
                JScrollPane reader = named(load, "rose.selectedRulesetFilenameScroll", JScrollPane.class);
                assertThat(choices.getWidth()).isGreaterThanOrEqualTo(100);
                assertThat(choices.getHeight()).isGreaterThanOrEqualTo(36);
                assertThat(choices.getPreferredSize().width).isLessThan(500);
                assertThat(choices.getItemAt(0)).isEqualTo(LONG_NAME);
                assertThat(filename.getText()).isEqualTo(LONG_NAME);
                assertThat(filename.getLineWrap()).isTrue();
                assertThat(filename.isEditable()).isFalse();
                assertThat(filename.isFocusable()).isTrue();
                render(theme + "-" + width + "x" + height + "-rose-load", loadDialog);

                choices.setSelectedIndex(1);
                assertThat(choices.getSelectedItem()).isEqualTo(alternative);
                assertThat(choices.getAccessibleContext().getAccessibleDescription()).isEqualTo(alternative);
                assertThat(filename.getText()).isEqualTo(alternative);
                filename.selectAll();
                assertThat(filename.getSelectedText()).isEqualTo(alternative);
                settle(loadDialog);
                reader.getVerticalScrollBar().setValue(reader.getVerticalScrollBar().getMaximum());
                Point position = reader.getViewport().getViewPosition();
                focus(filename);
                assertThat(reader.getViewport().getViewPosition()).isEqualTo(position);
                assertRevealed(reader, load);

                choices.setSelectedIndex(2);
                assertThat(filename.getText()).isEqualTo(literalName);
                assertThat(filename.getAccessibleContext().getAccessibleDescription()).isEqualTo(literalName);
                assertThat(choices.getToolTipText()).contains("&lt;html&gt;", "&amp;");
                JLabel rendered = (JLabel) choices.getRenderer().getListCellRendererComponent(
                        new JList<>(), literalName, 2, false, false);
                assertThat(rendered.getText()).isEqualTo(literalName);
                assertThat(rendered.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE);
                assertThat(rendered.getToolTipText()).contains("&lt;html&gt;", "&amp;");

                JTextField name = new JTextField(LONG_NAME, 28);
                name.getAccessibleContext().setAccessibleName("Ruleset name");
                JScrollPane save = RoseVisualizerTab.rulesetSaveDialog(owner, name);
                JOptionPane saveDialog = layoutDialog(owner, save, JOptionPane.PLAIN_MESSAGE);
                assertThat(name.getText()).isEqualTo(LONG_NAME);
                assertThat(name.getHeight()).isGreaterThanOrEqualTo(36);
                assertThat(name.getAccessibleContext().getAccessibleName()).isEqualTo("Ruleset name");
                name.setCaretPosition(0);
                focus(name);
                assertRevealed(name, save);
                render(theme + "-" + width + "x" + height + "-rose-save", saveDialog);
            } finally {
                FlatLightLaf.setup();
                FarmUi.installThemeDefaults();
            }
        });
    }

    @Test
    @DisplayName("cancelling the production save action retains the current rose and skips writes")
    void cancelsProductionSave() throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        FarmFileChooser files = mock(FarmFileChooser.class);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        TabHost host = mock(TabHost.class);
        when(host.canMutateData()).thenReturn(true);
        when(dialogs.showConfirm(any(), any(), eq("Save L-System ruleset"),
                eq(JOptionPane.OK_CANCEL_OPTION), eq(JOptionPane.PLAIN_MESSAGE)))
                .thenAnswer(invocation -> {
                    JScrollPane form = invocation.getArgument(1);
                    JTextField name = named(form, "rose.rulesetName", JTextField.class);
                    assertThat(name.getText()).isEqualTo("Nootka Rose");
                    name.setText("Unsaved replacement name");
                    return JOptionPane.CANCEL_OPTION;
                });
        SwingUtilities.invokeAndWait(() -> {
            RoseVisualizerTab tab = new RoseVisualizerTab(inventory, host, dialogs, files);
            JComponent page = tab.getUIComponent();
            page.setSize(660, 500);
            @SuppressWarnings("unchecked")
            JComboBox<LSystem> presets = (JComboBox<LSystem>) descendants(page, JComboBox.class).stream()
                    .filter(combo -> "Rose variety or saved ruleset".equals(
                            combo.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow();
            presets.addItem(RoseLSystemPresets.nootka());
            LSystem before = (LSystem) presets.getSelectedItem();
            descendants(page, AbstractButton.class).stream()
                    .filter(button -> "Save ruleset…".equals(button.getText()))
                    .findFirst().orElseThrow().doClick();
            assertThat(presets.getSelectedItem()).isSameAs(before);
            assertThat(before.getName()).isEqualTo("Nootka Rose");
        });
        verify(host).setStatus("Ruleset was not saved; the current rose is unchanged.");
        verifyNoInteractions(inventory, files);
    }

    private static JOptionPane layoutDialog(JComponent owner, JScrollPane scroll, int messageType) {
        JOptionPane dialog = new JOptionPane(scroll, messageType, JOptionPane.OK_CANCEL_OPTION);
        dialog.setSize(dialog.getPreferredSize());
        settle(dialog);
        assertThat(dialog.getWidth()).as("dialog fits compact owner").isLessThan(owner.getWidth());
        assertThat(dialog.getHeight()).isLessThanOrEqualTo(owner.getHeight() - 30);
        assertThat(scroll.getHorizontalScrollBar().isVisible()).isFalse();
        assertThat(scroll.getViewport().getView().getWidth()).isEqualTo(scroll.getViewport().getWidth());
        List<AbstractButton> actions = descendants(dialog, AbstractButton.class).stream()
                .filter(button -> "OK".equals(button.getText()) || "Cancel".equals(button.getText())).toList();
        assertThat(actions).hasSize(2);
        for (AbstractButton button : actions) {
            Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), dialog);
            assertThat(new Rectangle(0, 0, dialog.getWidth(), dialog.getHeight()).contains(bounds))
                    .as("dialog action remains visible: %s", button.getText()).isTrue();
            assertThat(button.getHeight()).isGreaterThan(20);
        }
        return dialog;
    }

    private static void assertRevealed(JComponent component, JScrollPane scroll) {
        Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(),
                scroll.getViewport().getView());
        assertThat(scroll.getViewport().getViewRect().contains(bounds)).isTrue();
    }

    private static void focus(JComponent component) {
        FocusEvent gained = new FocusEvent(component, FocusEvent.FOCUS_GAINED);
        for (var listener : component.getFocusListeners()) listener.focusGained(gained);
        FocusEvent lost = new FocusEvent(component, FocusEvent.FOCUS_LOST);
        for (var listener : component.getFocusListeners()) listener.focusLost(lost);
    }

    private static void settle(Component component) {
        for (int pass = 0; pass < 5; pass++) layout(component);
    }

    private static void layout(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) layout(child);
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
            throw new AssertionError("Could not render ruleset dialog", exception);
        }
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        return descendants(root, type).stream().filter(component -> name.equals(component.getName()))
                .findFirst().orElseThrow();
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
