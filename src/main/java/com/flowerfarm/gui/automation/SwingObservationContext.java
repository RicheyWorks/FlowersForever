package com.flowerfarm.gui.automation;

import javax.accessibility.AccessibleContext;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoundedRangeModel;
import javax.swing.ButtonModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JScrollBar;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.SingleSelectionModel;
import javax.swing.SpinnerModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.RowSorterListener;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** One opaque observation receipt, bound to the current Swing tree and model revisions. */
final class SwingObservationContext {
    private final Map<Object, Watch> watches = new IdentityHashMap<>();
    private List<Object> observedState;
    private String observedToken;

    String observe(JComponent activeRoot) {
        requireEdt();
        List<Object> current = capture(activeRoot);
        if (!current.equals(observedState)) {
            observedState = current;
            observedToken = UUID.randomUUID().toString();
        }
        return observedToken;
    }

    boolean matches(String expectedToken, JComponent activeRoot) {
        requireEdt();
        List<Object> current = capture(activeRoot);
        if (!current.equals(observedState)) {
            // A rejected context must never revive after its old tree is restored.
            observedState = null;
            observedToken = null;
            return false;
        }
        return observedToken != null && observedToken.equals(expectedToken);
    }

    void clear() {
        requireEdt();
        watches.values().forEach(Watch::detach);
        watches.clear();
        observedState = null;
        observedToken = null;
    }

