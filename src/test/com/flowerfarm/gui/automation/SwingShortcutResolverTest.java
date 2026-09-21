package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingShortcutResolverTest {
    private static final KeyStroke F1 = KeyStroke.getKeyStroke("F1");
    private static final KeyStroke F5 = KeyStroke.getKeyStroke("F5");

    @Test
    void invokesProductionShapedGuideAccessAndDarkModeAcceleratorsWithClosedMenus() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger guideCalls = new AtomicInteger();
            AtomicInteger accessCalls = new AtomicInteger();
            AtomicInteger darkModeCalls = new AtomicInteger();
            JMenu help = new JMenu("Help");
            help.add(item("PNW Plant Guide", F1, guideCalls));
            JMenu account = new JMenu("Account");
            KeyStroke accessKey = KeyStroke.getKeyStroke("ctrl shift M");
            account.add(item("My access", accessKey, accessCalls));
            JMenu view = new JMenu("View");
            JCheckBoxMenuItem darkMode = new JCheckBoxMenuItem("Dark Mode");
            KeyStroke darkKey = KeyStroke.getKeyStroke("ctrl shift D");
            darkMode.setAccelerator(darkKey);
            darkMode.addActionListener(event -> darkModeCalls.incrementAndGet());
            view.add(darkMode);
            JRootPane root = root(help, account, view);

            SwingShortcutResolver.invoke(root, F1);
            SwingShortcutResolver.invoke(root, accessKey);
            SwingShortcutResolver.invoke(root, darkKey);

            assertThat(guideCalls).hasValue(1);
            assertThat(accessCalls).hasValue(1);
            assertThat(darkModeCalls).hasValue(1);
            assertThat(darkMode.isSelected()).isTrue();
            assertThat(help.isPopupMenuVisible()).isFalse();
            assertThat(account.isPopupMenuVisible()).isFalse();
            assertThat(view.isPopupMenuVisible()).isFalse();
            SwingShortcutResolver.invoke(root, darkKey);
            assertThat(darkMode.isSelected()).isFalse();
            assertThat(darkModeCalls).hasValue(2);
        });
    }

    @Test
    void enforcesVisibilityAndEnabledStateThroughoutTheLogicalMenuHierarchy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger calls = new AtomicInteger();
            JMenuItem guide = item("PNW Plant Guide", F1, calls);
            JMenu reference = new JMenu("Reference");
            reference.add(guide);
            JMenu help = new JMenu("Help");
            help.add(reference);
            JMenuBar bar = new JMenuBar();
            bar.add(help);
            JPanel wrapper = new JPanel();
            wrapper.add(bar);
            JPanel root = new JPanel();
            root.add(wrapper);

            for (JComponent target : new JComponent[]{guide, reference, help, bar, wrapper}) {
                target.setEnabled(false);
                assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F1))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
                target.setEnabled(true);
                target.setVisible(false);
                assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F1))
                        .isInstanceOf(IllegalArgumentException.class);
                target.setVisible(true);
                assertThat(calls).hasValue(0);
            }
            SwingShortcutResolver.invoke(root, F1);
            assertThat(calls).hasValue(1);
        });
    }

    @Test
    void rejectsDuplicateAcceleratorsBeforeEitherMenuCommandRuns() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger firstCalls = new AtomicInteger();
            AtomicInteger secondCalls = new AtomicInteger();
            JMenu first = new JMenu("Help");
            first.add(item("Guide", F1, firstCalls));
            JMenu second = new JMenu("Tools");
            JMenuItem secondItem = item("Other guide", F1, secondCalls);
            second.add(secondItem);
            JRootPane root = root(first, second);

            assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
            assertThat(firstCalls).hasValue(0);
            assertThat(secondCalls).hasValue(0);
            secondItem.setVisible(false);
            SwingShortcutResolver.invoke(root, F1);
            assertThat(firstCalls).hasValue(1);
            assertThat(secondCalls).hasValue(0);
        });
    }

    @Test
    void rejectsAmbiguousMenuPathsEvenWhenOnlyOneItemHasTheRequestedAccelerator() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger calls = new AtomicInteger();
            JMenu help = new JMenu("Help");
            JMenuItem guide = item("Guide", F1, calls);
            JMenuItem duplicate = item("Guide", KeyStroke.getKeyStroke("F2"), calls);
            help.add(guide);
            help.add(duplicate);
            JRootPane root = root(help);
            assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
            assertThat(calls).hasValue(0);
            help.remove(duplicate);

            JMenu duplicateTop = new JMenu("Help");
            duplicateTop.add(item("Other guide", KeyStroke.getKeyStroke("F2"), calls));
            root.getJMenuBar().add(duplicateTop);
            assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
            assertThat(calls).hasValue(0);
            root.getJMenuBar().remove(duplicateTop);
            SwingShortcutResolver.invoke(root, F1);
            assertThat(calls).hasValue(1);
        });
    }

    @Test
    void prefersTheExplicitF5BindingAndFallsBackWhenItsSourceOrActionIsDisabled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger menuCalls = new AtomicInteger();
            AtomicInteger explicitCalls = new AtomicInteger();
            AtomicReference<Object> source = new AtomicReference<>();
            JMenu view = new JMenu("View");
            view.add(item("Refresh All Tabs", F5, menuCalls));
            JRootPane root = root(view);
            JPanel scope = new JPanel();
            root.getContentPane().add(scope);
            Action explicit = new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    explicitCalls.incrementAndGet();
                    source.set(event.getSource());
                }
            };
            bind(scope, JComponent.WHEN_IN_FOCUSED_WINDOW, F5, explicit);
            bind(scope, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, F5, explicit);

            SwingShortcutResolver.invoke(root, F5);
            assertThat(explicitCalls).hasValue(1);
            assertThat(source.get()).isSameAs(scope);
            assertThat(menuCalls).hasValue(0);

            scope.setEnabled(false);
            SwingShortcutResolver.invoke(root, F5);
            assertThat(menuCalls).hasValue(1);
            scope.setEnabled(true);
            explicit.setEnabled(false);
            SwingShortcutResolver.invoke(root, F5);
            assertThat(menuCalls).hasValue(2);
            explicit.setEnabled(true);
            scope.setVisible(false);
            SwingShortcutResolver.invoke(root, F5);
            assertThat(menuCalls).hasValue(3);
            assertThat(explicitCalls).hasValue(1);
        });
    }

    @Test
    void keepsSharedActionsOnDifferentSourcesAmbiguousAndUsesTheCorrectEnabledSource() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger calls = new AtomicInteger();
            AtomicReference<Object> source = new AtomicReference<>();
            Action shared = new AbstractAction() {
                @Override public void actionPerformed(ActionEvent event) {
                    calls.incrementAndGet();
                    source.set(event.getSource());
                }
            };
            JPanel first = new JPanel();
            JPanel second = new JPanel();
            bind(first, JComponent.WHEN_IN_FOCUSED_WINDOW, F5, shared);
            bind(second, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, F5, shared);
            JPanel secondWrapper = new JPanel();
            secondWrapper.add(second);
            JPanel root = new JPanel();
            root.add(first);
            root.add(secondWrapper);

            assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F5))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
            assertThat(calls).hasValue(0);
            first.setEnabled(false);
            SwingShortcutResolver.invoke(root, F5);
            assertThat(calls).hasValue(1);
            assertThat(source.get()).isSameAs(second);
            second.setEnabled(false);
            assertThatThrownBy(() -> SwingShortcutResolver.invoke(root, F5))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("disabled");
            assertThat(calls).hasValue(1);
            first.setEnabled(true);
            second.setEnabled(true);
            secondWrapper.setVisible(false);
            SwingShortcutResolver.invoke(root, F5);
            assertThat(calls).hasValue(2);
            assertThat(source.get()).isSameAs(first);
        });
    }

    @Test
    void cannotResolveAnOwnerWindowShortcutFromAnActiveModalRoot() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger ownerCalls = new AtomicInteger();
            JMenu ownerHelp = new JMenu("Help");
            ownerHelp.add(item("Owner guide", F1, ownerCalls));
            JRootPane owner = root(ownerHelp);
            JRootPane modal = new JRootPane();
            owner.getContentPane().add(modal);

            assertThatThrownBy(() -> SwingShortcutResolver.invoke(modal, F1))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 0");
            AtomicInteger modalCalls = new AtomicInteger();
            JMenu modalHelp = new JMenu("Help");
            modalHelp.add(item("Dialog guide", F1, modalCalls));
            JMenuBar modalBar = new JMenuBar();
            modalBar.add(modalHelp);
            modal.setJMenuBar(modalBar);
            SwingShortcutResolver.invoke(modal, F1);
            assertThat(modalCalls).hasValue(1);
            assertThat(ownerCalls).hasValue(0);
        });
    }

    @Test
    void rejectsCallsOutsideTheEventDispatchThread() throws Exception {
        AtomicReference<JRootPane> root = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            JMenu help = new JMenu("Help");
            help.add(item("Guide", F1, calls));
            root.set(root(help));
        });
        assertThatThrownBy(() -> SwingShortcutResolver.invoke(root.get(), F1))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("event-dispatch");
        assertThat(calls).hasValue(0);
    }

    private static JMenuItem item(String label, KeyStroke shortcut, AtomicInteger calls) {
        JMenuItem item = new JMenuItem(label);
        item.setAccelerator(shortcut);
        item.addActionListener(event -> calls.incrementAndGet());
        return item;
    }

    private static JRootPane root(JMenu... menus) {
        JMenuBar bar = new JMenuBar();
        for (JMenu menu : menus) {
            bar.add(menu);
        }
        JRootPane root = new JRootPane();
        root.setJMenuBar(bar);
        return root;
    }

    private static void bind(JComponent source, int condition, KeyStroke shortcut, Action action) {
        String key = "shortcut-" + shortcut;
        source.getInputMap(condition).put(shortcut, key);
        source.getActionMap().put(key, action);
    }
}
