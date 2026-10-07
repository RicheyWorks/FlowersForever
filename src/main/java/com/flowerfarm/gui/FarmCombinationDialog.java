package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.model.FarmWorkspace;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** A draft-only picker for an operation that uses more than one farm workspace. */
final class FarmCombinationDialog {
    private static final DataFlavor WORKSPACE_FLAVOR =
            new DataFlavor(FarmWorkspace.class, "Farm type");
    private final FarmDialogs dialogs;

    FarmCombinationDialog(FarmDialogs dialogs) {
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
    }

    /** Returns a validated selection only after the owner chooses Save. */
    Selection show(Component owner, FarmWorkspace currentPrimary,
                   Set<FarmWorkspace> currentIncluded, FarmRole role) {
        Objects.requireNonNull(role, "role");
        boolean editable = role == FarmRole.OWNER;
        Form form = new Form(currentPrimary, currentIncluded, editable);
        JScrollPane scroll = form.dialogScroll(owner);
        Object[] options = editable
                ? new Object[]{"Save combination", "Cancel"} : new Object[]{"Close"};
        while (true) {
            int choice = dialogs.showOption(owner, scroll, "Combine farm types",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, options, options[0]);
            if (!editable || choice != 0) {
                return null;
            }
            try {
                return form.selection();
            } catch (IllegalArgumentException exception) {
                form.showError(exception.getMessage());
            }
        }
    }

    /** Immutable save request; persistence remains with the caller. */
    record Selection(FarmWorkspace primary, Set<FarmWorkspace> included) {
        Selection {
            Objects.requireNonNull(primary, "Primary farm type is required.");
            Objects.requireNonNull(included, "Included farm types are required.");
            LinkedHashSet<FarmWorkspace> copy = new LinkedHashSet<>(included);
            if (copy.contains(null)) {
                throw new IllegalArgumentException("Farm types cannot be blank.");
            }
            if (copy.isEmpty()) {
                throw new IllegalArgumentException(
                        "Choose at least one farm type.");
            }
            if (!copy.contains(primary)) {
                throw new IllegalArgumentException(
                        "Choose a main farm type from the selected types.");
            }
            included = Collections.unmodifiableSet(copy);
        }
    }

    static final class Form extends JPanel {
        private final boolean editingExistingMix;
        private final Map<FarmWorkspace, JCheckBox> checkboxes =
                new EnumMap<>(FarmWorkspace.class);
        private final Map<FarmWorkspace, JPanel> farmCards =
                new EnumMap<>(FarmWorkspace.class);
        private final JComboBox<FarmWorkspace> primary = new JComboBox<>();
        private final JTextArea preview = new JTextArea(10, 48);
        private final JTextArea mixSummary = new JTextArea(3, 18);
        private final JLabel selectedCount = FarmUi.statusBadge("", FarmUi.Tone.INFO);
        private final JLabel dragFeedback = FarmUi.wrappingHint("");
        private final JLabel error = FarmUi.wrappingHint("");

        /** Fits the modal viewport so only farm cards scroll, keeping drop targets visible. */
        JScrollPane dialogScroll(Component owner) {
            JScrollPane scroll = FarmUi.dialogScroll(owner, this, new Dimension(850, 660));
            Dimension viewport = scroll.getPreferredSize();
            setPreferredSize(new Dimension(viewport.width, viewport.height - 12));
            return scroll;
        }