    private List<Object> capture(JComponent root) {
        Objects.requireNonNull(root, "activeRoot");
        List<Object> state = new ArrayList<>();
        Map<Object, Boolean> retained = new IdentityHashMap<>();
        Map<Component, Boolean> visited = new IdentityHashMap<>();
        state.add(new Identity(root));
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        state.add(new Identity(focus == root || focus != null
                && SwingUtilities.isDescendingFrom(focus, root) ? focus : null));
        captureComponent(root, true, state, retained, visited);
        Iterator<Map.Entry<Object, Watch>> iterator = watches.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Object, Watch> entry = iterator.next();
            if (!retained.containsKey(entry.getKey())) {
                entry.getValue().detach();
                iterator.remove();
            }
        }
        return Collections.unmodifiableList(state);
    }

    private void captureComponent(Component component, boolean root, List<Object> state,
                                  Map<Object, Boolean> retained,
                                  Map<Component, Boolean> visited) {
        if (!root && !component.isVisible() || visited.put(component, Boolean.TRUE) != null) {
            return;
        }
        state.add("component");
        AccessibleContext accessible = null;
        if (component instanceof JComponent swing) {
            // Swing's lazy accessibility and binding initialization can set component
            // properties; finish both before recording this component's revision.
            accessible = swing.getAccessibleContext();
            captureBindings(swing, state, retained);
        }
        track(component, state, retained);
        state.add(component.isVisible());
        state.add(component.isEnabled());
        state.add(component.isFocusable());
        state.add(component.getName());
        state.add(component.getBounds());
        if (component instanceof JComponent swing) {
            track(accessible, state, retained);
            state.add(accessible == null ? null : accessible.getAccessibleName());
            state.add(new Identity(swing.getTransferHandler()));
        }
        if (component instanceof AbstractButton button) {
            track(button.getModel(), state, retained);
            track(button.getAction(), state, retained);
            state.add(button.getText());
            state.add(button.getActionCommand());
            identities(button.getActionListeners(), state);
            identities(button.getItemListeners(), state);
        }
        if (component instanceof JTextComponent text) {
            track(text.getDocument(), state, retained);
            track(text.getCaret(), state, retained);
            state.add(text.isEditable());
            if (text instanceof JTextField field) {
                track(field.getAction(), state, retained);
                identities(field.getActionListeners(), state);
            }
        }
        if (component instanceof JComboBox<?> combo) {
            track(combo.getModel(), state, retained);
            track(combo.getAction(), state, retained);
            state.add(new Identity(combo.getEditor()));
            state.add(new Identity(combo.getRenderer()));
            state.add(combo.isEditable());
            identities(combo.getActionListeners(), state);
            identities(combo.getItemListeners(), state);
        }
        if (component instanceof JSpinner spinner) {
            track(spinner.getModel(), state, retained);
            state.add(new Identity(spinner.getEditor()));
            identities(spinner.getChangeListeners(), state);
        }
        if (component instanceof JSlider slider) {
            track(slider.getModel(), state, retained);
            identities(slider.getChangeListeners(), state);
        }
        if (component instanceof JScrollBar scrollbar) {
            track(scrollbar.getModel(), state, retained);
            identities(scrollbar.getAdjustmentListeners(), state);
        }
        if (component instanceof JList<?> list) {
            track(list.getModel(), state, retained);
            track(list.getSelectionModel(), state, retained);
            state.add(new Identity(list.getCellRenderer()));
            identities(list.getListSelectionListeners(), state);
        }
        if (component instanceof JTable table) {
            track(table.getModel(), state, retained);
            track(table.getRowSorter(), state, retained);
            track(table.getSelectionModel(), state, retained);
            TableColumnModel columns = table.getColumnModel();
            track(columns, state, retained);
            track(columns.getSelectionModel(), state, retained);
            state.add(columns.getColumnCount());
            for (int column = 0; column < columns.getColumnCount(); column++) {
                TableColumn tableColumn = columns.getColumn(column);
                track(tableColumn, state, retained);
                if (tableColumn.getCellRenderer() == null) {
                    Class<?> columnClass = table.getColumnClass(column);
                    state.add(new Identity(columnClass));
                    // JTable's default renderer registry changes without a property event.
                    state.add(new Identity(table.getDefaultRenderer(columnClass)));
                }
            }
            state.add(new Identity(table.getCellEditor()));
            state.add(table.getEditingRow());
            state.add(table.getEditingColumn());
        }
        identities(component.getMouseListeners(), state);
        identities(component.getKeyListeners(), state);
        if (component instanceof JTabbedPane tabs) {
            track(tabs.getModel(), state, retained);
            state.add(tabs.getSelectedIndex());
            state.add(tabs.getTabCount());
            for (int index = 0; index < tabs.getTabCount(); index++) {
                state.add(tabs.getTitleAt(index));
                state.add(tabs.isEnabledAt(index));
                state.add(new Identity(tabs.getComponentAt(index)));
            }
            identities(tabs.getChangeListeners(), state);
            if (tabs.getSelectedComponent() != null) {
                captureComponent(tabs.getSelectedComponent(), false, state, retained, visited);
            }
        } else if (component instanceof JMenu menu) {
            // Published menu paths include commands whose popup has not been opened.
            state.add(menu.isPopupMenuVisible());
            for (Component child : menu.getMenuComponents()) {
                captureComponent(child, false, state, retained, visited);
            }
        } else if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                captureComponent(child, false, state, retained, visited);
            }
        }
        state.add("end-component");
    }

    private void track(Object object, List<Object> state, Map<Object, Boolean> retained) {
        state.add(new Identity(object));
        if (object != null) {
            retained.put(object, Boolean.TRUE);
            Watch watch = watches.computeIfAbsent(object, Watch::new);
            state.add(watch.revision.get());
            if (object instanceof Action action) {
                state.add(action.isEnabled());
            }
        }
    }

    private void captureBindings(JComponent component, List<Object> state,
                                 Map<Object, Boolean> retained) {
        ActionMap actions = component.getActionMap();
        state.add(new Identity(actions));
        state.add(new Identity(actions.getParent()));
        Object[] actionKeys = actions.allKeys();
        state.add(actionKeys == null ? 0 : actionKeys.length);
        if (actionKeys != null) {
            for (Object key : actionKeys) {
                state.add(new Identity(key));
                track(actions.get(key), state, retained);
            }
        }
        for (int condition : new int[]{JComponent.WHEN_FOCUSED,
                JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, JComponent.WHEN_IN_FOCUSED_WINDOW}) {
            InputMap input = component.getInputMap(condition);
            state.add(new Identity(input));
            state.add(new Identity(input.getParent()));
            KeyStroke[] keys = input.allKeys();
            state.add(keys == null ? 0 : keys.length);
            if (keys != null) {
                for (KeyStroke key : keys) {
                    state.add(key);
                    Object actionKey = input.get(key);
                    state.add(new Identity(actionKey));
                    track(actions.get(actionKey), state, retained);
                }
            }
        }
    }

    private static void identities(Object[] objects, List<Object> state) {
        state.add(objects.length);
        for (Object object : objects) {
            state.add(new Identity(object));
        }
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing observation contexts require the event-dispatch thread");
        }
    }

    /** Equality intentionally never invokes model, renderer, listener, or domain equals methods. */
    private record Identity(Object value) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Identity identity && value == identity.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    private static final class Watch {
        private final AtomicLong revision = new AtomicLong();
        private final List<Runnable> removals = new ArrayList<>();

        private Watch(Object object) {
            PropertyChangeListener property = event -> changed();
            if (object instanceof Component component) {
                component.addPropertyChangeListener(property);
                removals.add(() -> component.removePropertyChangeListener(property));
                ComponentAdapter visibility = new ComponentAdapter() {
                    @Override public void componentShown(ComponentEvent event) { changed(); }
                    @Override public void componentHidden(ComponentEvent event) { changed(); }
                };
                component.addComponentListener(visibility);
                removals.add(() -> component.removeComponentListener(visibility));
                if (component instanceof Container container) {
                    ContainerAdapter children = new ContainerAdapter() {
                        @Override public void componentAdded(ContainerEvent event) { changed(); }
                        @Override public void componentRemoved(ContainerEvent event) { changed(); }
                    };
                    container.addContainerListener(children);
                    removals.add(() -> container.removeContainerListener(children));
                }
            } else if (object instanceof AccessibleContext accessible) {
                accessible.addPropertyChangeListener(property);
                removals.add(() -> accessible.removePropertyChangeListener(property));
            } else if (object instanceof Action action) {
                action.addPropertyChangeListener(property);
                removals.add(() -> action.removePropertyChangeListener(property));
            } else if (object instanceof TableColumn column) {
                column.addPropertyChangeListener(property);
                removals.add(() -> column.removePropertyChangeListener(property));
            }
            if (object instanceof Document document) {
                DocumentListener listener = new DocumentListener() {
                    @Override public void insertUpdate(DocumentEvent event) { changed(); }
                    @Override public void removeUpdate(DocumentEvent event) { changed(); }
                    @Override public void changedUpdate(DocumentEvent event) { changed(); }
                };
                document.addDocumentListener(listener);
                removals.add(() -> document.removeDocumentListener(listener));
            }
            if (object instanceof TableModel model) {
                TableModelListener listener = event -> changed();
                model.addTableModelListener(listener);
                removals.add(() -> model.removeTableModelListener(listener));
            }
            if (object instanceof ListModel<?> model) {
                ListDataListener listener = new ListDataListener() {
                    @Override public void intervalAdded(ListDataEvent event) { changed(); }
                    @Override public void intervalRemoved(ListDataEvent event) { changed(); }
                    @Override public void contentsChanged(ListDataEvent event) { changed(); }
                };
                model.addListDataListener(listener);
                removals.add(() -> model.removeListDataListener(listener));
            }
            if (object instanceof ListSelectionModel selection) {
                ListSelectionListener listener = event -> changed();
                selection.addListSelectionListener(listener);
                removals.add(() -> selection.removeListSelectionListener(listener));
            }
            if (object instanceof RowSorter<?> sorter) {
                RowSorterListener listener = event -> changed();
                sorter.addRowSorterListener(listener);
                removals.add(() -> sorter.removeRowSorterListener(listener));
            }
            if (object instanceof TableColumnModel columns) {
                TableColumnModelListener listener = new TableColumnModelListener() {
                    @Override public void columnAdded(TableColumnModelEvent event) { changed(); }
                    @Override public void columnRemoved(TableColumnModelEvent event) { changed(); }
                    @Override public void columnMoved(TableColumnModelEvent event) { changed(); }
                    @Override public void columnMarginChanged(javax.swing.event.ChangeEvent event) { changed(); }
                    @Override public void columnSelectionChanged(javax.swing.event.ListSelectionEvent event) { changed(); }
                };
                columns.addColumnModelListener(listener);
                removals.add(() -> columns.removeColumnModelListener(listener));
            }
            ChangeListener change = event -> changed();
            if (object instanceof JViewport viewport) {
                viewport.addChangeListener(change);
                removals.add(() -> viewport.removeChangeListener(change));
            }
            if (object instanceof SpinnerModel model) {
                model.addChangeListener(change);
                removals.add(() -> model.removeChangeListener(change));
            }
            if (object instanceof BoundedRangeModel model) {
                model.addChangeListener(change);
                removals.add(() -> model.removeChangeListener(change));
            }
            if (object instanceof ButtonModel model) {
                model.addChangeListener(change);
                removals.add(() -> model.removeChangeListener(change));
            }
            if (object instanceof SingleSelectionModel model) {
                model.addChangeListener(change);
                removals.add(() -> model.removeChangeListener(change));
            }
            if (object instanceof Caret caret) {
                caret.addChangeListener(change);
                removals.add(() -> caret.removeChangeListener(change));
            }
        }

        private void changed() {
            revision.incrementAndGet();
        }

        private void detach() {
            removals.forEach(Runnable::run);
            removals.clear();
        }
    }
}
