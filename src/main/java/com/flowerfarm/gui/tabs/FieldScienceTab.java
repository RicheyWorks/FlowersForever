package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmFileChooser;
import com.flowerfarm.gui.GuiPermissions;
import com.flowerfarm.model.FieldScienceExport;
import com.flowerfarm.model.FieldScienceExportSummary;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.reference.FieldScienceBundle;
import com.flowerfarm.reference.FieldScienceCatalog;
import com.flowerfarm.reference.FieldScienceCatalog.Instrument;
import com.flowerfarm.service.FieldScienceExportService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Farm-filtered launch surface for the original, standalone CSRBT HTML tools. */
public final class FieldScienceTab implements FlowerFarmTab {

    private static final int MAX_EXPORT_BYTES = 524_288;

    private static final String STORAGE_BOUNDARY =
            "These tools open in your web browser and can be used without internet. "
                    + "Work in the browser is kept separately from your saved farm records. "
                    + "To keep results with your farm records and backups, download them from "
                    + "the tool, then review and save a copy here. Opening a tool never "
                    + "changes a farm record. The tools do not provide "
                    + "pesticide, food-safety, or cannabis-compliance advice.";

    private final List<Instrument> instruments;
    private final TabHost host;
    private final PageMaterializer materializer;
    private final PageOpener opener;
    private final FieldScienceExportService exportService;
    private final FarmDialogs dialogs;
    private final OpenFilePicker openFilePicker;
    private final FarmFileChooser saveFileChooser;
    private final SelectedTextWriter selectedTextWriter;
    private final List<JButton> recordButtons = new ArrayList<>();
    private final List<JButton> saveButtons = new ArrayList<>();
    private boolean canWrite = true;
    private boolean savingExport;
    private boolean savingSelectedText;
    private boolean loadingExports;
    private boolean reloadExports;
    private boolean searchQueued;
    private String queuedSearchQuery;
    private String appliedExportQuery = "";
    private int loadingExportPageIndex;
    private int exportPageIndex;
    private boolean exportsHaveMore;
    private long exportTotalCount;
    private ExportDraft retryDraft;
    private String retryInstrumentId;
    private ExportDraftPanel activeDraft;
    private List<FieldScienceExportSummary> savedExports = List.of();
    private FieldScienceExport displayedExport;
    private long selectedExportLoadSerial;
    private long linkedExportLoadSerial;
    private JPanel panel;
    private JLabel status;
    private DefaultTableModel exportsModel;
    private JTable exportsTable;
    private JTextArea exportMetadata;
    private JTextArea exportDetail;
    private JButton refreshExports;
    private JButton previousExports;
    private JButton nextExports;
    private JTextField exportSearchField;
    private JButton searchExports;
    private JButton clearExportSearch;
    private JButton saveSelectedText;
    private JButton recordSelectedFinding;
    private JLabel exportPageLabel;
    private JComponent linkedExportSection;
    private JTextArea linkedExportMetadata;
    private JTextArea linkedExportDetail;