        Form(FarmWorkspace currentPrimary, Set<FarmWorkspace> currentIncluded,
             boolean editable) {
            super();
            Objects.requireNonNull(currentIncluded, "currentIncluded");
            editingExistingMix = currentIncluded.size() > 1;
            setName("farmCombination.form");
            setOpaque(false);
            setLayout(new BorderLayout(0, 8));
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel fixed = new JPanel();
            fixed.setName("farmCombination.fixedDropBoard");
            fixed.setOpaque(false);
            fixed.setLayout(new BoxLayout(fixed, BoxLayout.Y_AXIS));

            JLabel title = new JLabel("Make it your farm");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            fixed.add(title);
            fixed.add(Box.createVerticalStrut(3));
            JLabel introduction = FarmUi.wrappingHint(
                    editable
                            ? "Drag cards into Your mix or Main focus, or use checkboxes. "
                                    + "Save to apply; records stay saved."
                            : "Review the selected farm tools. An owner can edit this mix.");
            introduction.setAlignmentX(Component.LEFT_ALIGNMENT);
            fixed.add(introduction);
            error.setName("farmCombination.error");
            error.setAlignmentX(Component.LEFT_ALIGNMENT);
            error.setVisible(false);
            fixed.add(Box.createVerticalStrut(3));
            fixed.add(error);
            fixed.add(Box.createVerticalStrut(8));

            configurePrimary(editable);
            JPanel dropBoard = new JPanel(new GridLayout(1, 3, 10, 0));
            dropBoard.setName("farmCombination.dropBoard");
            dropBoard.setOpaque(false);
            dropBoard.add(mixDropCard(editable));
            dropBoard.add(mainDropCard(editable));
            dropBoard.add(removeDropCard(editable));
            dropBoard.setAlignmentX(Component.LEFT_ALIGNMENT);
            fixed.add(dropBoard);
            dragFeedback.setName("farmCombination.dragFeedback");
            dragFeedback.setAlignmentX(Component.LEFT_ALIGNMENT);
            dragFeedback.setVisible(false);
            fixed.add(dragFeedback);
            fixed.add(Box.createVerticalStrut(6));
            JTextField search = new JTextField(22);
            search.setName("farmCombination.search");
            search.putClientProperty("JTextField.placeholderText",
                    "Try marijuana, wedding, or orchard");
            search.getAccessibleContext().setAccessibleName("Find a farm type");
            JLabel searchLabel = new JLabel("Find a farm type");
            searchLabel.setLabelFor(search);
            JLabel matchCount = FarmUi.mutedLabel("");
            matchCount.setName("farmCombination.matchCount");
            JButton clearSearch = FarmUi.secondaryButton("Clear search");
            clearSearch.setName("farmCombination.clearSearch");
            clearSearch.setEnabled(false);
            clearSearch.addActionListener(event -> {
                search.setText("");
                search.requestFocusInWindow();
            });
            JPanel searchActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            searchActions.setOpaque(false);
            searchActions.add(matchCount);
            searchActions.add(clearSearch);
            JPanel searchRow = new JPanel(new BorderLayout(10, 0));
            searchRow.setOpaque(false);
            searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            searchRow.add(searchLabel, BorderLayout.WEST);
            searchRow.add(search, BorderLayout.CENTER);
            searchRow.add(searchActions, BorderLayout.EAST);
            fixed.add(searchRow);
            add(fixed, BorderLayout.NORTH);

            CardScrollContent scrollContent = new CardScrollContent();

            JPanel cards = new JPanel(new GridLayout(0, 2, 12, 12));
            cards.setName("farmCombination.cards");
            cards.setOpaque(false);
            for (FarmWorkspace workspace : FarmWorkspace.values()) {
                JCheckBox box = new JCheckBox(FarmSetupDialog.workspaceLabel(workspace),
                        currentIncluded.contains(workspace));
                box.setName("farmCombination.include." + workspace.name());
                box.setOpaque(false);
                box.setEnabled(editable);
                box.getAccessibleContext().setAccessibleName(
                        "Include " + FarmSetupDialog.workspaceLabel(workspace));
                box.getAccessibleContext().setAccessibleDescription(scope(workspace));
                box.addActionListener(event -> updateSelection());
                checkboxes.put(workspace, box);

                JPanel cardContent = new JPanel(new BorderLayout(0, 4));
                cardContent.setOpaque(false);
                cardContent.add(box, BorderLayout.NORTH);
                JLabel scope = FarmUi.wrappingHint(scope(workspace));
                cardContent.add(scope, BorderLayout.CENTER);
                JLabel dragHandle = new JLabel("↗ Drag this card");
                dragHandle.setName("farmCombination.drag." + workspace.name());
                dragHandle.setForeground(FarmUi.accentColor());
                dragHandle.getAccessibleContext().setAccessibleName(
                        "Drag " + FarmSetupDialog.workspaceLabel(workspace));
                dragHandle.getAccessibleContext().setAccessibleDescription(
                        "Drop on Your mix to add, Main focus to make primary, "
                                + "or Remove from mix to remove.");
                dragHandle.setEnabled(editable);
                if (editable) {
                    enableDrag(dragHandle, workspace);
                }
                cardContent.add(dragHandle, BorderLayout.SOUTH);
                JPanel card = FarmUi.section(category(workspace), null, cardContent);
                card.setName("farmCombination.card." + workspace.name());
                if (editable) {
                    enableDrag(card, workspace);
                }
                farmCards.put(workspace, card);
                cards.add(card);
            }
            CardLayout cardResultLayout = new CardLayout();
            JPanel cardResults = new JPanel(cardResultLayout);
            cardResults.setOpaque(false);
            cardResults.add(cards, "matches");
            JPanel empty = new JPanel(new BorderLayout());
            empty.setName("farmCombination.empty");
            empty.setOpaque(false);
            empty.add(FarmUi.wrappingHint(
                    "No farm types match. Try a crop, product, or farm type."),
                    BorderLayout.NORTH);
            cardResults.add(empty, "empty");
            scrollContent.addRow(cardResults, 16);

            preview.setName("farmCombination.preview");
            preview.getAccessibleContext().setAccessibleName(
                    "Included farm tools and current scope");
            preview.setEditable(false);
            preview.setFocusable(false);
            preview.setOpaque(false);
            preview.setLineWrap(true);
            preview.setWrapStyleWord(true);
            scrollContent.addRow(FarmUi.section("Your combined workspace",
                    "Shared records appear once; selected farm tools appear alongside them.",
                    preview), 10);
            JLabel customHint = FarmUi.wrappingHint(
                    "Need another crop, breed, or product? Add it in Farm setup under "
                            + "Specialties. Add Custom farm to this mix for an unlisted "
                            + "business line; it uses shared records without a separate planning tool.");
            scrollContent.addRow(customHint, 0);

            JScrollPane cardScroll = new JScrollPane(scrollContent,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            cardScroll.setName("farmCombination.cardScroll");
            cardScroll.setBorder(BorderFactory.createEmptyBorder());
            cardScroll.setOpaque(false);
            cardScroll.getViewport().setOpaque(false);
            cardScroll.getVerticalScrollBar().setUnitIncrement(24);
            cardScroll.getAccessibleContext().setAccessibleName(
                    "Farm type cards and combined workspace preview");
            add(cardScroll, BorderLayout.CENTER);
            search.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) {
                    filterCards(search.getText(), cards, cardResults, cardResultLayout,
                            cardScroll, matchCount, clearSearch);
                }

                @Override public void removeUpdate(DocumentEvent event) {
                    filterCards(search.getText(), cards, cardResults, cardResultLayout,
                            cardScroll, matchCount, clearSearch);
                }

                @Override public void changedUpdate(DocumentEvent event) {
                    filterCards(search.getText(), cards, cardResults, cardResultLayout,
                            cardScroll, matchCount, clearSearch);
                }
            });
            filterCards("", cards, cardResults, cardResultLayout, cardScroll,
                    matchCount, clearSearch);

