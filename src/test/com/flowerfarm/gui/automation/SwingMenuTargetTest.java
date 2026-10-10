package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingMenuTargetTest {
    private static final List<String> DARK_MODE = List.of("View", "Appearance", "Dark Mode");

    @Test
    void resolvesCheckboxCommandsThroughClosedNestedMenusWithoutInvokingThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture();
            AtomicInteger invocations = new AtomicInteger();
            fixture.item().addActionListener(event -> invocations.incrementAndGet());

            SwingMenuTarget.Target target = SwingMenuTarget.resolve(fixture.root(), DARK_MODE, true);

            assertThat(target.item()).isSameAs(fixture.item());
            assertThat(target.enabled()).isTrue();
            assertThat(fixture.top().isPopupMenuVisible()).isFalse();
            assertThat(fixture.submenu().isPopupMenuVisible()).isFalse();
            assertThat(fixture.item().isSelected()).isFalse();
            assertThat(invocations).hasValue(0);
        });
    }

    @Test
    void reportsEffectiveDisabledStateAndRejectsActionsAtEveryAncestor() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture();
            for (var component : List.of(fixture.root(), fixture.container(), fixture.bar(),
                    fixture.top(), fixture.submenu(), fixture.item())) {
                component.setEnabled(false);
                assertThat(SwingMenuTarget.resolve(fixture.root(), DARK_MODE, false).enabled())
                        .as(component.getClass().getSimpleName()).isFalse();
                assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), DARK_MODE, true))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
                component.setEnabled(true);
            }
        });
    }

    @Test
    void rejectsHiddenMenusItemsAndContainerAncestors() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture();
            for (var component : List.of(fixture.top(), fixture.submenu(), fixture.item())) {
                component.setVisible(false);
                assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), DARK_MODE, false))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hidden");
                component.setVisible(true);
            }
            for (var component : List.of(fixture.root(), fixture.container(), fixture.bar())) {
                component.setVisible(false);
                assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), DARK_MODE, false))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Menu bar")
                        .hasMessageContaining("hidden");
                component.setVisible(true);
            }
        });
    }

    @Test
    void rejectsDuplicateTopLevelAndChildLabelsIncludingHiddenDuplicates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture();
            JMenu duplicateTop = new JMenu("View");
            fixture.bar().add(duplicateTop);
            assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), DARK_MODE, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
            fixture.bar().remove(duplicateTop);
            JMenuItem duplicateItem = new JMenuItem("Dark Mode");
            duplicateItem.setVisible(false);
            fixture.submenu().add(duplicateItem);
            assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), DARK_MODE, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
        });
    }

    @Test
    void validatesPathsAndRequiresExactLabelsAndALeafCommand() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture();
            assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), null, true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("top-level");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(fixture.root(), List.of("View"), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("top-level");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(
                    fixture.root(), List.of("View", " "), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-blank");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(
                    fixture.root(), Arrays.asList("View", null), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-blank");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(
                    fixture.root(), List.of("view", "Appearance", "Dark Mode"), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 0");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(
                    fixture.root(), List.of("View", "Appearance", "Dark Mode "), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 0");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(
                    fixture.root(), List.of("View", "Appearance"), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("submenu");
            assertThatThrownBy(() -> SwingMenuTarget.resolve(
                    fixture.root(), List.of("View", "Appearance", "Dark Mode", "Extra"), true))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("continues after");
        });
    }

    @Test
    void cannotEscapeTheActiveRootToReachAnotherWindowsMenu() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture();
            JPanel modal = new JPanel();
            fixture.root().add(modal);
            assertThatThrownBy(() -> SwingMenuTarget.resolve(modal, DARK_MODE, false))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("no visible menu bar");
            JRootPane root = new JRootPane();
            root.setJMenuBar(fixture.bar());
            assertThat(SwingMenuTarget.resolve(root, DARK_MODE, true).item()).isSameAs(fixture.item());
        });
    }

    @Test
    void findsOnlyTheSelectedTabsMenuAndRejectsMultipleVisibleBars() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture current = fixture();
            Fixture other = fixture();
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Current", current.root());
            tabs.addTab("Other", other.root());
            assertThat(SwingMenuTarget.resolve(tabs, DARK_MODE, true).item()).isSameAs(current.item());
            tabs.setSelectedIndex(1);
            assertThat(SwingMenuTarget.resolve(tabs, DARK_MODE, true).item()).isSameAs(other.item());
            JPanel ambiguous = new JPanel();
            ambiguous.add(current.bar());
            ambiguous.add(other.bar());
            assertThatThrownBy(() -> SwingMenuTarget.resolve(ambiguous, DARK_MODE, false))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("menu bar")
                    .hasMessageContaining("found 2");
        });
    }

    @Test
    void requiresTheEventDispatchThread() {
        assertThatThrownBy(() -> SwingMenuTarget.resolve(new JPanel(), DARK_MODE, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("event-dispatch thread");
    }

    private static Fixture fixture() {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem("Dark Mode");
        JMenu submenu = new JMenu("Appearance");
        submenu.add(item);
        JMenu top = new JMenu("View");
        top.add(submenu);
        JMenuBar bar = new JMenuBar();
        bar.add(top);
        JPanel container = new JPanel();
        container.add(bar);
        JPanel root = new JPanel();
        root.add(container);
        return new Fixture(root, container, bar, top, submenu, item);
    }

    private record Fixture(JPanel root, JPanel container, JMenuBar bar,
                           JMenu top, JMenu submenu, JCheckBoxMenuItem item) {
    }
}