    public FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host) {
        this(enabledWorkspaces, host, FieldScienceBundle::materialize,
                FieldScienceTab::browse, null, FarmDialogs.swing(),
                OpenFilePicker.swing(), FarmFileChooser.swing(),
                FieldScienceTab::writeSelectedTextAtomically);
    }

    public FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host,
                           FieldScienceExportService exportService) {
        this(enabledWorkspaces, host, FieldScienceBundle::materialize,
                FieldScienceTab::browse, exportService, FarmDialogs.swing(),
                OpenFilePicker.swing(), FarmFileChooser.swing(),
                FieldScienceTab::writeSelectedTextAtomically);
    }

    public FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host,
                           FieldScienceExportService exportService, FarmDialogs dialogs,
                           FarmFileChooser saveFileChooser) {
        this(enabledWorkspaces, host, FieldScienceBundle::materialize,
                FieldScienceTab::browse, exportService, dialogs,
                OpenFilePicker.swing(), saveFileChooser,
                FieldScienceTab::writeSelectedTextAtomically);
    }

    FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host,
                    PageMaterializer materializer, PageOpener opener) {
        this(enabledWorkspaces, host, materializer, opener, null,
                FarmDialogs.swing(), OpenFilePicker.swing(), FarmFileChooser.swing(),
                FieldScienceTab::writeSelectedTextAtomically);
    }

    FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host,
                    PageMaterializer materializer, PageOpener opener,
                    FieldScienceExportService exportService, FarmDialogs dialogs,
                    OpenFilePicker openFilePicker) {
        this(enabledWorkspaces, host, materializer, opener, exportService,
                dialogs, openFilePicker, FarmFileChooser.swing(),
                FieldScienceTab::writeSelectedTextAtomically);
    }

    FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host,
                    PageMaterializer materializer, PageOpener opener,
                    FieldScienceExportService exportService, FarmDialogs dialogs,
                    OpenFilePicker openFilePicker, FarmFileChooser saveFileChooser) {
        this(enabledWorkspaces, host, materializer, opener, exportService, dialogs,
                openFilePicker, saveFileChooser, FieldScienceTab::writeSelectedTextAtomically);
    }

    FieldScienceTab(Set<FarmWorkspace> enabledWorkspaces, TabHost host,
                    PageMaterializer materializer, PageOpener opener,
                    FieldScienceExportService exportService, FarmDialogs dialogs,
                    OpenFilePicker openFilePicker, FarmFileChooser saveFileChooser,
                    SelectedTextWriter selectedTextWriter) {
        this.instruments = FieldScienceCatalog.forWorkspaces(enabledWorkspaces);
        if (instruments.isEmpty() && exportService == null) {
            throw new IllegalArgumentException("No field science tools fit these farm types.");
        }
        this.host = Objects.requireNonNull(host, "host");
        this.materializer = Objects.requireNonNull(materializer, "materializer");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.exportService = exportService;
        this.dialogs = Objects.requireNonNull(dialogs, "dialogs");
        this.openFilePicker = Objects.requireNonNull(openFilePicker, "openFilePicker");
        this.saveFileChooser = Objects.requireNonNull(saveFileChooser, "saveFileChooser");
        this.selectedTextWriter = Objects.requireNonNull(selectedTextWriter,
                "selectedTextWriter");
    }

    @Override public String getTabTitle() { return "Field science"; }

    @Override public String getDescription() {
        return instruments.isEmpty()
                ? "Browse saved field science results for this farm"
                : "Offline field and bench tools selected for this farm's types";
    }

    @Override public JComponent getUIComponent() {
        if (panel == null) {
            buildUi();
        }
        return panel;
    }

    @Override public void applyRolePermissions(boolean canWrite) {
        this.canWrite = canWrite;
        for (JButton record : recordButtons) {
            GuiPermissions.setWritable(canWrite, record);
        }
        for (JButton save : saveButtons) {
            GuiPermissions.setWritable(canWrite && exportService != null && !savingExport,
                    exportService == null ? "Saved results storage is unavailable."
                            : savingExport ? "Your field science results are being saved."
                            : GuiPermissions.VIEWER_READONLY_TIP, save);
        }
        updateSelectedArchiveActions();
    }

    @Override public void initialize() { refreshData(); }

    @Override public void refreshData() {
        loadExportPage(appliedExportQuery, exportPageIndex);
    }

    private void loadExportPage(String query, int pageIndex) {
        if (exportService == null || exportsModel == null) {
            return;
        }
        if (loadingExports) {
            reloadExports = true;
            return;
        }
        loadingExports = true;
        loadingExportPageIndex = pageIndex;
        updateExportNavigation();
        new SwingWorker<FieldScienceExportService.ExportPage, Void>() {
            @Override protected FieldScienceExportService.ExportPage doInBackground() {
                return exportService.page(query, pageIndex);
            }

            @Override protected void done() {
                loadingExports = false;
                try {
                    FieldScienceExportService.ExportPage result = get();
                    appliedExportQuery = query;
                    showExports(result);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showExportFailure("Could not search saved results. Previous results "
                            + "are still shown; retry or clear the search.");
                } catch (ExecutionException exception) {
                    showExportFailure("Could not search saved results. Previous results "
                            + "are still shown; retry or clear the search.");
                } finally {
                    updateExportNavigation();
                    if (searchQueued) {
                        String queued = queuedSearchQuery;
                        searchQueued = false;
                        queuedSearchQuery = null;
                        reloadExports = false;
                        loadExportPage(queued, 0);
                    } else if (reloadExports) {
                        reloadExports = false;
                        refreshData();
                    }
                }
            }
        }.execute();
    }

    private void searchSavedExports() {
        String query = exportSearchField.getText().trim();
        if (query.length() > 120 || query.chars().anyMatch(Character::isISOControl)) {
            showExportFailure("Search text must be 120 characters or fewer with no control characters. "
                    + "Previous results are still shown.");
            return;
        }
        if (loadingExports) {
            queuedSearchQuery = query;
            searchQueued = true;
            return;
        }
        loadExportPage(query, 0);
    }

    private void clearSavedExportSearch() {
        exportSearchField.setText("");
        searchSavedExports();
    }

    public String applicationExitWarning() {
        if (savingExport || savingSelectedText) {
            return savingSelectedText ? "A saved field science text file is being written."
                    : "Your field science results are being saved.";
        }
        return retryDraft == null ? null : "Unsaved field science results remain after a failed save.";
    }

    public boolean hasPendingSave() {
        return savingExport || savingSelectedText;
    }

    private void buildUi() {
        panel = FarmUi.page();
        panel.setName("fieldScience.page");
        panel.add(FarmUi.pageHeader("Field science",
                instruments.isEmpty()
                        ? "Browse saved results; matching offline tools appear when available."
                        : "Tools for the kind of farm you run.", null),
                BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        body.setName("fieldScience.cards");
        int row = 0;
        JPanel boundary = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        boundary.setOpaque(false);
        boundary.add(paragraph(STORAGE_BOUNDARY,
                "fieldScience.storageBoundary", 4), BorderLayout.CENTER);
        JButton knowledge = FarmUi.secondaryButton("Open Knowledge Log");
        knowledge.setName("fieldScience.openKnowledge");
        knowledge.setToolTipText("Record your own observation in FlowersForever; "
                + "nothing is copied from the browser tool.");
        knowledge.addActionListener(event -> {
            host.selectTab("Knowledge Log");
            host.setStatus("Opened Knowledge Log to record an observation manually. "
                    + "No field science content was copied.");
        });
        JButton library = FarmUi.secondaryButton("Browse all farm tools");
        library.setName("fieldScience.library");
        library.setToolTipText("Find soil, greenhouse, and other tools and guides; no internet needed.");
        library.addActionListener(event -> open("library.html", "Farm tools and guides", library));
        boundary.add(FarmUi.toolbar(knowledge, library), BorderLayout.SOUTH);
        row = addRow(body, row, FarmUi.section("Keep a copy of your results",
                "How to save work from the tools with your farm records.", boundary));
        if (exportService != null) {
            linkedExportSection = linkedSavedExportSection();
            row = addRow(body, row, linkedExportSection);
        }
        for (Instrument instrument : instruments) {
            row = addRow(body, row, card(instrument));
        }
        if (instruments.isEmpty()) {
            JComponent noTools = FarmUi.section("No matching offline tools",
                    "There are no suggested tools for this farm type yet.",
                    paragraph("Saved field science results remain available below. "
                            + "Choose a farm type with a matching tool to open or capture "
                            + "a new saved result, or choose Browse all farm tools.", "fieldScience.noTools", 2));
            noTools.setName("fieldScience.noTools.section");
            row = addRow(body, row, noTools);
        }
        if (exportService != null) {
            row = addRow(body, row, savedExportsSection());
        }
        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0;
        spacer.gridy = row;
        spacer.weighty = 1;
        body.add(Box.createVerticalGlue(), spacer);

        JScrollPane scroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setName("fieldScience.scroll");
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        panel.add(scroll, BorderLayout.CENTER);
        status = FarmUi.wrappingStatusBadge(
                instruments.isEmpty() ? "No matching offline tools · saved archive available"
                        : instruments.size() + " farm tool"
                        + (instruments.size() == 1 ? "" : "s") + " available",
                FarmUi.Tone.INFO);
        status.setName("fieldScience.status");
        panel.add(status, BorderLayout.SOUTH);
    }

    private static int addRow(JPanel target, int row, JComponent component) {
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = row;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        cell.anchor = GridBagConstraints.NORTHWEST;
        cell.insets = new Insets(0, 0, FarmUi.SECTION_GAP, 0);
        target.add(component, cell);
        return row + 1;
    }

    private JComponent linkedSavedExportSection() {
        linkedExportMetadata = FarmUi.displayArea(7, 62);
        linkedExportMetadata.setName("fieldScience.linked.metadata");
        linkedExportMetadata.getAccessibleContext().setAccessibleName(
                "Exact linked results source and archive identity");
        linkedExportDetail = FarmUi.displayArea(9, 62);
        linkedExportDetail.setName("fieldScience.linked.detail");
        linkedExportDetail.getAccessibleContext().setAccessibleName(
                "Exact linked saved field science results text");
        JScrollPane detailScroll = new JScrollPane(linkedExportDetail);
        detailScroll.setPreferredSize(new Dimension(740, 170));
        detailScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));
        JButton close = FarmUi.secondaryButton("Close linked view");
        close.setName("fieldScience.linked.close");
        close.addActionListener(event -> {
            linkedExportLoadSerial++;
            linkedExportSection.setVisible(false);
            panel.revalidate();
            host.setStatus("Closed linked results view. Saved archive search is unchanged.");
        });
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(linkedExportMetadata);
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(detailScroll);
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(FarmUi.toolbar(close));
        JComponent section = FarmUi.section("Linked saved results",
                "Opened by exact archive ID from Knowledge Log. Your archive search stays unchanged.",
                content);
        section.setName("fieldScience.linked.section");
        section.setVisible(false);
        return section;
    }

    /** Read one linked export by archive ID without changing the current archive page. */
    public void showSavedExport(long exportId) {
        if (exportId <= 0 || exportService == null) {
            host.setStatus("The linked field science results are unavailable in this workspace.");
            return;
        }
        getUIComponent();
        long loadSerial = ++linkedExportLoadSerial;
        host.setStatus("Opening linked field science results #" + exportId + "…");
        new SwingWorker<Optional<FieldScienceExport>, Void>() {
            @Override protected Optional<FieldScienceExport> doInBackground() {
                return exportService.find(exportId);
            }

            @Override protected void done() {
                if (loadSerial != linkedExportLoadSerial) {
                    return;
                }
                try {
                    Optional<FieldScienceExport> found = get();
                    if (found.isEmpty()) {
                        host.setStatus("Linked field science results #" + exportId
                                + " is no longer available. The archive view was kept.");
                        return;
                    }
                    FieldScienceExport saved = found.orElseThrow();
                    if (!verifiedLinkedExport(exportId, saved)) {
                        host.setStatus("Linked field science results #" + exportId
                                + " failed its identity or text check. The archive view was kept.");
                        return;
                    }
                    linkedExportMetadata.setText("ARCHIVE ID  #" + saved.getId()
                            + "\nTITLE  " + saved.getTitle()
                            + "\nINSTRUMENT  " + saved.getInstrumentTitle()
                            + "\nSOURCE  " + saved.getSourceUrl()
                            + "\nSOURCE COMMIT  " + saved.getSourceCommit()
                            + "\nFILE  " + (saved.getFilename() == null
                                    || saved.getFilename().isBlank()
                                    ? "Pasted text" : saved.getFilename())
                            + "\nNOTE  " + saved.getNote()
                            + "\nSHA-256  " + saved.getSha256());
                    linkedExportDetail.setText(saved.getContent());
                    linkedExportDetail.setCaretPosition(0);
                    linkedExportSection.setVisible(true);
                    panel.revalidate();
                    host.setStatus("Opened linked field science results #" + exportId
                            + " by exact archive ID.");
                    SwingUtilities.invokeLater(() -> linkedExportSection.scrollRectToVisible(
                            new Rectangle(0, 0, 1, 1)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    host.setStatus("Could not open linked field science results #" + exportId
                            + ". The archive view was kept.");
                } catch (ExecutionException exception) {
                    host.setStatus("Could not open linked field science results #" + exportId
                            + ". The archive view was kept.");
                }
            }
        }.execute();
    }

    private static boolean verifiedLinkedExport(long exportId, FieldScienceExport saved) {
        if (!Objects.equals(saved.getId(), exportId) || saved.getContent() == null) {
            return false;
        }
        byte[] bytes = saved.getContent().getBytes(StandardCharsets.UTF_8);
        return bytes.length == saved.getByteLength()
                && Objects.equals(sha256(bytes), saved.getSha256());
    }

    private JComponent savedExportsSection() {
        exportSearchField = FarmUi.searchField("Search saved results",
                "Title, tool, filename, or note", 38);
        exportSearchField.setName("fieldScience.exports.search");
        exportSearchField.addActionListener(event -> searchSavedExports());
        searchExports = FarmUi.primaryButton("Search results");
        searchExports.setName("fieldScience.exports.searchButton");
        searchExports.addActionListener(event -> searchSavedExports());
        clearExportSearch = FarmUi.secondaryButton("Clear search");
        clearExportSearch.setName("fieldScience.exports.clearSearch");
        clearExportSearch.addActionListener(event -> clearSavedExportSearch());
        exportSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateExportNavigation(); }
            @Override public void removeUpdate(DocumentEvent event) { updateExportNavigation(); }
            @Override public void changedUpdate(DocumentEvent event) { updateExportNavigation(); }
        });
        exportsModel = new DefaultTableModel(
                new String[]{"Saved", "Title", "Instrument", "Size"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        exportsTable = new JTable(exportsModel);
        exportsTable.setName("fieldScience.exports.table");
        exportsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        FarmUi.configureTable(exportsTable, "Saved field science results");
        exportsTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelectedExport();
            }
        });
        JScrollPane tableScroll = FarmUi.tableScroll(exportsTable);
        tableScroll.setPreferredSize(new Dimension(740, 155));

        exportMetadata = FarmUi.displayArea(8, 62);
        exportMetadata.setName("fieldScience.exports.metadata");
        exportMetadata.setOpaque(false);
        exportMetadata.setBorder(BorderFactory.createEmptyBorder());
        exportMetadata.getAccessibleContext().setAccessibleName(
                "Selected results source and note");
        exportMetadata.setText("Select saved results to read their source and note.");
        exportDetail = FarmUi.displayArea(9, 62);
        exportDetail.setName("fieldScience.exports.detail");
        exportDetail.getAccessibleContext().setAccessibleName(
                "Selected saved field science results text");
        exportDetail.setText("");
        JScrollPane detailScroll = new JScrollPane(exportDetail);
        detailScroll.setPreferredSize(new Dimension(740, 170));
        detailScroll.setBorder(BorderFactory.createLineBorder(FarmUi.borderColor()));

        refreshExports = FarmUi.secondaryButton("Refresh saved results");
        refreshExports.setName("fieldScience.exports.refresh");
        refreshExports.addActionListener(event -> refreshData());
        previousExports = FarmUi.secondaryButton("Previous");
        previousExports.setName("fieldScience.exports.previous");
        previousExports.addActionListener(event -> {
            if (!loadingExports && exportPageIndex > 0) {
                loadExportPage(appliedExportQuery, exportPageIndex - 1);
            }
        });
        nextExports = FarmUi.secondaryButton("Next");
        nextExports.setName("fieldScience.exports.next");
        nextExports.addActionListener(event -> {
            if (!loadingExports && exportsHaveMore) {
                loadExportPage(appliedExportQuery, exportPageIndex + 1);
            }
        });
        exportPageLabel = new JLabel("No saved results loaded");
        exportPageLabel.setName("fieldScience.exports.pageStatus");
        exportPageLabel.getAccessibleContext().setAccessibleName(
                "Saved field science results page and count");
        saveSelectedText = FarmUi.secondaryButton("Save selected text…");
        saveSelectedText.setName("fieldScience.exports.saveSelected");
        saveSelectedText.setToolTipText("Save the selected archived text as a local "
                + "text file. Available to everyone who can view this farm.");
        saveSelectedText.getAccessibleContext().setAccessibleName(
                "Save selected field science results text to a file");
        saveSelectedText.addActionListener(event -> saveSelectedText());
        recordSelectedFinding = FarmUi.secondaryButton("Record finding from selected…");
        recordSelectedFinding.setName("fieldScience.exports.recordFinding");
        recordSelectedFinding.setToolTipText("Prepare an editable Knowledge Log finding "
                + "linked to these saved results. Add your own observation before saving.");
        recordSelectedFinding.getAccessibleContext().setAccessibleName(
                "Record a finding from the selected saved field science results");
        recordSelectedFinding.addActionListener(event -> recordFindingFromSelected());
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(FarmUi.toolbar(exportSearchField, searchExports, clearExportSearch));
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(paragraph("Search title, tool, filename, and note across all "
                + "saved results; results text is not searched. Every role can read, search, "
                + "and save selected archive text to a local file. Only a farmhand or owner "
                + "can add reviewed results to the archive or record a linked finding.",
                "fieldScience.exports.searchHelp", 3));
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(tableScroll);
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(exportMetadata);
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(detailScroll);
        content.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        content.add(FarmUi.toolbar(saveSelectedText, recordSelectedFinding,
                previousExports, nextExports,
                refreshExports, exportPageLabel));
        updateExportNavigation();
        JComponent section = FarmUi.section("Saved field science results",
                "Find and read reviewed results from this farm's archive.",
                content);
        section.setName("fieldScience.exports.section");
        return section;
    }

    private void showExports(FieldScienceExportService.ExportPage page) {
        Long selectedId = selectedExport() == null ? null : selectedExport().id();
        displayedExport = null;
        selectedExportLoadSerial++;
        exportPageIndex = page.pageIndex();
        exportsHaveMore = page.hasMore();
        exportTotalCount = page.totalCount();
        savedExports = List.copyOf(page.exports());
        exportsModel.setRowCount(0);
        int restore = -1;
        for (int index = 0; index < savedExports.size(); index++) {
            FieldScienceExportSummary item = savedExports.get(index);
            exportsModel.addRow(new Object[]{item.createdAt(), item.title(),
                    item.instrumentTitle(), item.byteLength() + " B"});
            if (Objects.equals(selectedId, item.id())) {
                restore = index;
            }
        }
        if (savedExports.isEmpty()) {
            exportMetadata.setText(appliedExportQuery.isEmpty()
                    ? "No results saved yet. Open a tool, copy its results, "
                    + "then choose Save results from this tool on that tool's card."
                    : "No saved results match “" + appliedExportQuery
                    + "”. Try fewer words or choose Clear search.");
            exportDetail.setText("");
        } else {
            exportsTable.setRowSelectionInterval(restore >= 0 ? restore : 0,
                    restore >= 0 ? restore : 0);
        }
        updateExportNavigation();
    }

    private void updateExportNavigation() {
        if (refreshExports == null) {
            return;
        }
        refreshExports.setEnabled(!loadingExports);
        searchExports.setEnabled(!loadingExports);
        clearExportSearch.setEnabled(!loadingExports
                && (!exportSearchField.getText().isBlank() || !appliedExportQuery.isEmpty()));
        previousExports.setEnabled(!loadingExports && exportPageIndex > 0);
        nextExports.setEnabled(!loadingExports && exportsHaveMore);
        if (loadingExports) {
            exportPageLabel.setText("Loading page " + (loadingExportPageIndex + 1) + "…");
        } else if (exportTotalCount == 0) {
            exportPageLabel.setText(appliedExportQuery.isEmpty()
                    ? "No saved results" : "No results for “"
                    + shortQuery(appliedExportQuery) + "”");
        } else {
            long first = (long) exportPageIndex * FieldScienceExportService.LIST_LIMIT + 1;
            long last = first + savedExports.size() - 1;
            exportPageLabel.setText("Showing " + first + "–" + last + " of "
                    + exportTotalCount + " · page " + (exportPageIndex + 1)
                    + (appliedExportQuery.isEmpty() ? ""
                    : " · “" + shortQuery(appliedExportQuery) + "”"));
        }
        exportPageLabel.getAccessibleContext().setAccessibleDescription(
                appliedExportQuery.isEmpty() ? "All saved field science results"
                        : "Results for search: " + appliedExportQuery);
        exportPageLabel.getAccessibleContext().setAccessibleName(
                exportPageLabel.getText());
        updateSelectedArchiveActions();
    }

    private static String shortQuery(String query) {
        return query.length() > 28 ? query.substring(0, 27) + "…" : query;
    }

    private FieldScienceExportSummary selectedExport() {
        if (exportsTable == null || exportsTable.getSelectedRow() < 0) {
            return null;
        }
        int row = exportsTable.convertRowIndexToModel(exportsTable.getSelectedRow());
        return row >= 0 && row < savedExports.size() ? savedExports.get(row) : null;
    }

    private void showSelectedExport() {
        FieldScienceExportSummary item = selectedExport();
        long loadSerial = ++selectedExportLoadSerial;
        displayedExport = null;
        updateSelectedArchiveActions();
        if (item == null) {
            return;
        }
        exportMetadata.setText("Loading “" + item.title() + "”…");
        exportDetail.setText("");
        new SwingWorker<Optional<FieldScienceExport>, Void>() {
            @Override protected Optional<FieldScienceExport> doInBackground() {
                return exportService.find(item.id());
            }

            @Override protected void done() {
                if (loadSerial != selectedExportLoadSerial || selectedExport() == null
                        || !Objects.equals(selectedExport().id(), item.id())) {
                    return;
                }
                try {
                    Optional<FieldScienceExport> found = get();
                    if (found.isEmpty()) {
                        exportMetadata.setText(
                                "These results are no longer available. Refresh the list.");
                        return;
                    }
                    FieldScienceExport saved = found.orElseThrow();
                    if (!sameArchiveRow(item, saved)) {
                        exportMetadata.setText("Saved results details changed. Refresh the list "
                                + "before saving this text to a file.");
                        return;
                    }
                    exportMetadata.setText("INSTRUMENT  " + saved.getInstrumentTitle()
                            + "\nSOURCE  " + saved.getSourceUrl()
                            + "\nSOURCE COMMIT  " + saved.getSourceCommit()
                            + "\nFILE  " + (saved.getFilename() == null
                                    || saved.getFilename().isBlank()
                                    ? "Pasted text" : saved.getFilename())
                            + "\nNOTE  " + saved.getNote()
                            + "\nSHA-256  " + saved.getSha256());
                    exportDetail.setText(saved.getContent());
                    exportDetail.setCaretPosition(0);
                    displayedExport = saved;
                    updateSelectedArchiveActions();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    exportMetadata.setText("Could not read these results. Select them again to retry.");
                } catch (ExecutionException exception) {
                    exportMetadata.setText("Could not read these results. Select them again to retry.");
                }
            }
        }.execute();
    }

    private static boolean sameArchiveRow(FieldScienceExportSummary summary,
                                          FieldScienceExport saved) {
        return summary != null && saved != null
                && Objects.equals(summary.id(), saved.getId())
                && Objects.equals(summary.instrumentId(), saved.getInstrumentId())
                && Objects.equals(summary.instrumentTitle(), saved.getInstrumentTitle())
                && Objects.equals(summary.sourceUrl(), saved.getSourceUrl())
                && Objects.equals(summary.sourceCommit(), saved.getSourceCommit())
                && Objects.equals(summary.title(), saved.getTitle())
                && Objects.equals(summary.filename(), saved.getFilename())
                && Objects.equals(summary.note(), saved.getNote())
                && summary.byteLength() == saved.getByteLength()
                && Objects.equals(summary.sha256(), saved.getSha256());
    }

    private void updateSelectedArchiveActions() {
        if (saveSelectedText != null) {
            saveSelectedText.setEnabled(!loadingExports && !savingSelectedText
                    && sameArchiveRow(selectedExport(), displayedExport));
        }
        if (recordSelectedFinding != null) {
            boolean permitted = canWrite && host.canMutateData();
            recordSelectedFinding.setEnabled(permitted && !loadingExports
                    && !savingSelectedText && sameArchiveRow(selectedExport(), displayedExport));
            recordSelectedFinding.setToolTipText(permitted
                    ? "Prepare an editable Knowledge Log finding linked to this exact "
                            + "saved results. Add your own observation before saving."
                    : GuiPermissions.VIEWER_READONLY_TIP);
            recordSelectedFinding.getAccessibleContext().setAccessibleDescription(
                    recordSelectedFinding.getToolTipText());
        }
    }

    private void recordFindingFromSelected() {
        FieldScienceExportSummary row = selectedExport();
        FieldScienceExport saved = displayedExport;
        if (!canWrite || !host.canMutateData()) {
            host.setStatus("This session cannot record a field science finding.");
            return;
        }
        if (loadingExports || savingSelectedText || !sameArchiveRow(row, saved)) {
            host.setStatus("Select saved results that have finished loading before recording a finding.");
            return;
        }
        try {
            verifiedSavedText(row, saved);
        } catch (IllegalArgumentException exception) {
            showExportFailure("The selected results failed its saved size or fingerprint "
                    + "check. Refresh the archive before recording a finding.");
            return;
        }
        host.showFieldScienceFindingFromExport(row);
    }

    private void saveSelectedText() {
        FieldScienceExportSummary row = selectedExport();
        FieldScienceExport saved = displayedExport;
        if (loadingExports || savingSelectedText || !sameArchiveRow(row, saved)) {
            host.setStatus("Select saved results that have finished loading before saving its text.");
            return;
        }
        String suggested = saved.getFilename() == null || saved.getFilename().isBlank()
                ? "field-science-export-" + row.id() + ".txt" : saved.getFilename();
        Optional<File> choice = saveFileChooser.chooseSave(panel,
                "Save selected field science text", new File(suggested));
        if (choice.isEmpty()) {
            host.setStatus("Saving selected field science text was cancelled. "
                    + "No file was changed.");
            return;
        }
        Path destination;
        try {
            destination = textDestination(choice.orElseThrow(), suggested);
        } catch (IllegalArgumentException exception) {
            dialogs.showMessage(panel, exception.getMessage(),
                    "Choose a text results file", JOptionPane.WARNING_MESSAGE);
            host.setStatus("Selected text was not saved. Choose a .eco, .txt, or .csv file.");
            return;
        }
        if (Files.isDirectory(destination)) {
            host.setStatus("Selected text was not saved. Choose a file, not a folder.");
            return;
        }
        boolean replaceExisting = Files.exists(destination);
        if (replaceExisting) {
            int confirm = dialogs.showConfirm(panel,
                    FarmUi.confirmationPanel("Replace “" + destination.getFileName() + "”?",
                            "The existing file will be replaced only after the selected "
                                    + "text is ready to write.", FarmUi.Tone.WARNING),
                    "Replace existing results?", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                host.setStatus("Saving selected text was cancelled. Existing file was kept.");
                return;
            }
        }
        if (!sameArchiveRow(selectedExport(), saved) || displayedExport != saved) {
            host.setStatus("The selected results changed. Select it again before saving text.");
            updateSelectedArchiveActions();
            return;
        }
        byte[] exactText;
        try {
            exactText = verifiedSavedText(row, saved);
        } catch (IllegalArgumentException exception) {
            showExportFailure("Selected text was not saved. The stored results did not "
                    + "match its saved size or fingerprint; refresh the archive.");
            return;
        }
        savingSelectedText = true;
        updateSelectedArchiveActions();
        FarmUi.updateStatusBadge(status,
                "Saving selected text to " + destination.getFileName() + "…",
                FarmUi.Tone.INFO);
        boolean allowReplace = replaceExisting;
        new SwingWorker<Path, Void>() {
            @Override protected Path doInBackground() throws IOException {
                return selectedTextWriter.write(destination, exactText, allowReplace);
            }

            @Override protected void done() {
                savingSelectedText = false;
                updateSelectedArchiveActions();
                try {
                    Path written = get();
                    String message = "Saved selected field science text to " + written + ".";
                    FarmUi.updateStatusBadge(status, message, FarmUi.Tone.SUCCESS);
                    host.setStatus(message);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showExportFailure("Could not save selected text. The existing file was kept; "
                            + "choose a destination and retry.");
                } catch (ExecutionException exception) {
                    showExportFailure("Could not save selected text. The existing file was kept; "
                            + "choose a destination and retry.");
                }
            }
        }.execute();
    }

    private static Path textDestination(File picked, String suggested) {
        Path selected = picked.toPath().toAbsolutePath().normalize();
        Path fileName = selected.getFileName();
        String name = fileName == null ? "" : fileName.toString();
        if (name.isBlank()) {
            throw new IllegalArgumentException("Choose a .eco, .txt, or .csv filename.");
        }
        int dot = name.lastIndexOf('.');
        if (dot < 1) {
            String sourceExtension = suggested.substring(suggested.lastIndexOf('.'));
            name += sourceExtension;
            selected = selected.resolveSibling(name);
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".eco") && !lower.endsWith(".txt")
                && !lower.endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "Save selected text as a .eco, .txt, or .csv file.");
        }
        return selected;
    }

    private static byte[] verifiedSavedText(FieldScienceExportSummary row,
                                             FieldScienceExport saved) {
        if (!sameArchiveRow(row, saved) || saved.getContent() == null) {
            throw new IllegalArgumentException("Saved results do not match the selected row.");
        }
        byte[] bytes = saved.getContent().getBytes(StandardCharsets.UTF_8);
        if (bytes.length != saved.getByteLength()
                || !Objects.equals(sha256(bytes), saved.getSha256())) {
            throw new IllegalArgumentException("Saved results text failed its integrity check.");
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static Path writeSelectedTextAtomically(Path destination, byte[] bytes,
                                                     boolean allowReplace) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("The selected folder does not exist.");
        }
        Path temporary = Files.createTempFile(parent, ".field-science-text-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (Files.size(temporary) != bytes.length) {
                throw new IOException("The temporary results text was incomplete.");
            }
            if (!allowReplace) {
                return Files.move(temporary, target);
            }
            try {
                return Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("This folder cannot safely replace an existing "
                        + "file. Choose another destination.", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private JComponent card(Instrument instrument) {
        JPanel content = new JPanel(new BorderLayout(0, FarmUi.CONTROL_GAP));
        content.setOpaque(false);

        JPanel copy = new JPanel();
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        copy.add(paragraph(instrument.description(),
                "fieldScience.description." + instrument.id(), 2));
        copy.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        copy.add(paragraph(instrument.scopeNote(),
                "fieldScience.scope." + instrument.id(), 2));
        copy.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
        JTextArea sourceNote = paragraph("Source: " + instrument.sourceLabel() + " · "
                        + instrument.sourceUrl(),
                "fieldScience.source." + instrument.id(), 2);
        sourceNote.setVisible(false);
        JButton about = FarmUi.secondaryButton("About this tool");
        about.setName("fieldScience.about." + instrument.id());
        about.getAccessibleContext().setAccessibleName("About " + instrument.title());
        about.setToolTipText("Show the original source and credits for this tool.");
        about.addActionListener(event -> {
            sourceNote.setVisible(!sourceNote.isVisible());
            about.setText(sourceNote.isVisible() ? "Hide source details" : "About this tool");
            copy.revalidate();
            copy.repaint();
        });
        copy.add(FarmUi.toolbar(about));
        copy.add(sourceNote);
        content.add(copy, BorderLayout.CENTER);

        JButton open = FarmUi.secondaryButton("Open offline tool");
        open.setName("fieldScience.open." + instrument.id());
        open.setToolTipText("Open " + instrument.title()
                + " in the system browser from the bundled local copy.");
        open.getAccessibleContext().setAccessibleName(
                "Open " + instrument.title() + " offline tool");
        open.addActionListener(event -> open(instrument, open));
        JButton record = FarmUi.secondaryButton("Record a finding");
        record.setName("fieldScience.record." + instrument.id());
        record.setToolTipText("Prepare an editable Knowledge Log note with this tool's "
                + "source. Add your own observation before saving.");
        record.getAccessibleContext().setAccessibleName(
                "Record a finding from " + instrument.title());
        record.addActionListener(event -> host.showFieldScienceFinding(
                instrument.title(), instrument.sourceUrl()));
        recordButtons.add(record);
        GuiPermissions.setWritable(canWrite, record);
        JButton save = FarmUi.secondaryButton("Save results from this tool…");
        save.setName("fieldScience.save." + instrument.id());
        save.setToolTipText("Paste or drop results from " + instrument.title()
                + ", review the text, then explicitly save it to this farm.");
        save.getAccessibleContext().setAccessibleName(
                "Save results from " + instrument.title());
        save.addActionListener(event -> captureExport(instrument));
        saveButtons.add(save);
        GuiPermissions.setWritable(canWrite && exportService != null, save);
        content.add(FarmUi.toolbar(open, save, record), BorderLayout.SOUTH);

        JPanel card = FarmUi.section(instrument.title(), instrument.section(), content);
        card.setName("fieldScience.card." + instrument.id());
        return card;
    }

    private static JTextArea paragraph(String text, String name, int rows) {
        JTextArea area = new JTextArea(text, rows, 56);
        area.setName(name);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder());
        area.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12.5f));
        area.getAccessibleContext().setAccessibleName(text);
        return area;
    }

    private void captureExport(Instrument instrument) {
        if (exportService == null || savingExport || !canWrite || !host.canMutateData()) {
            host.setStatus("This session cannot save field science results.");
            return;
        }
        if (retryDraft != null) {
            if (!instrument.id().equals(retryInstrumentId)) {
                Object[] choices = {"Keep draft", "Discard and continue"};
                int answer = dialogs.showOption(panel,
                        FarmUi.confirmationPanel("Discard the failed results draft?",
                                "The unsaved results from another tool will be lost. "
                                        + "Choose Keep draft to return and retry it.",
                                FarmUi.Tone.WARNING),
                        "Replace results draft?", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
                if (answer != 1) {
                    host.setStatus("The unsaved field science results draft was kept.");
                    return;
                }
                retryDraft = null;
                retryInstrumentId = null;
            } else {
                Object[] choices = {"Edit retained draft", "Start blank", "Cancel"};
                int answer = dialogs.showOption(panel,
                        FarmUi.confirmationPanel("A failed results draft is available",
                                "Review and retry the saved draft, or explicitly discard it "
                                        + "to begin a new results draft.", FarmUi.Tone.INFO),
                        "Continue results draft", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
                if (answer == 2 || answer < 0) {
                    return;
                }
                if (answer == 1) {
                    retryDraft = null;
                    retryInstrumentId = null;
                }
            }
        }
        ExportDraft initial = instrument.id().equals(retryInstrumentId) ? retryDraft : null;
        ExportDraftPanel form = new ExportDraftPanel(instrument, initial);
        activeDraft = form;
        JScrollPane scroll = FarmUi.dialogScroll(panel, form.panel(), new Dimension(760, 630));
        scroll.setName("fieldScience.exportDraft.scroll");
        try {
            while (true) {
                Object[] choices = {"Save results", "Cancel"};
                int answer = dialogs.showOption(panel, scroll, "Save tool results",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                        null, choices, choices[0]);
                if (answer != 0) {
                    host.setStatus("Results draft closed. No field science results were saved.");
                    return;
                }
                try {
                    ExportDraft draft = form.read();
                    if (!canWrite || !host.canMutateData()) {
                        host.setStatus("This session cannot save field science results.");
                        return;
                    }
                    saveExport(instrument, draft);
                    return;
                } catch (IllegalArgumentException exception) {
                    dialogs.showMessage(panel, exception.getMessage(),
                            "Check these results", JOptionPane.WARNING_MESSAGE);
                }
            }
        } finally {
            activeDraft = null;
        }
    }

    /** Stage a text or file drop into the open export draft; never saves it. */
    boolean importExportDrop(Transferable transferable) {
        return activeDraft != null && activeDraft.importExportDrop(transferable);
    }

    private void saveExport(Instrument instrument, ExportDraft draft) {
        retryInstrumentId = instrument.id();
        retryDraft = draft;
        savingExport = true;
        applyRolePermissions(canWrite);
        FarmUi.updateStatusBadge(status,
                "Saving results from " + instrument.title() + "…", FarmUi.Tone.INFO);
        new SwingWorker<FieldScienceExport, Void>() {
            @Override protected FieldScienceExport doInBackground() {
                return exportService.save(instrument.id(), draft.title(),
                        draft.filename(), draft.note(), draft.content());
            }

            @Override protected void done() {
                savingExport = false;
                applyRolePermissions(canWrite);
                try {
                    FieldScienceExport saved = get();
                    retryDraft = null;
                    retryInstrumentId = null;
                    String message = "Saved field science results “" + saved.getTitle()
                            + "”. Select it in Saved field science results to read it.";
                    FarmUi.updateStatusBadge(status, message, FarmUi.Tone.SUCCESS);
                    host.setStatus(message);
                    exportSearchField.setText("");
                    searchSavedExports();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showExportFailure("Results were not saved. Its draft is kept; "
                            + "choose Save results from this tool to retry.");
                } catch (ExecutionException exception) {
                    showExportFailure("Results were not saved. Its draft is kept; "
                            + "choose Save results from this tool to retry.");
                }
            }
        }.execute();
    }

    private void showExportFailure(String message) {
        FarmUi.updateStatusBadge(status, message, FarmUi.Tone.DANGER);
        host.setStatus(message);
    }

    private final class ExportDraftPanel {
        private final JPanel panel;
        private final JTextField title;
        private final JTextArea note;
        private final JTextArea content;
        private final JLabel filenameLabel;
        private final JLabel draftStatus;
        private String filename;

        private ExportDraftPanel(Instrument instrument, ExportDraft initial) {
            this.filename = initial == null ? "" : initial.filename();
            panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.add(paragraph("Source: " + instrument.title() + " · "
                    + instrument.sourceUrl(), "fieldScience.exportDraft.source", 2));
            panel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
            panel.add(paragraph("Use the card for the tool where you made these results. "
                    + "Paste copied results or choose a file you downloaded from the tool "
                    + "(supported file endings: .eco, .txt, .csv). "
                    + "The text stays a draft until you choose Save results. A saved copy "
                    + "does not update any crop, batch, or sales record.",
                    "fieldScience.exportDraft.help", 4));
            panel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));

            title = new JTextField(initial == null
                    ? instrument.title() + " results" : initial.title(), 48);
            title.setName("fieldScience.exportDraft.title");
            title.getAccessibleContext().setAccessibleName("Results title");
            panel.add(FarmUi.toolbar(new JLabel("Title:"), title));
            panel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));

            note = new JTextArea(initial == null ? "" : initial.note(), 3, 56);
            note.setName("fieldScience.exportDraft.note");
            note.setLineWrap(true);
            note.setWrapStyleWord(true);
            note.getAccessibleContext().setAccessibleName("Notes about these results");
            panel.add(new JLabel("Note (optional):"));
            panel.add(new JScrollPane(note));
            panel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));

            filenameLabel = new JLabel(filename.isBlank()
                    ? "Source: pasted text" : "Source file: " + filename);
            filenameLabel.setName("fieldScience.exportDraft.filename");
            JButton browse = FarmUi.secondaryButton("Choose a results file…");
            browse.setName("fieldScience.exportDraft.browse");
            browse.addActionListener(event -> openFilePicker.choose(panel).ifPresent(
                    path -> stageFile(path.toFile())));
            panel.add(FarmUi.toolbar(filenameLabel, browse));
            panel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));

            content = new JTextArea(initial == null ? "" : initial.content(), 15, 64);
            content.setName("fieldScience.exportDraft.content");
            content.setLineWrap(false);
            content.getAccessibleContext().setAccessibleName("Results from your farm tool");
            content.getAccessibleContext().setAccessibleDescription(
                    "Paste your results or drop one downloaded file here (file endings: .eco, .txt, .csv). "
                            + "Saving requires the Save results dialog choice.");
            panel.add(new JLabel("Results to keep (paste or drop your downloaded file here):"));
            content.setTransferHandler(new ExportTextDropHandler(content.getTransferHandler()));
            JScrollPane contentScroll = new JScrollPane(content);
            contentScroll.setPreferredSize(new Dimension(710, 260));
            panel.add(contentScroll);
            panel.add(Box.createVerticalStrut(FarmUi.CONTROL_GAP));
            draftStatus = FarmUi.wrappingStatusBadge("No results added yet", FarmUi.Tone.NEUTRAL);
            draftStatus.setName("fieldScience.exportDraft.status");
            panel.add(draftStatus);
            content.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { updateDraftStatus(); }
                @Override public void removeUpdate(DocumentEvent event) { updateDraftStatus(); }
                @Override public void changedUpdate(DocumentEvent event) { updateDraftStatus(); }
            });
            updateDraftStatus();
        }

        private JPanel panel() { return panel; }

        private ExportDraft read() {
            String proposedTitle = title.getText().trim();
            String proposedNote = note.getText().trim();
            String exportText = content.getText();
            if (proposedTitle.isEmpty() || proposedTitle.length() > 180) {
                throw new IllegalArgumentException("Give these results a title of 1–180 characters.");
            }
            if (proposedNote.length() > 500) {
                throw new IllegalArgumentException("Keep the results note within 500 characters.");
            }
            int size = utf8Size(exportText);
            if (emptyExportText(exportText) || size > MAX_EXPORT_BYTES) {
                throw new IllegalArgumentException(
                        "Add your results before saving. The file-size limit is 512 KB (524,288 bytes).");
            }
            return new ExportDraft(proposedTitle, filename, proposedNote, exportText);
        }

        private void updateDraftStatus() {
            try {
                int size = utf8Size(content.getText());
                String message = size + " of " + MAX_EXPORT_BYTES
                        + " bytes of results added · review before saving";
                FarmUi.updateStatusBadge(draftStatus, message,
                        size > MAX_EXPORT_BYTES ? FarmUi.Tone.WARNING : FarmUi.Tone.INFO);
            } catch (IllegalArgumentException exception) {
                FarmUi.updateStatusBadge(draftStatus,
                        "Text has invalid characters · review before saving",
                        FarmUi.Tone.WARNING);
            }
        }

        private boolean stageFile(File file) {
            try {
                String extension = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (!extension.endsWith(".eco") && !extension.endsWith(".txt")
                        && !extension.endsWith(".csv")) {
                    throw new IllegalArgumentException("Choose a results file (.eco, .txt, or .csv).");
                }
                Path path = file.toPath();
                if (!Files.isRegularFile(path) || Files.size(path) > MAX_EXPORT_BYTES) {
                    throw new IllegalArgumentException(
                            "Choose one results file that can be opened, up to 512 KB (524,288 bytes).");
                }
                byte[] bytes;
                try (InputStream stream = Files.newInputStream(path)) {
                    bytes = stream.readNBytes(MAX_EXPORT_BYTES + 1);
                }
                if (bytes.length > MAX_EXPORT_BYTES) {
                    throw new IllegalArgumentException("This results file is too large. The limit is 512 KB (524,288 bytes).");
                }
                String imported = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                return stageText(imported, file.getName());
            } catch (CharacterCodingException exception) {
                FarmUi.updateStatusBadge(draftStatus,
                        "Could not read this file · ask your equipment helper for a plain-text copy (UTF-8).",
                        FarmUi.Tone.WARNING);
                return false;
            } catch (IOException | IllegalArgumentException exception) {
                FarmUi.updateStatusBadge(draftStatus,
                        "File kept out of draft · " + exception.getMessage(),
                        FarmUi.Tone.WARNING);
                return false;
            }
        }

        private boolean stageText(String text, String sourceFile) {
            try {
                int size = utf8Size(text);
                if (emptyExportText(text) || size > MAX_EXPORT_BYTES) {
                    throw new IllegalArgumentException(
                            "Add your results. The file-size limit is 512 KB (524,288 bytes).");
                }
                if (!content.getText().isBlank() && !content.getText().equals(text)) {
                    Object[] choices = {"Keep draft", "Use new text"};
                    int answer = dialogs.showOption(panel,
                            FarmUi.confirmationPanel("Replace results draft?",
                                    "The current unsaved results text will be replaced. "
                                            + "Title and note stay as entered.", FarmUi.Tone.WARNING),
                            "Replace results draft?", JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
                    if (answer != 1) {
                        return false;
                    }
                }
                content.setText(text);
                content.setCaretPosition(0);
                filename = sourceFile;
                filenameLabel.setText(filename.isBlank()
                        ? "Source: pasted text" : "Source file: " + filename);
                updateDraftStatus();
                return true;
            } catch (IllegalArgumentException exception) {
                FarmUi.updateStatusBadge(draftStatus,
                        "Drop not used · " + exception.getMessage(), FarmUi.Tone.WARNING);
                return false;
            }
        }

        /** Testable, draft-only entry point for text and single-file drops. */
        boolean importExportDrop(Transferable transferable) {
            if (transferable == null || !canWrite || savingExport) {
                return false;
            }
            try {
                if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    Object value = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(value instanceof List<?> files) || files.size() != 1
                            || !(files.get(0) instanceof File file)) {
                        throw new IllegalArgumentException("Drop one results file at a time.");
                    }
                    return stageFile(file);
                }
                String text;
                if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    text = (String) transferable.getTransferData(DataFlavor.stringFlavor);
                } else {
                    DataFlavor flavor = DataFlavor.selectBestTextFlavor(
                            transferable.getTransferDataFlavors());
                    if (flavor == null) {
                        throw new IllegalArgumentException(
                                "Drop text or one .eco, .txt, or .csv file.");
                    }
                    try (Reader reader = flavor.getReaderForText(transferable)) {
                        StringBuilder result = new StringBuilder();
                        char[] buffer = new char[8192];
                        int count;
                        while ((count = reader.read(buffer)) >= 0) {
                            if (result.length() + count > MAX_EXPORT_BYTES) {
                                throw new IllegalArgumentException(
                                        "Dropped text exceeds 512 KiB.");
                            }
                            result.append(buffer, 0, count);
                        }
                        text = result.toString();
                    }
                }
                return stageText(text, "");
            } catch (IOException | UnsupportedFlavorException | IllegalArgumentException exception) {
                FarmUi.updateStatusBadge(draftStatus,
                        "Drop not used · " + exception.getMessage(), FarmUi.Tone.WARNING);
                return false;
            }
        }

        private final class ExportTextDropHandler extends TransferHandler {
            private final TransferHandler textHandler;

            private ExportTextDropHandler(TransferHandler textHandler) {
                this.textHandler = textHandler;
            }

            @Override public boolean canImport(TransferSupport support) {
                if (!support.isDrop()) {
                    return textHandler != null && textHandler.canImport(support);
                }
                return canWrite && !savingExport
                        && (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(DataFlavor.stringFlavor)
                        || DataFlavor.selectBestTextFlavor(support.getDataFlavors()) != null);
            }

            @Override public boolean importData(TransferSupport support) {
                if (!support.isDrop()) {
                    return textHandler != null && textHandler.importData(support);
                }
                return canImport(support) && importExportDrop(support.getTransferable());
            }

            @Override public int getSourceActions(javax.swing.JComponent component) {
                return textHandler == null ? NONE : textHandler.getSourceActions(component);
            }

            @Override public void exportAsDrag(javax.swing.JComponent component,
                                               java.awt.event.InputEvent event, int action) {
                if (textHandler != null) {
                    textHandler.exportAsDrag(component, event, action);
                }
            }

            @Override public void exportToClipboard(javax.swing.JComponent component,
                    java.awt.datatransfer.Clipboard clipboard, int action)
                    throws IllegalStateException {
                if (textHandler != null) {
                    textHandler.exportToClipboard(component, clipboard, action);
                }
            }
        }
    }

    private static int utf8Size(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Results text is required.");
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if ((character < 0x20 && character != '\t' && character != '\r'
                    && character != '\n') || character == 0x7f) {
                throw new IllegalArgumentException("These results contain unreadable characters. Use a plain-text copy.");
            }
        }
        try {
            CharBuffer input = CharBuffer.wrap(text);
            return StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(input).remaining();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Could not read these results. Use a plain-text copy (UTF-8).", exception);
        }
    }

    private static boolean emptyExportText(String text) {
        return (text.startsWith("\uFEFF") ? text.substring(1) : text).isBlank();
    }

    private record ExportDraft(String title, String filename, String note, String content) { }

    private void open(Instrument instrument, JButton button) {
        open(instrument.fileName(), instrument.title(), button);
    }

    private void open(String fileName, String title, JButton button) {
        button.setEnabled(false);
        FarmUi.updateStatusBadge(status,
                "Opening " + title + " in your browser…", FarmUi.Tone.INFO);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                Path page = materializer.materialize(fileName);
                opener.open(page);
                return null;
            }

            @Override protected void done() {
                button.setEnabled(true);
                try {
                    get();
                    String message = title
                            + " opened in your browser. Download results from that page to keep them.";
                    FarmUi.updateStatusBadge(status, message, FarmUi.Tone.SUCCESS);
                    host.setStatus(message);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showOpenFailure(title);
                } catch (ExecutionException exception) {
                    showOpenFailure(title);
                }
            }
        }.execute();
    }

    private void showOpenFailure(String title) {
        String message = "Could not open " + title
                + ". Check that a browser is available, then retry.";
        FarmUi.updateStatusBadge(status, message, FarmUi.Tone.DANGER);
        host.setStatus(message);
    }

    private static void browse(Path page) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("No system browser is available.");
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            throw new IOException("The system does not support opening a browser.");
        }
        desktop.browse(page.toUri());
    }

    @FunctionalInterface interface PageMaterializer {
        Path materialize(String fileName) throws IOException;
    }

    @FunctionalInterface interface PageOpener {
        void open(Path page) throws IOException;
    }

    @FunctionalInterface interface SelectedTextWriter {
        Path write(Path destination, byte[] content, boolean allowReplace)
                throws IOException;
    }

    @FunctionalInterface interface OpenFilePicker {
        Optional<Path> choose(Component parent);

        static OpenFilePicker swing() {
            return parent -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setName("fieldScience.exportDraft.fileChooser");
                chooser.getAccessibleContext().setAccessibleName("Choose a saved tool results file");
                chooser.setDialogTitle("Choose results downloaded from your tool");
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "Tool results and spreadsheets (*.eco, *.txt, *.csv)", "eco", "txt", "csv"));
                chooser.setAcceptAllFileFilterUsed(false);
                return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
                        ? Optional.ofNullable(chooser.getSelectedFile()).map(File::toPath)
                        : Optional.empty();
            };
        }
    }
}