            updateSelection(currentPrimary);
        }

        private void filterCards(String query, JPanel cards, JPanel cardResults,
                CardLayout resultLayout, JScrollPane cardScroll, JLabel matchCount,
                JButton clearSearch) {
            String normalized = query.trim().toLowerCase(Locale.ROOT);
            String[] terms = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
            cards.removeAll();
            int matches = 0;
            for (FarmWorkspace workspace : FarmWorkspace.values()) {
                String searchable = (FarmSetupDialog.workspaceLabel(workspace) + " "
                        + category(workspace) + " " + scope(workspace) + " "
                        + tools(workspace) + " " + FarmTypeLauncher.searchAliases(workspace))
                        .toLowerCase(Locale.ROOT);
                boolean visible = true;
                for (String term : terms) {
                    if (!searchable.contains(term)) {
                        visible = false;
                        break;
                    }
                }
                if (visible) {
                    cards.add(farmCards.get(workspace));
                    matches++;
                }
            }
            cards.setLayout(new GridLayout(0, matches == 1 ? 1 : 2, 12, 12));
            matchCount.setText(matches + (matches == 1 ? " farm type" : " farm types"));
            clearSearch.setEnabled(!query.isEmpty());
            resultLayout.show(cardResults, matches == 0 ? "empty" : "matches");
            cards.revalidate();
            cardResults.revalidate();
            cardScroll.getVerticalScrollBar().setValue(0);
            cardResults.repaint();
        }

        private void configurePrimary(boolean editable) {
            primary.setName("farmCombination.primary");
            primary.getAccessibleContext().setAccessibleName("Main farm type");
            primary.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list,
                        Object value, int index, boolean isSelected, boolean hasFocus) {
                    super.getListCellRendererComponent(
                            list, value, index, isSelected, hasFocus);
                    if (value instanceof FarmWorkspace workspace) {
                        setText(FarmSetupDialog.workspaceLabel(workspace));
                    }
                    return this;
                }
            });
            primary.setEnabled(editable);
            primary.addActionListener(event -> updatePreview());
        }

        private JPanel mixDropCard(boolean editable) {
            mixSummary.setName("farmCombination.mixSummary");
            mixSummary.setEditable(false);
            mixSummary.setFocusable(false);
            mixSummary.setOpaque(false);
            mixSummary.setLineWrap(true);
            mixSummary.setWrapStyleWord(true);
            mixSummary.getAccessibleContext().setAccessibleName("Farm types in your mix");
            JPanel drop = dropTarget("farmCombination.mixDrop",
                    "Drop a card here to add it", this::addToMix, editable);
            drop.add(mixSummary, BorderLayout.CENTER);
            if (editable) {
                mixSummary.setTransferHandler(drop.getTransferHandler());
            }
            JPanel content = new JPanel(new BorderLayout(0, 6));
            content.setOpaque(false);
            content.add(drop, BorderLayout.CENTER);
            content.add(selectedCount, BorderLayout.SOUTH);
            return FarmUi.section("Your mix", "Selected tools stay together.", content);
        }

        private JPanel mainDropCard(boolean editable) {
            JPanel drop = dropTarget("farmCombination.mainDrop",
                    "Drop a card here to make it main", this::makeMain, editable);
            JLabel primaryLabel = new JLabel("Main farm type");
            primaryLabel.setLabelFor(primary);
            JPanel chooser = new JPanel(new BorderLayout(0, 3));
            chooser.setOpaque(false);
            chooser.add(primaryLabel, BorderLayout.NORTH);
            chooser.add(primary, BorderLayout.CENTER);
            JPanel content = new JPanel(new BorderLayout(0, 6));
            content.setOpaque(false);
            content.add(drop, BorderLayout.CENTER);
            content.add(chooser, BorderLayout.SOUTH);
            return FarmUi.section("Main focus", "Sets shared defaults.", content);
        }

        private JPanel removeDropCard(boolean editable) {
            JPanel drop = dropTarget("farmCombination.removeDrop",
                    "Drop a card here to remove it", this::removeFromMix, editable);
            JLabel hint = FarmUi.wrappingHint("Or uncheck its card below.");
            JPanel content = new JPanel(new BorderLayout(0, 6));
            content.setOpaque(false);
            content.add(drop, BorderLayout.CENTER);
            content.add(hint, BorderLayout.SOUTH);
            return FarmUi.section("Remove from mix", "Records stay saved.", content);
        }

        private JPanel dropTarget(String name, String caption,
                                  Consumer<FarmWorkspace> action, boolean editable) {
            JPanel target = new JPanel(new BorderLayout());
            target.setName(name);
            target.setOpaque(false);
            target.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createDashedBorder(FarmUi.accentColor(), 1f, 3f),
                    BorderFactory.createEmptyBorder(8, 8, 8, 8)));
            target.getAccessibleContext().setAccessibleName(caption);
            target.getAccessibleContext().setAccessibleDescription(
                    editable ? caption + ". Changes are saved only after Save combination."
                            : "Only an owner can edit this combination.");
            JLabel instruction = FarmUi.wrappingHint(caption);
            instruction.setName(name + ".hint");
            target.add(instruction, BorderLayout.NORTH);
            if (editable) {
                TransferHandler handler = new WorkspaceDropHandler(action);
                target.setTransferHandler(handler);
                instruction.setTransferHandler(handler);
            }
            return target;
        }

        private void addToMix(FarmWorkspace workspace) {
            JCheckBox box = checkboxes.get(workspace);
            if (box.isSelected()) {
                showDragFeedback(FarmSetupDialog.workspaceLabel(workspace)
                        + " is already in your mix.");
                return;
            }
            box.setSelected(true);
            updateSelection();
            showDragFeedback("Added " + FarmSetupDialog.workspaceLabel(workspace)
                    + " to your draft mix.");
        }

        private void makeMain(FarmWorkspace workspace) {
            checkboxes.get(workspace).setSelected(true);
            updateSelection(workspace);
            showDragFeedback(FarmSetupDialog.workspaceLabel(workspace)
                    + " is now the main farm type in your draft mix.");
        }

        private void removeFromMix(FarmWorkspace workspace) {
            JCheckBox box = checkboxes.get(workspace);
            if (!box.isSelected()) {
                showDragFeedback(FarmSetupDialog.workspaceLabel(workspace)
                        + " is not in your mix.");
                return;
            }
            box.setSelected(false);
            updateSelection();
            showDragFeedback("Removed " + FarmSetupDialog.workspaceLabel(workspace)
                    + " from your draft mix. Its records remain available.");
        }

        private void showDragFeedback(String message) {
            dragFeedback.setText(message);
            dragFeedback.setVisible(true);
            revalidate();
        }

        Selection selection() {
            Set<FarmWorkspace> included = selected();
            FarmWorkspace chosen = (FarmWorkspace) primary.getSelectedItem();
            if (included.isEmpty()) {
                throw new IllegalArgumentException(
                        "Choose at least one farm type.");
            }
            if (included.size() == 1 && !editingExistingMix) {
                throw new IllegalArgumentException(
                        "Choose at least two farm types to make a combination.");
            }
            return new Selection(chosen, included);
        }

        void showError(String message) {
            error.setText(message);
            error.setVisible(true);
            revalidate();
        }

        private void updateSelection() {
            updateSelection((FarmWorkspace) primary.getSelectedItem());
        }

        private void updateSelection(FarmWorkspace preferred) {
            Set<FarmWorkspace> selected = selected();
            primary.removeAllItems();
            for (FarmWorkspace workspace : selected) {
                primary.addItem(workspace);
            }
            if (selected.contains(preferred)) {
                primary.setSelectedItem(preferred);
            }
            selectedCount.setText(selected.size() + " selected");
            mixSummary.setText(selected.isEmpty() ? "No farm types selected yet."
                    : selected.stream().map(FarmSetupDialog::workspaceLabel)
                            .collect(Collectors.joining(" + ")));
            mixSummary.setCaretPosition(0);
            error.setVisible(false);
            updatePreview();
        }

        private Set<FarmWorkspace> selected() {
            LinkedHashSet<FarmWorkspace> selected = new LinkedHashSet<>();
            for (FarmWorkspace workspace : FarmWorkspace.values()) {
                JCheckBox box = checkboxes.get(workspace);
                if (box != null && box.isSelected()) {
                    selected.add(workspace);
                }
            }
            return selected;
        }

        private void updatePreview() {
            Set<FarmWorkspace> selected = selected();
            if (selected.isEmpty()) {
                preview.setText("Choose farm types to build your workspace.\n\n"
                        + "Shared records: Inventory, Customers & Orders, Knowledge Log, "
                        + "Reports, and Activity History.");
                return;
            }
            String names = selected.stream()
                    .map(FarmSetupDialog::workspaceLabel)
                    .collect(Collectors.joining(" + "));
            String tools = selected.stream()
                    .map(FarmCombinationDialog::tools)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .collect(Collectors.joining("; "));
            FarmWorkspace chosen = (FarmWorkspace) primary.getSelectedItem();
            String start = chosen == null ? "Choose a main farm type"
                    : "Main farm type: " + FarmSetupDialog.workspaceLabel(chosen);
            String selectedTools = tools.isBlank() ? "none" : tools;
            String customScope = selected.contains(FarmWorkspace.CUSTOM_FARM)
                    ? "Custom farm uses these shared records and the Harvest Log "
                            + "for unlisted business lines; it adds no crop-specific "
                            + "planning tool.\n\n"
                    : "";
            preview.setText(names + "\n" + start + "\n\n"
                    + "Shared: Inventory, Customers & Orders, Knowledge Log, "
                    + "Reports, Activity History.\n"
                    + "Selected tools: " + selectedTools + ".\n\n"
                    + customScope
                    + "Planning notebooks remain separate from harvest, stock, and orders. "
                    + "Cannabis reporting and crop-specific packout are not included.");
            preview.setCaretPosition(0);
        }
    }

    /** Keeps the two-column card grid within the modal width without horizontal scrolling. */
    private static final class CardScrollContent extends JPanel implements Scrollable {
        private int nextRow;

        private CardScrollContent() {
            setOpaque(false);
            setLayout(new GridBagLayout());
        }

        private void addRow(Component content, int bottomGap) {
            GridBagConstraints row = new GridBagConstraints();
            row.gridx = 0;
            row.gridy = nextRow++;
            row.weightx = 1;
            row.fill = GridBagConstraints.HORIZONTAL;
            row.anchor = GridBagConstraints.FIRST_LINE_START;
            row.insets = new Insets(0, 0, bottomGap, 0);
            add(content, row);
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public int getScrollableUnitIncrement(Rectangle visible,
                int orientation, int direction) {
            return 24;
        }

        @Override public int getScrollableBlockIncrement(Rectangle visible,
                int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private static void enableDrag(JComponent source, FarmWorkspace workspace) {
        source.setTransferHandler(new WorkspaceDragHandler(workspace));
        source.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        MouseAdapter gesture = new MouseAdapter() {
            private java.awt.Point start;
            private boolean exported;

            @Override public void mousePressed(MouseEvent event) {
                start = event.getPoint();
                exported = false;
            }

            @Override public void mouseReleased(MouseEvent event) {
                start = null;
                exported = false;
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (start != null && !exported
                        && (event.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0
                        && start.distance(event.getPoint()) >= 4) {
                    exported = true;
                    source.getTransferHandler().exportAsDrag(
                            source, event, TransferHandler.COPY);
                }
            }
        };
        source.addMouseListener(gesture);
        source.addMouseMotionListener(gesture);
    }

    private static final class WorkspaceDragHandler extends TransferHandler {
        private final FarmWorkspace workspace;

        private WorkspaceDragHandler(FarmWorkspace workspace) {
            this.workspace = workspace;
        }

        @Override public int getSourceActions(JComponent component) {
            return COPY;
        }

        @Override protected Transferable createTransferable(JComponent component) {
            return new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{WORKSPACE_FLAVOR};
                }

                @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return WORKSPACE_FLAVOR.equals(flavor);
                }

                @Override public Object getTransferData(DataFlavor flavor)
                        throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(flavor)) {
                        throw new UnsupportedFlavorException(flavor);
                    }
                    return workspace;
                }
            };
        }
    }

    private static final class WorkspaceDropHandler extends TransferHandler {
        private final Consumer<FarmWorkspace> action;

        private WorkspaceDropHandler(Consumer<FarmWorkspace> action) {
            this.action = action;
        }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(WORKSPACE_FLAVOR);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                Object value = support.getTransferable().getTransferData(WORKSPACE_FLAVOR);
                if (value instanceof FarmWorkspace workspace) {
                    action.accept(workspace);
                    return true;
                }
            } catch (UnsupportedFlavorException | IOException exception) {
                return false;
            }
            return false;
        }
    }

    private static String category(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> "01 / GROW + DESIGN";
            case WILDFLOWER_FARM -> "02 / FIELD FLOWERS";
            case FLOWER_SHOP -> "03 / FLORAL RETAIL";
            case CANNABIS_FARM -> "04 / CULTIVATION";
            case POTATO_FARM -> "05 / FIELD CROPS";
            case APPLE_ORCHARD -> "06 / ORCHARDS";
            case BERRY_FARM -> "07 / BERRIES";
            case NURSERY_FARM -> "08 / LIVE PLANTS";
            case LIVESTOCK_FARM -> "09 / HERD GROUPS";
            case POULTRY_FARM -> "10 / EGGS + FLOCKS";
            case DAIRY_FARM -> "11 / MILK COLLECTIONS";
            case VINEYARD_FARM -> "12 / VINEYARD BLOCKS";
            case HAY_FARM -> "13 / HAY CUTTINGS";
            case HOPS_FARM -> "14 / HOP YARDS";
            case GRAIN_FARM -> "15 / GRAIN FIELDS";
            case SEED_FARM -> "16 / SEED FIELDS";
            case NUT_ORCHARD -> "17 / NUT BLOCKS";
            case VEGETABLE_FARM -> "18 / VEGETABLE PLANTINGS";
            case AQUACULTURE_FARM -> "19 / AQUATIC COHORTS";
            case CHRISTMAS_TREE_FARM -> "20 / TREE BLOCKS";
            case APIARY_FARM -> "21 / HIVE CHECKS";
            case MUSHROOM_FARM -> "22 / MUSHROOM BATCHES";
            case CRANBERRY_FARM -> "23 / CRANBERRY BEDS";
            case PUMPKIN_PATCH -> "24 / PUMPKIN PATCHES";
            case CUSTOM_FARM -> "OTHER FARMS";
        };
    }

    private static String scope(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> "Roses, bouquets, weddings, and season planning";
            case WILDFLOWER_FARM -> "Field flowers, harvests, markets, and season planning";
            case FLOWER_SHOP -> "Shop stock, orders, events, and floral design";
            case CANNABIS_FARM -> "Cultivation batch plans; no compliance reporting";
            case POTATO_FARM -> "Potato field plans; no grading or packout";
            case APPLE_ORCHARD -> "Apple block plans; no bin or cold-storage ledger";
            case BERRY_FARM -> "Berry block plans; no U-pick booking or packout";
            case NURSERY_FARM -> "Growing batch plans; no licensing or inspections";
            case LIVESTOCK_FARM -> "Herd group plans; no animal health or individual records";
            case POULTRY_FARM -> "Egg collection log; stock and orders stay separate";
            case DAIRY_FARM -> "Milking session log; no stock, quality, or delivery link";
            case VINEYARD_FARM -> "Grape block plans; no crush, stock, or delivery link";
            case HAY_FARM -> "Repeat-cut plans; no bale inventory or feed-ration link";
            case HOPS_FARM -> "Hop yard plans; no drying, stock, or contract link";
            case GRAIN_FARM -> "Field rotation plans; no yield, storage, or sales link";
            case SEED_FARM -> "Seed field plans; no isolation calculation or seed lot link";
            case NUT_ORCHARD -> "Nut block plans; no pollinizer check, yield, or sales link";
            case VEGETABLE_FARM -> "Vegetable planting plans; no yield, packout, or sales link";
            case AQUACULTURE_FARM -> "Aquatic cohort plans; no harvest clearance, traceability, or sales link";
            case CHRISTMAS_TREE_FARM -> "Tree block plans; no cut counts, bookings, stock, or export clearance";
            case APIARY_FARM -> "Hive check log; no diagnosis, registration, pollination contracts, or honey stock";
            case MUSHROOM_FARM -> "Batch plans; no fruiting forecast, harvest yield, climate control, or stock posting";
            case CRANBERRY_FARM -> "Bed plans; no actual harvest, flood scheduling, yield, packout, or stock posting";
            case PUMPKIN_PATCH -> "Patch plans; no admission, bookings, completed harvest, or stock posting";
            case CUSTOM_FARM -> "Shared farm records for unlisted lines; no separate planning tool";
        };
    }

    private static String tools(FarmWorkspace workspace) {
        return switch (workspace) {
            case ROSE_AND_EVENTS -> "Dashboard, Market Day, Season Planner, Irrigation & Care";
            case WILDFLOWER_FARM -> "Market Day, Season Planner";
            case FLOWER_SHOP -> "Event Services, Production Studio";
            case CANNABIS_FARM -> "Cultivation";
            case POTATO_FARM -> "Field plans";
            case APPLE_ORCHARD -> "Orchard blocks";
            case BERRY_FARM -> "Berry blocks";
            case NURSERY_FARM -> "Growing batches";
            case LIVESTOCK_FARM -> "Herd groups";
            case POULTRY_FARM -> "Egg collections";
            case DAIRY_FARM -> "Milk collections";
            case VINEYARD_FARM -> "Vineyard blocks";
            case HAY_FARM -> "Hay cuttings";
            case HOPS_FARM -> "Hop yards";
            case GRAIN_FARM -> "Grain fields";
            case SEED_FARM -> "Seed fields";
            case NUT_ORCHARD -> "Nut blocks";
            case VEGETABLE_FARM -> "Vegetable plantings";
            case AQUACULTURE_FARM -> "Aquatic cohorts";
            case CHRISTMAS_TREE_FARM -> "Tree blocks";
            case APIARY_FARM -> "Hive checks";
            case MUSHROOM_FARM -> "Mushroom batches";
            case CRANBERRY_FARM -> "Cranberry beds";
            case PUMPKIN_PATCH -> "Pumpkin patches";
            case CUSTOM_FARM -> "";
        };
    }
}
