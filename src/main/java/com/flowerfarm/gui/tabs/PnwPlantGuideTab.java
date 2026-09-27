package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.PnwPlantProfile;
import com.flowerfarm.model.PnwPlantProfile.Source;
import com.flowerfarm.reference.PnwPlantCatalog;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.Scrollable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;

/** Searchable, sourced Pacific Northwest plant reference. */
public final class PnwPlantGuideTab implements FlowerFarmTab {

    private final TabHost host;
    private final List<PnwPlantProfile> plants;
    private JPanel panel;
    private JButton planButton;
    private JLabel planningStatus;
    private boolean canWrite = true;

    public PnwPlantGuideTab() {
        this(null, PnwPlantCatalog.all());
    }

    public PnwPlantGuideTab(TabHost host) {
        this(host, PnwPlantCatalog.all());
    }

    PnwPlantGuideTab(List<PnwPlantProfile> plants) {
        this(null, plants);
    }

    PnwPlantGuideTab(TabHost host, List<PnwPlantProfile> plants) {
        this.host = host;
        this.plants = List.copyOf(plants);
    }

    @Override
    public String getTabTitle() {
        return "PNW Plant Guide";
    }

    @Override
    public String getDescription() {
        return "Sourced Pacific Northwest plants, crops, cut flowers, and field guidance";
    }

    @Override
    public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    @Override
    public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        if (planButton != null) {
            planButton.setEnabled(canWrite && host != null);
            planButton.setToolTipText(canWrite
                    ? "Open an editable Season Planner draft from the selected sourced profile."
                    : "Viewer access can research plants but cannot prepare planting drafts.");
        }
    }

    private void buildUi() {
        panel = FarmUi.page();
        JLabel coverage = FarmUi.statusBadge(
                plants.size() + " profiles · " + PnwPlantCatalog.sources().size()
                        + " public sources", FarmUi.Tone.SUCCESS);
        coverage.setName("plant.coverage");
        coverage.getAccessibleContext().setAccessibleName("Plant guide coverage");
        panel.add(FarmUi.pageHeader(
                "Pacific Northwest plant guide",
                "Washington and Oregon field notes with university and Master Gardener provenance.",
                coverage), BorderLayout.NORTH);

        PlantTableModel model = new PlantTableModel(plants);
        JTable table = new JTable(model);
        table.setName("plant.guide.table");
        FarmUi.configureTable(table, "Pacific Northwest plant guide table");
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableRowSorter<PlantTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(145);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(125);
        table.getColumnModel().getColumn(5).setPreferredWidth(150);

        JTextField search = FarmUi.searchField(
                "Plant guide search", "Search name, use, season, notes, or source", 22);
        search.setName("plant.search");
        FarmUi.initialFocus(search);
        JComboBox<String> type = new JComboBox<>();
        type.setName("plant.typeFilter");
        type.addItem("All plant types");
        PnwPlantCatalog.types().forEach(type::addItem);
        type.getAccessibleContext().setAccessibleName("Plant type filter");
        JComboBox<String> origin = new JComboBox<>(new String[]{
                "All origins", "PNW native", "Garden crop / adapted"});
        origin.setName("plant.originFilter");
        origin.getAccessibleContext().setAccessibleName("Plant origin filter");
        JComboBox<String> light = new JComboBox<>(new String[]{
                "All light", "Full sun", "Sun / part shade", "Part shade / shade"});
        light.setName("plant.lightFilter");
        light.getAccessibleContext().setAccessibleName("Plant light filter");
        JLabel results = FarmUi.statusBadge(plants.size() + " matches", FarmUi.Tone.INFO);
        results.setName("plant.results");

        Runnable applyFilters = () -> {
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            String selectedType = String.valueOf(type.getSelectedItem());
            String selectedOrigin = String.valueOf(origin.getSelectedItem());
            String selectedLight = String.valueOf(light.getSelectedItem());
            sorter.setRowFilter(new RowFilter<>() {
                @Override
                public boolean include(Entry<? extends PlantTableModel, ? extends Integer> entry) {
                    PnwPlantProfile plant = model.plantAt(entry.getIdentifier());
                    return (query.isBlank() || plant.searchableText().contains(query))
                            && (selectedType.startsWith("All")
                            || plant.type().equals(selectedType))
                            && (selectedOrigin.startsWith("All")
                            || plant.originLabel().equals(selectedOrigin))
                            && (selectedLight.startsWith("All")
                            || plant.light().equals(selectedLight));
                }
            });
            FarmUi.updateStatusBadge(results, table.getRowCount() + " matches", FarmUi.Tone.INFO);
            if (table.getRowCount() > 0) {
                table.setRowSelectionInterval(0, 0);
            } else {
                table.clearSelection();
            }
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { applyFilters.run(); }
            @Override public void removeUpdate(DocumentEvent event) { applyFilters.run(); }
            @Override public void changedUpdate(DocumentEvent event) { applyFilters.run(); }
        });
        type.addActionListener(event -> applyFilters.run());
        origin.addActionListener(event -> applyFilters.run());
        light.addActionListener(event -> applyFilters.run());
        JButton clear = FarmUi.secondaryButton("Clear plant filters");
        clear.setName("plant.clearFilters");
        clear.addActionListener(event -> {
            search.setText("");
            type.setSelectedIndex(0);
            origin.setSelectedIndex(0);
            light.setSelectedIndex(0);
        });
        planButton = FarmUi.primaryButton("Plan selected crop");
        planButton.setName("plant.planSelected");
        planningStatus = FarmUi.statusBadge(
                "Choose a profile to start a planting draft", FarmUi.Tone.NEUTRAL);
        planningStatus.setName("plant.planStatus");
        planButton.addActionListener(event -> {
            PnwPlantProfile selected = selectedPlant(table, model);
            if (selected == null) {
                FarmUi.updateStatusBadge(planningStatus,
                        "Select one plant profile first", FarmUi.Tone.WARNING);
                return;
            }
            if (host == null) {
                FarmUi.updateStatusBadge(planningStatus,
                        "Season Planner handoff is unavailable in this view",
                        FarmUi.Tone.WARNING);
                return;
            }
            boolean accepted = host.showSeasonPlanDraft(selected);
            FarmUi.updateStatusBadge(planningStatus,
                    accepted ? "Planting block accepted in Season Planner"
                            : "Season Planner opened · no planting block saved",
                    accepted ? FarmUi.Tone.SUCCESS : FarmUi.Tone.INFO);
        });
        JPanel filters = new JPanel();
        filters.setName("plant.filters");
        filters.setOpaque(false);
        filters.setLayout(new BoxLayout(filters, BoxLayout.Y_AXIS));
        JPanel searchRow = FarmUi.toolbar(
                new JLabel("Search:"), search,
                new JLabel("Type:"), type, results);
        searchRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JPanel siteRow = FarmUi.toolbar(
                new JLabel("Origin:"), origin,
                new JLabel("Light:"), light);
        siteRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        filters.add(searchRow);
        filters.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filters.add(siteRow);
        JPanel planningRow = FarmUi.toolbar(clear, planButton, planningStatus);
        planningRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        filters.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        filters.add(planningRow);

        JTextArea details = FarmUi.displayArea(14, 34);
        details.setName("plant.profile.details");
        details.getAccessibleContext().setAccessibleName("Selected plant profile and source");
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setName("plant.profile.scroll");
        detailScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));
        detailScroll.setMinimumSize(new Dimension(0, 140));
        detailScroll.getVerticalScrollBar().setUnitIncrement(20);
        detailScroll.getAccessibleContext().setAccessibleName("Selected plant profile scroll area");
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelection(table, model, details);
            }
        });

        JComboBox<Source> sources = new JComboBox<>(
                PnwPlantCatalog.sources().toArray(Source[]::new));
        sources.setName("plant.sourceSelector");
        int sourceControlHeight = sources.getPreferredSize().height;
        sources.setPreferredSize(new Dimension(330, sourceControlHeight));
        sources.setMinimumSize(new Dimension(220, sourceControlHeight));
        sources.getAccessibleContext().setAccessibleName("Plant research source selector");
        JTextArea sourceDetails = FarmUi.displayArea(4, 34);
        sourceDetails.setName("plant.source.details");
        sourceDetails.getAccessibleContext().setAccessibleName("Selected research source URL");
        sources.addActionListener(event -> {
            Source selected = (Source) sources.getSelectedItem();
            showSource(selected, sourceDetails);
            sources.setToolTipText(selected == null ? null : selected.label());
        });
        if (sources.getItemCount() > 0) {
            sources.setSelectedIndex(0);
            showSource((Source) sources.getSelectedItem(), sourceDetails);
            sources.setToolTipText(((Source) sources.getSelectedItem()).label());
        }
        JScrollPane sourceScroll = new JScrollPane(sourceDetails);
        sourceScroll.setName("plant.source.scroll");
        sourceScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));
        sourceScroll.setMinimumSize(new Dimension(0, 55));
        sourceScroll.getVerticalScrollBar().setUnitIncrement(20);
        sourceScroll.getAccessibleContext().setAccessibleName("Research source attribution scroll area");
        JPanel sourcePanel = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        sourcePanel.setOpaque(false);
        sourcePanel.add(sources, BorderLayout.NORTH);
        sourcePanel.add(sourceScroll, BorderLayout.CENTER);

        JPanel profileSection = referenceSection(
                "Selected profile",
                "Every growing note retains its public source title and HTTPS address.",
                detailScroll);
        profileSection.setPreferredSize(new Dimension(420, 250));
        profileSection.setMinimumSize(new Dimension(300, 220));
        JPanel sourceSection = referenceSection(
                "Research library",
                "Extension publications, Master Gardener resources, and university collections.",
                sourcePanel);
        sourceSection.setPreferredSize(new Dimension(420, 190));
        sourceSection.setMinimumSize(new Dimension(300, 160));
        JSplitPane detailStack = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                profileSection, sourceSection);
        detailStack.setName("plant.profileSource.split");
        detailStack.getAccessibleContext().setAccessibleName("Plant profile and research library split");
        detailStack.setResizeWeight(0.75);
        detailStack.setContinuousLayout(true);
        detailStack.setBorder(null);
        detailStack.setPreferredSize(new Dimension(420, 446));
        detailStack.setMinimumSize(new Dimension(300, 386));

        JScrollPane tableScroll = FarmUi.tableScroll(table);
        tableScroll.setPreferredSize(new Dimension(520, 446));
        tableScroll.setMinimumSize(new Dimension(340, 220));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                tableScroll, detailStack);
        split.setName("plant.guide.split");
        split.setResizeWeight(0.55);
        split.setContinuousLayout(true);
        split.setBorder(null);

        JPanel content = new ReferenceBody();
        content.setOpaque(false);
        content.add(FarmUi.section(
                "Find the right plant",
                "Filter first, then select a row for field notes and provenance.",
                filters), BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        JPanel footnotes = new JPanel();
        footnotes.setOpaque(false);
        footnotes.setLayout(new BoxLayout(footnotes, BoxLayout.Y_AXIS));
        JLabel planningHint = FarmUi.formHint(
                "The source is copied into notes; dates and yield stay farm decisions.");
        planningHint.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        JLabel regionalHint = FarmUi.formHint(
                "Regional reference only: confirm cultivar, microclimate, soil test, toxicity, "
                        + "and current pest guidance before field decisions.");
        regionalHint.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        footnotes.add(planningHint);
        footnotes.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        footnotes.add(regionalHint);
        content.add(footnotes, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("plant.guide.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        FarmUi.revealFocusedControls(content);

        if (!plants.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
            showSelection(table, model, details);
        }
        applyRolePermissions(canWrite);
    }

    private static JPanel referenceSection(String title, String description, JComponent reader) {
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setOpaque(false);
        content.add(new ReferenceHint(description), BorderLayout.NORTH);
        content.add(reader, BorderLayout.CENTER);
        return FarmUi.section(title, null, content);
    }

    /** Allow attribution hints to wrap when the reader divider is adjusted. */
    private static final class ReferenceHint extends JLabel {
        private static final long serialVersionUID = 1L;

        private ReferenceHint(String text) {
            super("<html>" + text.replace("&", "&amp;").replace("<", "&lt;")
                    .replace(">", "&gt;") + "</html>");
            getAccessibleContext().setAccessibleName(text);
        }

        @Override public void updateUI() {
            super.updateUI();
            setForeground(FarmUi.mutedColor());
            setFont(getFont().deriveFont(12f));
        }

        @Override public Dimension getPreferredSize() {
            int width = getParent() == null || getParent().getWidth() <= 0 ? 340
                    : getParent().getWidth() - getParent().getInsets().left
                    - getParent().getInsets().right;
            width = Math.max(100, width);
            javax.swing.text.View content = (javax.swing.text.View)
                    getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey);
            if (content == null) return super.getPreferredSize();
            content.setSize(width, 0);
            return new Dimension(width,
                    (int) Math.ceil(content.getPreferredSpan(javax.swing.text.View.Y_AXIS)));
        }

        @Override public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
    }

    /** Preserve readable readers and table rows while short windows scroll. */
    private static final class ReferenceBody extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        private ReferenceBody() { super(new BorderLayout(0, FarmUi.SECTION_GAP)); }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }

    private static PnwPlantProfile selectedPlant(JTable table, PlantTableModel model) {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null
                : model.plantAt(table.convertRowIndexToModel(viewRow));
    }

    private static void showSelection(JTable table, PlantTableModel model, JTextArea details) {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            details.setText("No plants match the current filters.");
            return;
        }
        PnwPlantProfile plant = model.plantAt(table.convertRowIndexToModel(viewRow));
        details.setText(plant.commonName() + "\n"
                + plant.scientificName() + "\n\n"
                + "Type: " + plant.type() + "\n"
                + "Origin: " + plant.originLabel() + "\n"
                + "Light: " + plant.light() + "\n"
                + "Moisture: " + plant.moisture() + "\n"
                + "Season: " + plant.season() + "\n\n"
                + "Uses: " + plant.uses() + "\n\n"
                + "Field note: " + plant.fieldNotes() + "\n\n"
                + "Source: " + plant.source().label() + "\n"
                + plant.source().url());
        details.setCaretPosition(0);
    }

    private static void showSource(Source source, JTextArea target) {
        if (source == null) {
            target.setText("No source selected.");
            return;
        }
        target.setText(source.label() + "\n" + source.url());
        target.setCaretPosition(0);
    }

    private static final class PlantTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                "Plant", "Scientific name", "Type", "Origin", "Light", "Season"};
        private final List<PnwPlantProfile> rows;

        private PlantTableModel(List<PnwPlantProfile> rows) {
            this.rows = rows;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PnwPlantProfile plant = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> plant.commonName();
                case 1 -> plant.scientificName();
                case 2 -> plant.type();
                case 3 -> plant.originLabel();
                case 4 -> plant.light();
                case 5 -> plant.season();
                default -> "";
            };
        }

        private PnwPlantProfile plantAt(int row) {
            return rows.get(row);
        }
    }
}
