package com.flowerfarm.gui;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.gui.automation.SwingHarnessBridge;
import com.flowerfarm.gui.automation.SwingHarnessPlugin;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingFileHarness;
import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.CrmTab;
import com.flowerfarm.gui.tabs.DashboardTab;
import com.flowerfarm.gui.tabs.EventServicesTab;
import com.flowerfarm.gui.tabs.FlowerFarmTab;
import com.flowerfarm.gui.tabs.HarvestLogTab;
import com.flowerfarm.gui.tabs.InventoryTab;
import com.flowerfarm.gui.tabs.KnowledgeLogTab;
import com.flowerfarm.gui.tabs.MarketDayTab;
import com.flowerfarm.gui.tabs.PnwPlantGuideTab;
import com.flowerfarm.gui.tabs.ReportsTab;
import com.flowerfarm.gui.tabs.RoseVisualizerTab;
import com.flowerfarm.gui.tabs.SeasonPlannerTab;
import com.flowerfarm.gui.tabs.SyncHistoryTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.gui.tabs.TrendAnalysisTab;
import com.flowerfarm.model.Customer;
import com.flowerfarm.model.CustomerOrder;
import com.flowerfarm.model.HarvestEntry;
import com.flowerfarm.model.Item;
import com.flowerfarm.model.KnowledgeConfidence;
import com.flowerfarm.model.KnowledgeEntry;
import com.flowerfarm.model.KnowledgeKind;
import com.flowerfarm.model.OrderLine;
import com.flowerfarm.model.SeasonPlanEntry;
import com.flowerfarm.model.SeasonPlanStatus;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SeasonPlanService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Accessibility-driven worker journeys")
class UserJourneyHarnessTest {

    @BeforeAll
    static void installDesktopTheme() {
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
    }

    @Test
    @DisplayName("worker researches a wet-site native and verifies university provenance")
    void researchesRegionalPlantLikeAWorker() {
        PnwPlantGuideTab tab = new PnwPlantGuideTab();
        SwingUserHarness user = SwingUserHarness.on(build(tab)).layoutAt(1400, 900);

        user.type("Plant guide search", "wet")
                .select("Plant type filter", "Native shrub")
                .select("Plant origin filter", "PNW native")
                .sortTable("plant.guide.table", "Plant");

        assertThat(user.tableRows("plant.guide.table")).isGreaterThanOrEqualTo(2);
        user.selectRow("plant.guide.table", "Plant", "Pacific ninebark")
                .awaitText("Use where roots can reach consistent moisture")
                .select("Plant research source selector",
                        "WSU Kitsap County Master Gardeners — Clinics, gardens, "
                                + "and gardening resources")
                .awaitText("https://extension.wsu.edu/kitsap/")
                .click("Clear plant filters");

        assertThat(user.tableRows("plant.guide.table")).isGreaterThanOrEqualTo(90);
        assertThat(user.transcript()).containsSubsequence(
                "TYPE Plant guide search = \"wet\"",
                "SELECT Plant type filter = Native shrub",
                "SELECT Plant origin filter = PNW native",
                "ROW plant.guide.table WHERE Plant = Pacific ninebark",
                "CLICK Clear plant filters");
    }

    @Test
    @DisplayName("worker adjusts every rose growth slider through production listeners")
    void adjustsGrowthVisualizerLikeAWorker() {
        RoseVisualizerTab tab = new RoseVisualizerTab(
                mock(InventoryService.class), new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab)).layoutAt(1400, 900);

        user.setSlider("Rose growth recursion depth", 5)
                .setSlider("Rose branch angle", 32)
                .setSlider("Rose stem segment length", 12)
                .setSlider("Rose branching bias", 65)
                .awaitText("Depth: 5")
                .awaitText("Branch angle: 32°")
                .awaitText("Stem segment: 12 px")
                .awaitText("Branching bias: 65%");

        assertThat(user.transcript()).contains(
                "SLIDER Rose growth recursion depth = 5",
                "SLIDER Rose branch angle = 32",
                "SLIDER Rose stem segment length = 12",
                "SLIDER Rose branching bias = 65");
    }

    @Test
    @DisplayName("worker builds a private memorial-media brief and sends it to CRM")
    void plansMemorialMediaLikeAWorker() {
        HarnessHost host = new HarnessHost();
        EventServicesTab tab = new EventServicesTab(host);
        SwingUserHarness user = SwingUserHarness.on(build(tab)).layoutAt(1400, 900);

        user.select("Event occasion filter", "Memorial / funeral")
                .select("Event service category filter", "Photography & media")
                .selectRows("eventServices.table", 0, 1)
                .type("Event or family name", "Chen family memorial")
                .type("Event date", "2026-09-12")
                .type("Event venue or locations", "Harbor Chapel")
                .type("Estimated guest count", "85")
                .click("Build selected-service brief");

        assertThat(user.text("eventServices.feedback")).isEqualTo("2 services in brief");

        assertThat(user.fieldValue("Editable event service brief"))
                .contains("Chen family memorial", "Harbor Chapel",
                        "Memorial photography",
                        "Tribute photo and video coordination", "family/client and venue permission",
                        "no public image sharing by default");

        user.click("Start CRM order brief")
                .awaitText("Brief sent to Customers & Orders");

        assertThat(host.selectedTabs).contains("Customers & Orders");
        assertThat(host.eventBrief)
                .contains("EVENT BRIEF", "Chen family memorial", "Tribute photo and video");
        assertThat(user.transcript()).containsSubsequence(
                "SELECT Event occasion filter = Memorial / funeral",
                "SELECT Event service category filter = Photography & media",
                "ROWS eventServices.table = [0, 1]",
                "CLICK Build selected-service brief",
                "CLICK Start CRM order brief");
    }

    @Test
    @DisplayName("worker starts with a flower product, enters economics, and saves inventory")
    void addsFlowerProductLikeAWorker() {
        InventoryService inventory = mock(InventoryService.class);
        HarnessHost host = new HarnessHost();
        AddItemTab tab = new AddItemTab(inventory, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));

        user.select("Flower product starter library", "Wedding flower bucket")
                .click("Use starter")
                .type("Selling price", "85.00")
                .type("Cost per unit", "31.25")
                .type("Inventory quantity", "6")
                .type("Item notes", "Designer pickup · white and blush palette");

        assertThat(user.text("inventory.unitProfit")).isEqualTo("$53.75");
        assertThat(user.text("inventory.marginRate")).isEqualTo("63%");
        assertThat(user.text("inventory.openingValue")).isEqualTo("$510.00");

        user.click("Add item to inventory")
                .awaitText("Added “Wedding Flower Bucket”");

        ArgumentCaptor<Item> savedItem = ArgumentCaptor.forClass(Item.class);
        verify(inventory).addItem(savedItem.capture());
        assertThat(savedItem.getValue())
                .extracting(Item::getName, Item::getCategory, Item::getUnit,
                        Item::getPrice, Item::getCost, Item::getQuantity)
                .containsExactly("Wedding Flower Bucket", "Flowers/Plants", "Per Unit",
                        85.0, 31.25, 6d);
        assertThat(savedItem.getValue().getNotes())
                .isEqualTo("Designer pickup · white and blush palette")
                .doesNotContain("Type:");
        assertThat(host.refreshCount).isEqualTo(1);
        assertThat(user.transcript()).containsSubsequence(
                "SELECT Flower product starter library = Wedding flower bucket",
                "CLICK Use starter",
                "TYPE Selling price = \"85.00\"",
                "CLICK Add item to inventory");
    }

    @Test
    @DisplayName("worker corrects invalid economics and submits from the keyboard")
    void recoversFromValidationAndSubmitsWithEnter() {
        InventoryService inventory = mock(InventoryService.class);
        SwingUserHarness user = SwingUserHarness.on(
                build(new AddItemTab(inventory, new HarnessHost())));

        user.select("Flower product starter library", "Market bouquet")
                .click("Use starter")
                .type("Selling price", "-5")
                .type("Cost per unit", "11.50")
                .type("Inventory quantity", "8")
                .click("Add item to inventory")
                .awaitText("Check the highlighted field");

        verifyNoInteractions(inventory);

        user.type("Selling price", "32.00")
                .awaitText("Changed · ready to retry")
                .setChecked("Keep shared inventory setup after saving", true)
                .pressEnter("Inventory quantity")
                .awaitText("Added “Market Bouquet” · setup kept");

        ArgumentCaptor<Item> savedItem = ArgumentCaptor.forClass(Item.class);
        verify(inventory).addItem(savedItem.capture());
        assertThat(savedItem.getValue().getPrice()).isEqualTo(32.0);
        assertThat(savedItem.getValue().getCost()).isEqualTo(11.5);
        assertThat(user.transcript()).contains("ENTER Inventory quantity");
    }

    @Test
    @DisplayName("data-entry matrix rejects malformed inventory economics before accepting Unicode")
    void exercisesInventoryBoundaryDataMatrix() {
        InventoryService inventory = mock(InventoryService.class);
        SwingUserHarness user = SwingUserHarness.on(
                build(new AddItemTab(inventory, new HarnessHost())));

        user.select("Flower product starter library", "Market bouquet")
                .click("Use starter")
                .typeCase("blank name",
                        SwingUserHarness.input("Item name", "   "),
                        SwingUserHarness.input("Selling price", "32.00"),
                        SwingUserHarness.input("Cost per unit", "11.50"),
                        SwingUserHarness.input("Inventory quantity", "8"))
                .click("Add item to inventory")
                .awaitText("Check the highlighted field")
                .typeCase("non-finite price",
                        SwingUserHarness.input("Item name", "Market Bouquet"),
                        SwingUserHarness.input("Selling price", "NaN"),
                        SwingUserHarness.input("Cost per unit", "11.50"),
                        SwingUserHarness.input("Inventory quantity", "8"))
                .click("Add item to inventory")
                .awaitText("Check the highlighted field")
                .typeCase("text cost",
                        SwingUserHarness.input("Selling price", "32.00"),
                        SwingUserHarness.input("Cost per unit", "twelve"),
                        SwingUserHarness.input("Inventory quantity", "8"))
                .click("Add item to inventory")
                .awaitText("Check the highlighted field")
                .typeCase("fractional quantity",
                        SwingUserHarness.input("Cost per unit", "12.00"),
                        SwingUserHarness.input("Inventory quantity", "1.5"))
                .click("Add item to inventory")
                .awaitText("Check the highlighted field");

        verify(inventory, never()).addItem(any(Item.class));

        user.typeCase("valid Unicode and punctuation",
                        SwingUserHarness.input("Item name",
                                "  Café Dahlia – Bride’s Mix  "),
                        SwingUserHarness.input("Selling price", "32.75"),
                        SwingUserHarness.input("Cost per unit", "12.00"),
                        SwingUserHarness.input("Inventory quantity", "9"),
                        SwingUserHarness.input("Item notes",
                                "Saturday pickup · 10:30 AM — cooler #2"))
                .click("Add item to inventory")
                .awaitText("Added “Café Dahlia – Bride’s Mix”");

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(inventory).addItem(saved.capture());
        assertThat(saved.getValue())
                .extracting(Item::getName, Item::getPrice, Item::getCost,
                        Item::getQuantity, Item::getNotes)
                .containsExactly("Café Dahlia – Bride’s Mix", 32.75, 12.0, 9d,
                        "Saturday pickup · 10:30 AM — cooler #2");
        assertThat(user.transcript()).containsSubsequence(
                "CASE blank name", "CASE non-finite price", "CASE text cost",
                "CASE fractional quantity", "CASE valid Unicode and punctuation");
    }

    @Test
    @DisplayName("worker waits through a slow inventory save, keeps the draft, and retries failure")
    void preservesInventoryDraftAcrossSlowFailedSave() throws InterruptedException {
        InventoryService inventory = mock(InventoryService.class);
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        when(inventory.addItem(any(Item.class)))
                .thenAnswer(invocation -> {
                    saveStarted.countDown();
                    if (!releaseSave.await(3, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release test save");
                    }
                    throw new IllegalStateException("Inventory database unavailable");
                })
                .thenAnswer(invocation -> invocation.getArgument(0));
        SwingUserHarness user = SwingUserHarness.on(
                build(new AddItemTab(inventory, new HarnessHost())));

        user.select("Flower product starter library", "Market bouquet")
                .click("Use starter")
                .type("Selling price", "34.00")
                .type("Cost per unit", "12.50")
                .type("Inventory quantity", "9")
                .type("Item notes", "Saturday market · coral palette")
                .setChecked("Keep shared inventory setup after saving", true)
                .click("Add item to inventory");

        assertThat(saveStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(user.isButtonEnabled("Saving item…")).isFalse();
        assertThat(user.isFieldEditable("Selling price")).isFalse();
        releaseSave.countDown();

        user.awaitText("Item was not saved · retry");
        assertThat(user.fieldValue("Selling price")).isEqualTo("34.00");
        assertThat(user.fieldValue("Inventory quantity")).isEqualTo("9");
        assertThat(user.fieldValue("Item notes"))
                .isEqualTo("Saturday market · coral palette");
        assertThat(user.selectedOption("Flower product starter library"))
                .isEqualTo("Market bouquet");
        assertThat(user.isChecked("Keep shared inventory setup after saving")).isTrue();
        assertThat(user.isFieldEditable("Selling price")).isTrue();

        user.click("Add item to inventory")
                .awaitText("Added “Market Bouquet” · setup kept");
        verify(inventory, times(2)).addItem(any(Item.class));
    }

    @Test
    @DisplayName("worker logs a harvest and keeps context for the next cut")
    void logsHarvestLikeAWorker() {
        HarvestService harvest = mock(HarvestService.class);
        HarnessHost host = new HarnessHost();
        when(harvest.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class))).thenReturn(List.of());
        when(harvest.totalsByProductUnit()).thenReturn(Map.of());
        when(harvest.totalsByProductUnit(anyList())).thenReturn(Map.of());
        when(harvest.missingInventoryProducts(anyList())).thenReturn(List.of());
        when(harvest.add(any(HarvestEntry.class))).thenAnswer(invocation -> {
            HarvestEntry entered = invocation.getArgument(0);
            return new HarvestEntry(77L, entered.getHarvestDate(), entered.getCropName(),
                    entered.getQuantity(), entered.getUnit(), entered.getBedOrField(),
                    entered.getNotes());
        });
        HarvestLogTab tab = new HarvestLogTab(harvest, host);
        JComponent root = build(tab);
        initialize(tab);
        SwingUserHarness user = SwingUserHarness.on(root);

        user.awaitText("0 entries · 0 total")
                .type("Harvest date", LocalDate.now().minusDays(1).toString())
                .type("Harvest crop or variety", "Cafe au Lait Dahlia")
                .type("Harvest quantity", "24")
                .type("Harvest unit", "stems")
                .type("Harvest bed or field", "Field B · Row 4")
                .type("Harvest notes", "Cut at dawn; premium market grade")
                .click("Add harvest")
                .awaitText("Saved · context kept for next cut");

        ArgumentCaptor<HarvestEntry> savedHarvest = ArgumentCaptor.forClass(HarvestEntry.class);
        verify(harvest).add(savedHarvest.capture());
        assertThat(savedHarvest.getValue())
                .extracting(HarvestEntry::getCropName, HarvestEntry::getQuantity,
                        HarvestEntry::getUnit, HarvestEntry::getBedOrField,
                        HarvestEntry::getNotes)
                .containsExactly("Cafe au Lait Dahlia", 24.0, "stems",
                        "Field B · Row 4", "Cut at dawn; premium market grade");
        assertThat(host.refreshCount).isEqualTo(1);
    }

    @Test
    @DisplayName("worker keeps a failed harvest batch intact and retries it once")
    void retriesHarvestBatchWithoutReenteringTheMorningCut() {
        HarvestService harvest = mock(HarvestService.class);
        List<HarvestEntry> history = new CopyOnWriteArrayList<>();
        stubHarvestHistory(harvest, history);
        when(harvest.missingInventoryProducts(anyList())).thenReturn(List.of());
        AtomicLong nextId = new AtomicLong(100);
        when(harvest.addBatch(anyList()))
                .thenThrow(new IllegalStateException("inventory unavailable"))
                .thenAnswer(invocation -> {
                    List<HarvestEntry> submitted = invocation.getArgument(0);
                    List<HarvestEntry> saved = submitted.stream()
                            .map(entry -> harvest(nextId.getAndIncrement(), entry))
                            .toList();
                    history.addAll(saved);
                    return saved;
                });
        HarvestLogTab tab = new HarvestLogTab(harvest, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("harvest.history.table", 0)
                .selectTab("Batch entry")
                .type("Batch harvest date", LocalDate.now().toString())
                .type("Batch default unit", "stems")
                .type("Batch default bed or field", "Field North")
                .type("Batch harvest notes", "Cooler load A")
                .type("Batch harvest lines",
                        "Cafe au Lait Dahlia,18\nNootka Rose,7,bunches,Bed R2")
                .awaitText("2 rows ready")
                .awaitButtonEnabled("Log batch harvest", true)
                .click("Log batch harvest")
                .awaitText("Batch not logged · inventory unavailable · try again");

        assertThat(user.fieldValue("Batch harvest lines"))
                .isEqualTo("Cafe au Lait Dahlia,18\nNootka Rose,7,bunches,Bed R2");
        assertThat(user.fieldValue("Batch default bed or field")).isEqualTo("Field North");
        assertThat(user.fieldValue("Batch harvest notes")).isEqualTo("Cooler load A");
        user.awaitButtonEnabled("Log batch harvest", true)
                .click("Log batch harvest")
                .awaitText("2 harvest entries logged")
                .awaitTableRows("harvest.history.table", 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<HarvestEntry>> attempts = ArgumentCaptor.forClass(List.class);
        verify(harvest, times(2)).addBatch(attempts.capture());
        assertThat(attempts.getAllValues().get(1))
                .extracting(HarvestEntry::getCropName, HarvestEntry::getQuantity,
                        HarvestEntry::getUnit, HarvestEntry::getBedOrField,
                        HarvestEntry::getNotes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "Cafe au Lait Dahlia", 18.0, "stems",
                                "Field North", "Cooler load A"),
                        org.assertj.core.groups.Tuple.tuple(
                                "Nootka Rose", 7.0, "bunches",
                                "Bed R2", "Cooler load A"));
        assertThat(user.fieldValue("Batch harvest lines")).isEmpty();
    }

    @Test
    @DisplayName("worker filters, edits, and duplicates the exact harvest row")
    void editsAndDuplicatesAFilteredHarvest() {
        HarvestService harvest = mock(HarvestService.class);
        List<HarvestEntry> history = new CopyOnWriteArrayList<>(List.of(
                new HarvestEntry(21L, LocalDate.now().minusDays(1),
                        "Cafe au Lait Dahlia", 12, "stems", "Field B", "First cut"),
                new HarvestEntry(22L, LocalDate.now().minusDays(2),
                        "Nootka Rose", 30, "stems", "Rose Row", "Florist grade")));
        stubHarvestHistory(harvest, history);
        when(harvest.missingInventoryProducts(anyList())).thenReturn(List.of());
        when(harvest.update(anyLong(), any(HarvestEntry.class))).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            HarvestEntry edited = harvest(id, invocation.getArgument(1));
            history.removeIf(entry -> entry.getId().equals(id));
            history.add(edited);
            return edited;
        });
        when(harvest.add(any(HarvestEntry.class))).thenAnswer(invocation -> {
            HarvestEntry saved = harvest(23L, invocation.getArgument(0));
            history.add(saved);
            return saved;
        });
        HarnessHost host = new HarnessHost();
        HarvestLogTab tab = new HarvestLogTab(harvest, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("harvest.history.table", 2)
                .type("Filter by crop", "dahlia")
                .click("Apply filters")
                .awaitTableRows("harvest.history.table", 1)
                .doubleClickRow("harvest.history.table", 0)
                .awaitText("Editing #21")
                .type("Harvest quantity", "18")
                .type("Harvest notes", "Second flush · market grade")
                .click("Save edit")
                .awaitTableValue("harvest.history.table", 0, 3, 18.0);

        assertThat(host.lastStatus).contains("Harvest updated");
        user.selectRow("harvest.history.table", 0)
                .select("More harvest entry actions", "Duplicate selected as today")
                .click("Run entry action");
        assertThat(user.fieldValue("Harvest date")).isEqualTo(LocalDate.now().toString());
        assertThat(user.fieldValue("Harvest crop or variety"))
                .isEqualTo("Cafe au Lait Dahlia");
        assertThat(user.fieldValue("Harvest quantity")).isEqualTo("18.0");
        assertThat(user.fieldValue("Harvest notes"))
                .isEqualTo("Second flush · market grade");

        user.type("Harvest quantity", "7")
                .click("Add harvest")
                .awaitTableRows("harvest.history.table", 2);

        ArgumentCaptor<HarvestEntry> edited = ArgumentCaptor.forClass(HarvestEntry.class);
        verify(harvest).update(org.mockito.ArgumentMatchers.eq(21L), edited.capture());
        assertThat(edited.getValue())
                .extracting(HarvestEntry::getQuantity, HarvestEntry::getNotes)
                .containsExactly(18.0, "Second flush · market grade");
        ArgumentCaptor<HarvestEntry> duplicated = ArgumentCaptor.forClass(HarvestEntry.class);
        verify(harvest).add(duplicated.capture());
        assertThat(duplicated.getValue())
                .extracting(HarvestEntry::getHarvestDate, HarvestEntry::getCropName,
                        HarvestEntry::getQuantity, HarvestEntry::getBedOrField,
                        HarvestEntry::getNotes)
                .containsExactly(LocalDate.now(), "Cafe au Lait Dahlia", 7.0,
                        "Field B", "Second flush · market grade");
    }

    @Test
    @DisplayName("worker protects a filtered harvest export and confirms inventory reversal once")
    void safelyExportsAndDeletesAFilteredHarvest(@TempDir Path tempDir) throws Exception {
        HarvestService harvest = mock(HarvestService.class);
        List<HarvestEntry> history = new CopyOnWriteArrayList<>(List.of(
                new HarvestEntry(41L, LocalDate.now(), "Nootka Rose", 24,
                        "stems", "Rose Row", "Premium"),
                new HarvestEntry(42L, LocalDate.now(), "Dahlia Mix", 10,
                        "stems", "Field C", "Market")));
        stubHarvestHistory(harvest, history);
        Path export = tempDir.resolve("filtered-harvest.csv");
        Files.writeString(export, "keep existing harvest export", StandardCharsets.UTF_8);
        SwingFileHarness files = new SwingFileHarness()
                .cancelSave("Export filtered harvest view to CSV", "harvest_filtered.csv")
                .saveAs("Export filtered harvest view to CSV", "harvest_filtered.csv",
                        export.toFile())
                .saveAs("Export filtered harvest view to CSV", "harvest_filtered.csv",
                        export.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing file", false, form -> form
                        .expectText("Replace “filtered-harvest.csv”?")
                        .expectText("overwritten only if you continue"))
                .confirmForm("Replace existing file", true, form -> form
                        .expectText("Replace “filtered-harvest.csv”?")
                        .expectText("overwritten only if you continue"))
                .confirmForm("Delete harvest?", false, form -> form
                        .expectText("Delete this Nootka Rose harvest?")
                        .expectText("removed from inventory"))
                .confirmForm("Delete harvest?", true, form -> form
                        .expectText("Delete this Nootka Rose harvest?")
                        .expectText("removed from inventory"));
        doAnswer(invocation -> {
            String filename = invocation.getArgument(0);
            List<HarvestEntry> rows = invocation.getArgument(1);
            Files.writeString(Path.of(filename),
                    "crop,quantity\n" + rows.get(0).getCropName() + ","
                            + rows.get(0).getQuantity() + "\n",
                    StandardCharsets.UTF_8);
            return null;
        }).when(harvest).exportToCsv(anyString(), anyList());
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            history.removeIf(entry -> entry.getId().equals(id));
            return null;
        }).when(harvest).delete(anyLong());
        HarnessHost host = new HarnessHost();
        HarvestLogTab tab = new HarvestLogTab(harvest, host, dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("harvest.history.table", 2)
                .type("Filter by crop", "rose")
                .click("Apply filters")
                .awaitTableRows("harvest.history.table", 1)
                .select("Harvest reports and exports", "Export filtered CSV")
                .click("Create report");
        assertThat(Files.readString(export, StandardCharsets.UTF_8))
                .isEqualTo("keep existing harvest export");

        user.click("Create report");
        assertThat(Files.readString(export, StandardCharsets.UTF_8))
                .isEqualTo("keep existing harvest export");

        user.click("Create report")
                .awaitText("Harvest CSV saved · 1 visible rows");
        assertThat(Files.readString(export, StandardCharsets.UTF_8))
                .isEqualTo("crop,quantity\nNootka Rose,24.0\n");

        user.selectRow("harvest.history.table", 0)
                .select("More harvest entry actions", "Delete selected harvest")
                .click("Run entry action");
        assertThat(user.tableRows("harvest.history.table")).isEqualTo(1);
        verify(harvest, never()).delete(anyLong());

        user.click("Run entry action")
                .awaitTableRows("harvest.history.table", 0);
        verify(harvest).delete(41L);
        assertThat(host.lastStatus).contains("Inventory has been adjusted automatically");
        assertThat(dialogs.transcript()).containsExactly(
                "EXPECT Replace existing file contains \"Replace “filtered-harvest.csv”?\"",
                "EXPECT Replace existing file contains \"overwritten only if you continue\"",
                "CONFIRM Replace existing file = false",
                "EXPECT Replace existing file contains \"Replace “filtered-harvest.csv”?\"",
                "EXPECT Replace existing file contains \"overwritten only if you continue\"",
                "CONFIRM Replace existing file = true",
                "EXPECT Delete harvest? contains \"Delete this Nootka Rose harvest?\"",
                "EXPECT Delete harvest? contains \"removed from inventory\"",
                "CONFIRM Delete harvest? = false",
                "EXPECT Delete harvest? contains \"Delete this Nootka Rose harvest?\"",
                "EXPECT Delete harvest? contains \"removed from inventory\"",
                "CONFIRM Delete harvest? = true");
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker corrects a planting block, plans its succession, and removes it safely")
    void plansAndRemovesASeasonSuccessionThroughTheRealForms() {
        SeasonPlanService plans = mock(SeasonPlanService.class);
        List<SeasonPlanEntry> entries = new CopyOnWriteArrayList<>();
        stubSeasonAnalysis(plans, entries);
        AtomicLong nextId = new AtomicLong(70);
        when(plans.create(any(SeasonPlanEntry.class))).thenAnswer(invocation -> {
            SeasonPlanEntry saved = invocation.getArgument(0);
            saved.setId(nextId.getAndIncrement());
            entries.add(saved);
            return saved;
        });
        when(plans.draftSuccession(any(SeasonPlanEntry.class), anyInt()))
                .thenAnswer(invocation -> seasonSuccession(
                        invocation.getArgument(0), invocation.getArgument(1)));
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            entries.removeIf(entry -> entry.getId().equals(id));
            return null;
        }).when(plans).delete(anyLong());

        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Add planting block", true, form -> form
                        .type("Crop", "Sunflower")
                        .type("Variety", "ProCut Plum")
                        .type("Bed or field", "Field C")
                        .select("Planting status", "Planted")
                        .type("Sow date", "2026-04-01")
                        .type("Transplant date", "2026-05-01")
                        .type("Harvest start", "2026-07-01")
                        .type("Harvest end", "2026-06-20")
                        .type("Bed length in feet", "60")
                        .type("Expected stems", "450")
                        .type("Planning notes", "Saturday market succession"))
                .message("Check the planting block",
                        "Harvest end cannot be before harvest start")
                .confirmForm("Add planting block", true, form -> form
                        .expectText("ProCut Plum")
                        .expectText("Saturday market succession")
                        .type("Harvest end", "2026-08-15"))
                .confirmForm("Plan next succession", true, form -> form
                        .expectText("all dates moved 14 days")
                        .expectText("2026-04-15")
                        .expectText("2026-07-15")
                        .type("Bed or field", "Field D"))
                .confirmForm("Remove planting block", false, form -> form
                        .expectText("Sunflower · ProCut Plum in Field D")
                        .expectText("Harvest records are unchanged"))
                .confirmForm("Remove planting block", true, form -> form
                        .expectText("Sunflower · ProCut Plum in Field D")
                        .expectText("Harvest records are unchanged"));
        SeasonPlannerTab tab = new SeasonPlannerTab(plans, null, dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("No planting blocks for 2026")
                .awaitButtonEnabled("Add planting", true)
                .click("Add planting")
                .awaitTableRows("seasonPlanner.table", 1)
                .selectTab("Plan records")
                .selectRow("seasonPlanner.table", 0)
                .click("Plan succession")
                .awaitTableRows("seasonPlanner.table", 2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SeasonPlanEntry> created = ArgumentCaptor.forClass(SeasonPlanEntry.class);
        verify(plans, times(2)).create(created.capture());
        assertThat(created.getAllValues().get(0))
                .extracting(SeasonPlanEntry::getCropName, SeasonPlanEntry::getVariety,
                        SeasonPlanEntry::getBedOrField, SeasonPlanEntry::getExpectedStems,
                        SeasonPlanEntry::getStatus, SeasonPlanEntry::getNotes)
                .containsExactly("Sunflower", "ProCut Plum", "Field C", 450,
                        SeasonPlanStatus.PLANTED, "Saturday market succession");
        assertThat(created.getAllValues().get(1))
                .extracting(SeasonPlanEntry::getSowDate, SeasonPlanEntry::getHarvestStart,
                        SeasonPlanEntry::getHarvestEnd, SeasonPlanEntry::getBedOrField,
                        SeasonPlanEntry::getStatus)
                .containsExactly(LocalDate.of(2026, 4, 15), LocalDate.of(2026, 7, 15),
                        LocalDate.of(2026, 8, 29), "Field D", SeasonPlanStatus.PLANNED);

        user.click("Remove selected");
        assertThat(user.tableRows("seasonPlanner.table")).isEqualTo(2);
        verify(plans, never()).delete(anyLong());
        user.click("Remove selected")
                .awaitTableRows("seasonPlanner.table", 1);
        verify(plans).delete(71L);
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker records, filters, edits, and safely removes a linked farm lesson")
    void managesKnowledgeFollowUpThroughTheRealForm() {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        List<KnowledgeEntry> lessons = new CopyOnWriteArrayList<>(List.of(
                knowledge(80L, LocalDate.now().minusDays(5),
                        "Tunnel shade reduced afternoon stress", "Tulip", null,
                        KnowledgeConfidence.PROMISING, List.of("shade", "tunnel"))));
        stubKnowledgeSearch(knowledge, lessons);
        AtomicLong nextId = new AtomicLong(81);
        when(knowledge.create(any(KnowledgeEntry.class))).thenAnswer(invocation -> {
            KnowledgeEntry saved = knowledge(nextId.getAndIncrement(), invocation.getArgument(0));
            lessons.add(saved);
            return saved;
        });
        when(knowledge.update(anyLong(), any(KnowledgeEntry.class))).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            KnowledgeEntry saved = knowledge(id, invocation.getArgument(1));
            lessons.removeIf(entry -> entry.getId().equals(id));
            lessons.add(saved);
            return saved;
        });
        doAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            lessons.removeIf(entry -> entry.getId().equals(id));
            return null;
        }).when(knowledge).delete(anyLong());

        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Record a farm lesson", true, form -> form
                        .type("Observation date", "not-a-date")
                        .type("What happened", "Mildew slowed after earlier watering")
                        .select("Type", "Problem")
                        .type("Crop", "Nootka Rose")
                        .type("Variety", "Schoener's Nutkana")
                        .type("Bed or field", "Bed A")
                        .type("Production stage", "Budding")
                        .type("What did you notice?", "White patches after warm nights")
                        .type("What action did you take?", "Removed leaves; watered at dawn")
                        .type("Who took the action?", "Morning crew")
                        .type("What was the result?", "No new patches for four days")
                        .type("Follow-up date", LocalDate.now().toString())
                        .select("Confidence", "Promising")
                        .type("Tags", "Mildew, Irrigation, mildew")
                        .type("Harvest #", "41")
                        .type("Inventory item #", "9"))
                .message("Check this lesson", "Observation date must use YYYY-MM-DD")
                .confirmForm("Record a farm lesson", true, form -> form
                        .expectText("Mildew slowed after earlier watering")
                        .expectText("Removed leaves; watered at dawn")
                        .type("Observation date", LocalDate.now().minusDays(2).toString()))
                .confirmForm("Edit farm lesson", true, form -> form
                        .expectText("Mildew slowed after earlier watering")
                        .expectText("No new patches for four days")
                        .type("What was the result?", "No new patches after seven days")
                        .select("Confidence", "Proven on this farm"))
                .confirmForm("Remove farm lesson", false, form -> form
                        .expectText("Mildew slowed after earlier watering")
                        .expectText("customer, and order records will not change"))
                .confirmForm("Remove farm lesson", true, form -> form
                        .expectText("Mildew slowed after earlier watering")
                        .expectText("customer, and order records will not change"));
        KnowledgeLogTab tab = new KnowledgeLogTab(knowledge, null, dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("knowledge.table", 1)
                .awaitButtonEnabled("Record a lesson", true)
                .click("Record a lesson")
                .awaitTableRows("knowledge.table", 2);

        ArgumentCaptor<KnowledgeEntry> created = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledge).create(created.capture());
        assertThat(created.getValue())
                .extracting(KnowledgeEntry::getSummary, KnowledgeEntry::getKind,
                        KnowledgeEntry::getCropName, KnowledgeEntry::getFollowUpDate,
                        KnowledgeEntry::getConfidence, KnowledgeEntry::getHarvestId,
                        KnowledgeEntry::getInventoryItemId)
                .containsExactly("Mildew slowed after earlier watering", KnowledgeKind.PROBLEM,
                        "Nootka Rose", LocalDate.now(), KnowledgeConfidence.PROMISING, 41L, 9L);
        assertThat(created.getValue().getTags())
                .containsExactlyInAnyOrder("mildew", "irrigation");

        user.selectRow("knowledge.table", 1)
                .click("Edit selected")
                .awaitTableRows("knowledge.table", 2)
                .type("Search farm knowledge", "mildew")
                .pressEnter("Search farm knowledge")
                .awaitTableRows("knowledge.table", 1)
                .awaitOption("Knowledge tag filter", "mildew")
                .select("Knowledge tag filter", "mildew")
                .setChecked("Follow-up due", true)
                .awaitTableRows("knowledge.table", 1)
                .awaitButtonEnabled("Remove selected", true);

        assertThat(user.text("knowledge.detail"))
                .contains("No new patches after seven days", "Proven on this farm",
                        "Harvest #41", "Inventory item #9");
        ArgumentCaptor<KnowledgeEntry> edited = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledge).update(org.mockito.ArgumentMatchers.eq(81L), edited.capture());
        assertThat(edited.getValue())
                .extracting(KnowledgeEntry::getResult, KnowledgeEntry::getConfidence)
                .containsExactly("No new patches after seven days",
                        KnowledgeConfidence.PROVEN);

        user.click("Remove selected");
        assertThat(user.tableRows("knowledge.table")).isEqualTo(1);
        verify(knowledge, never()).delete(anyLong());
        user.click("Remove selected")
                .awaitTableRows("knowledge.table", 0);
        verify(knowledge).delete(81L);
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("knowledge search applies the latest query entered during an active refresh")
    void queuesLatestKnowledgeSearchInsteadOfShowingStaleRows() throws Exception {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        KnowledgeEntry mildew = knowledge(91L, LocalDate.now().minusDays(2),
                "Mildew response", "Rose", LocalDate.now(),
                KnowledgeConfidence.PROVEN, List.of("mildew"));
        KnowledgeEntry shade = knowledge(92L, LocalDate.now().minusDays(3),
                "Shade cloth timing", "Tulip", null,
                KnowledgeConfidence.PROMISING, List.of("shade"));
        List<KnowledgeEntry> all = List.of(mildew, shade);
        CountDownLatch refreshStarted = new CountDownLatch(1);
        CountDownLatch releaseRefresh = new CountDownLatch(1);
        AtomicInteger searches = new AtomicInteger();
        when(knowledge.search(any(KnowledgeService.SearchRequest.class)))
                .thenAnswer(invocation -> {
                    int call = searches.incrementAndGet();
                    KnowledgeService.SearchRequest request = invocation.getArgument(0);
                    if (call == 2) {
                        refreshStarted.countDown();
                        if (!releaseRefresh.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Timed out releasing lesson search");
                        }
                    }
                    return request.query().toLowerCase().contains("mildew")
                            ? List.of(mildew) : all;
                });
        when(knowledge.distinctTags()).thenReturn(List.of("mildew", "shade"));
        KnowledgeLogTab tab = new KnowledgeLogTab(knowledge, null,
                new SwingDialogHarness());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("knowledge.table", 2)
                .click("Refresh");
        assertThat(refreshStarted.await(2, TimeUnit.SECONDS)).isTrue();
        user.type("Search farm knowledge", "mildew")
                .pressEnter("Search farm knowledge")
                .awaitText("Search queued · current lesson search finishing");
        releaseRefresh.countDown();

        user.awaitTableRows("knowledge.table", 1);
        assertThat(user.tableValue("knowledge.table", 0, 1)).isEqualTo("Mildew response");
        ArgumentCaptor<KnowledgeService.SearchRequest> requests =
                ArgumentCaptor.forClass(KnowledgeService.SearchRequest.class);
        verify(knowledge, times(3)).search(requests.capture());
        assertThat(requests.getAllValues().get(2).query()).isEqualTo("mildew");
    }

    @Test
    @DisplayName("first planting draft survives a slow failed save and retries once")
    void retainsFirstSeasonDraftAcrossSlowFailedSave() throws Exception {
        SeasonPlanService plans = mock(SeasonPlanService.class);
        List<SeasonPlanEntry> entries = new CopyOnWriteArrayList<>();
        stubSeasonAnalysis(plans, entries);
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        when(plans.create(any(SeasonPlanEntry.class))).thenAnswer(invocation -> {
            SeasonPlanEntry draft = invocation.getArgument(0);
            if (attempts.incrementAndGet() == 1) {
                saveStarted.countDown();
                if (!releaseSave.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out releasing planting save");
                }
                throw new IllegalStateException("Season database unavailable");
            }
            draft.setId(101L);
            entries.add(draft);
            return draft;
        });
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Add planting block", true, form -> form
                        .type("Crop", "Dahlia")
                        .type("Variety", "Cafe au Lait")
                        .type("Bed or field", "Tunnel A")
                        .select("Planting status", "Planted")
                        .type("Sow date", "2026-03-10")
                        .type("Transplant date", "2026-05-20")
                        .type("Harvest start", "2026-08-10")
                        .type("Harvest end", "2026-10-15")
                        .type("Bed length in feet", "48")
                        .type("Expected stems", "360")
                        .type("Planning notes", "Wholesale focal stems"))
                .confirmForm("Retry planting block", true, form -> form
                        .expectText("Cafe au Lait")
                        .expectText("Tunnel A")
                        .expectText("2026-10-15")
                        .expectText("Wholesale focal stems"));
        SeasonPlannerTab tab = new SeasonPlannerTab(plans, null, dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("No planting blocks for 2026")
                .awaitButtonEnabled("Add planting", true)
                .click("Add planting")
                .awaitText("Saving planting…");
        assertThat(saveStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(user.isButtonEnabled("Saving planting…")).isFalse();
        verify(plans).create(any(SeasonPlanEntry.class));
        releaseSave.countDown();

        user.awaitText("Planting block not saved · draft kept")
                .click("Retry planting")
                .awaitTableRows("seasonPlanner.table", 1);

        ArgumentCaptor<SeasonPlanEntry> saves = ArgumentCaptor.forClass(SeasonPlanEntry.class);
        verify(plans, times(2)).create(saves.capture());
        assertThat(saves.getAllValues().get(1))
                .extracting(SeasonPlanEntry::getCropName, SeasonPlanEntry::getVariety,
                        SeasonPlanEntry::getBedOrField, SeasonPlanEntry::getBedFeet,
                        SeasonPlanEntry::getExpectedStems, SeasonPlanEntry::getStatus,
                        SeasonPlanEntry::getNotes)
                .containsExactly("Dahlia", "Cafe au Lait", "Tunnel A", 48d, 360,
                        SeasonPlanStatus.PLANTED, "Wholesale focal stems");
        assertThat(user.isButtonEnabled("Add planting")).isTrue();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("first farm lesson survives a slow failed save and retries once")
    void retainsFirstKnowledgeDraftAcrossSlowFailedSave() throws Exception {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        List<KnowledgeEntry> lessons = new CopyOnWriteArrayList<>();
        stubKnowledgeSearch(knowledge, lessons);
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        when(knowledge.create(any(KnowledgeEntry.class))).thenAnswer(invocation -> {
            KnowledgeEntry draft = invocation.getArgument(0);
            if (attempts.incrementAndGet() == 1) {
                saveStarted.countDown();
                if (!releaseSave.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out releasing lesson save");
                }
                throw new IllegalStateException("Knowledge database unavailable");
            }
            KnowledgeEntry saved = knowledge(111L, draft);
            lessons.add(saved);
            return saved;
        });
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Record a farm lesson", true, form -> form
                        .type("Observation date", "2026-08-27")
                        .type("What happened", "Shade cloth protected late lisianthus")
                        .select("Type", "Practice worth repeating")
                        .type("Crop", "Lisianthus")
                        .type("Variety", "Voyage Champagne")
                        .type("Bed or field", "Tunnel B")
                        .type("Production stage", "Flowering")
                        .type("What did you notice?", "Petal edges stayed clean after hot afternoons")
                        .type("What action did you take?", "Closed 30 percent shade at noon")
                        .type("Who took the action?", "Afternoon crew")
                        .type("What was the result?", "Marketable stems held through Friday")
                        .type("Follow-up date", "2026-09-03")
                        .select("Confidence", "Promising")
                        .type("Tags", "shade, quality"))
                .confirmForm("Retry farm lesson", true, form -> form
                        .expectText("Shade cloth protected late lisianthus")
                        .expectText("Voyage Champagne")
                        .expectText("Closed 30 percent shade at noon")
                        .expectText("Marketable stems held through Friday"));
        KnowledgeLogTab tab = new KnowledgeLogTab(knowledge, null, dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("knowledge.table", 0)
                .awaitText("No lessons matched this search")
                .awaitButtonEnabled("Record a lesson", true)
                .click("Record a lesson")
                .awaitText("Saving lesson…");
        assertThat(saveStarted.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(user.isButtonEnabled("Saving lesson…")).isFalse();
        assertThat(tab.hasPendingSave()).isTrue();
        assertThat(tab.applicationExitWarning()).contains("in progress");
        verify(knowledge).create(any(KnowledgeEntry.class));
        releaseSave.countDown();

        user.awaitText("Farm lesson not saved · draft kept")
                .awaitButtonEnabled("Retry lesson", true);
        assertThat(tab.applicationExitWarning()).contains("draft kept");
        user.click("Retry lesson")
                .awaitTableRows("knowledge.table", 1);
        assertThat(tab.hasPendingSave()).isFalse();
        assertThat(tab.applicationExitWarning()).isNull();

        ArgumentCaptor<KnowledgeEntry> saves = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(knowledge, times(2)).create(saves.capture());
        assertThat(saves.getAllValues().get(1))
                .extracting(KnowledgeEntry::getSummary, KnowledgeEntry::getKind,
                        KnowledgeEntry::getCropName, KnowledgeEntry::getVariety,
                        KnowledgeEntry::getBedOrField, KnowledgeEntry::getActionTaken,
                        KnowledgeEntry::getResult, KnowledgeEntry::getFollowUpDate,
                        KnowledgeEntry::getConfidence)
                .containsExactly("Shade cloth protected late lisianthus",
                        KnowledgeKind.PRACTICE, "Lisianthus", "Voyage Champagne", "Tunnel B",
                        "Closed 30 percent shade at noon",
                        "Marketable stems held through Friday", LocalDate.of(2026, 9, 3),
                        KnowledgeConfidence.PROMISING);
        assertThat(saves.getAllValues().get(1).getTags())
                .containsExactlyInAnyOrder("shade", "quality");
        assertThat(user.isButtonEnabled("Record a lesson")).isTrue();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("zero-activity report preview explains the empty product and customer views")
    void presentsActionableZeroActivityReportStates() {
        ReportService reports = mock(ReportService.class);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 7);
        when(reports.preview(from, to)).thenReturn(new ReportService.ReportPreview(
                from, to, 0, 0, 0, 0, 0, 0, List.of(), List.of()));
        ReportsTab tab = new ReportsTab(reports, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));

        user.type("Report start date", from.toString())
                .type("Report end date", to.toString())
                .click("Preview range")
                .awaitText("Range preview ready · no activity found")
                .awaitText("No harvest or order activity was recorded")
                .selectTab("Products")
                .awaitText("No product performance to show");
        assertThat(user.tableRows("reports.productPerformance")).isZero();

        user.selectTab("Customers")
                .awaitText("No customer performance to show");
        assertThat(user.tableRows("reports.customerPerformance")).isZero();
        assertThat(user.text("reports.preview.harvest")).isEqualTo("0 logs");
        assertThat(user.text("reports.preview.orders")).isEqualTo("0 orders");
        verify(reports).preview(from, to);
    }

    @Test
    @DisplayName("worker previews a range and reviews product and customer performance")
    void exploresRangeReportsLikeAWorker() {
        ReportService reports = mock(ReportService.class);
        HarnessHost host = new HarnessHost();
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 28);
        ReportService.ReportPreview preview = new ReportService.ReportPreview(
                from, to, 18, 7, 3, 4, 2480.0, 1765.0,
                List.of(
                        new ReportService.ProductPerformance(
                                "Market Bouquet", "each", 42, 6, 1680, 1120, 40),
                        new ReportService.ProductPerformance(
                                "Nootka Rose", "stems", 160, 3, 800, 645, 5)),
                List.of(
                        new ReportService.CustomerPerformance(
                                "Kitsap Blooms", 4, 1620, 1140, 405),
                        new ReportService.CustomerPerformance(
                                "Saturday Market", 3, 860, 625, 286.67)));
        when(reports.preview(from, to)).thenReturn(preview);
        SwingUserHarness user = SwingUserHarness.on(
                build(new ReportsTab(reports, host)));

        user.type("Report start date", from.toString())
                .type("Report end date", to.toString())
                .awaitText("28 days selected · 2026-08-01 through 2026-08-28")
                .click("Preview range")
                .awaitText("Range preview ready")
                .selectTab("Products");

        assertThat(user.tableRows("reports.productPerformance")).isEqualTo(2);
        assertThat(user.tableValue("reports.productPerformance", 0, 0))
                .isEqualTo("Market Bouquet");
        assertThat(user.tableValue("reports.productPerformance", 0, 5))
                .isEqualTo("$1,680.00");

        user.selectTab("Customers");
        assertThat(user.tableRows("reports.customerPerformance")).isEqualTo(2);
        assertThat(user.tableValue("reports.customerPerformance", 0, 0))
                .isEqualTo("Kitsap Blooms");
        verify(reports).preview(from, to);
        assertThat(host.lastStatus).contains("Report preview ready");
    }

    @Test
    @DisplayName("worker keeps a report range after preview failure and retries")
    void recoversFromReportPreviewFailureWithoutLosingRange() {
        ReportService reports = mock(ReportService.class);
        LocalDate from = LocalDate.of(2026, 8, 4);
        LocalDate to = LocalDate.of(2026, 8, 18);
        ReportService.ReportPreview recovered = new ReportService.ReportPreview(
                from, to, 9, 3, 2, 1, 860, 415, List.of(), List.of());
        when(reports.preview(from, to))
                .thenThrow(new IllegalStateException("Reporting database unavailable"))
                .thenReturn(recovered);
        SwingUserHarness user = SwingUserHarness.on(
                build(new ReportsTab(reports, new HarnessHost())));

        user.type("Report start date", from.toString())
                .type("Report end date", to.toString())
                .click("Preview range")
                .awaitText("Preview needs attention");

        assertThat(user.fieldValue("Report start date")).isEqualTo(from.toString());
        assertThat(user.fieldValue("Report end date")).isEqualTo(to.toString());
        assertThat(user.isButtonEnabled("Preview range")).isTrue();
        assertThat(user.text("reports.summary"))
                .contains("Previewing the report range")
                .contains("Reporting database unavailable");

        user.click("Preview range")
                .awaitText("Range preview ready");
        assertThat(user.text("reports.preview.harvest")).isEqualTo("9 logs");
        assertThat(user.text("reports.preview.committedRevenue")).isEqualTo("$860.00");
        verify(reports, times(2)).preview(from, to);
    }

    @Test
    @DisplayName("worker chooses a crop window and audits the forecast evidence")
    void exploresTrendEvidenceLikeAWorker() {
        TrendService trends = mock(TrendService.class);
        HarnessHost host = new HarnessHost();
        LocalDate firstWeek = LocalDate.of(2026, 7, 6);
        LocalDate latestWeek = LocalDate.of(2026, 8, 10);
        LocalDate forecastWeek = LocalDate.of(2026, 8, 17);
        when(trends.listSeries()).thenReturn(List.of(
                new TrendService.TrendSeriesSummary(
                        "Nootka Rose", "stems", 6, 13, firstWeek, latestWeek)));
        TrendService.TrendResult forecast = new TrendService.TrendResult(
                84, "Harvest production forecast\nNootka Rose evidence", null,
                "Nootka Rose", "stems", 6, 72, latestWeek, forecastWeek,
                List.of(
                        new TrendService.TrendPoint(firstWeek, 42, 40),
                        new TrendService.TrendPoint(firstWeek.plusWeeks(1), 48, 48),
                        new TrendService.TrendPoint(firstWeek.plusWeeks(2), 51, 56),
                        new TrendService.TrendPoint(firstWeek.plusWeeks(3), 63, 64),
                        new TrendService.TrendPoint(firstWeek.plusWeeks(4), 68, 72),
                        new TrendService.TrendPoint(latestWeek, 72, 80)),
                8, 32, 0.88);
        when(trends.analyzeQuantityTrend("Nootka Rose", "stems", 6))
                .thenReturn(forecast);
        SwingUserHarness user = SwingUserHarness.on(
                build(new TrendAnalysisTab(trends, host)));

        user.awaitOption("Harvest crop and unit series", "Nootka Rose · stems · 6 wk")
                .select("Harvest crop and unit series", "Nootka Rose · stems · 6 wk")
                .select("Trend analysis window", "Latest 6 weeks")
                .click("Analyze trends")
                .awaitText("Forecast ready");

        assertThat(user.text("trend.forecast")).isEqualTo("84 stems");
        assertThat(user.text("trend.reliability")).isEqualTo("Strong");
        assertThat(user.text("trend.freshness")).isEqualTo("Current");
        assertThat(user.tableRows("trend.weeklyTable")).isEqualTo(7);
        assertThat(user.tableValue("trend.weeklyTable", 6, 2))
                .isEqualTo("84 stems forecast");
        verify(trends).analyzeQuantityTrend("Nootka Rose", "stems", 6);
        assertThat(host.lastStatus).contains("Nootka Rose").contains("84 stems");
    }

    @Test
    @DisplayName("worker keeps the selected trend series and window after analysis failure")
    void recoversFromTrendFailureWithoutLosingSelection() {
        TrendService trends = mock(TrendService.class);
        LocalDate firstWeek = LocalDate.of(2026, 7, 20);
        LocalDate latestWeek = LocalDate.of(2026, 8, 10);
        when(trends.listSeries()).thenReturn(List.of(
                new TrendService.TrendSeriesSummary(
                        "Dahlia Mix", "stems", 4, 8, firstWeek, latestWeek)));
        TrendService.TrendResult recovered = new TrendService.TrendResult(
                72, "Dahlia recovery forecast", null, "Dahlia Mix", "stems", 4,
                64, latestWeek, latestWeek.plusWeeks(1),
                List.of(
                        new TrendService.TrendPoint(firstWeek, 40, 40),
                        new TrendService.TrendPoint(firstWeek.plusWeeks(1), 48, 48),
                        new TrendService.TrendPoint(firstWeek.plusWeeks(2), 56, 56),
                        new TrendService.TrendPoint(latestWeek, 64, 64)),
                8, 24, 0.91);
        when(trends.analyzeQuantityTrend("Dahlia Mix", "stems", 6))
                .thenThrow(new IllegalStateException("Harvest history unavailable"))
                .thenReturn(recovered);
        SwingUserHarness user = SwingUserHarness.on(
                build(new TrendAnalysisTab(trends, new HarnessHost())));

        user.awaitOption("Harvest crop and unit series", "Dahlia Mix · stems · 4 wk")
                .select("Harvest crop and unit series", "Dahlia Mix · stems · 4 wk")
                .select("Trend analysis window", "Latest 6 weeks")
                .click("Analyze trends")
                .awaitText("Analysis needs attention");

        assertThat(user.text("trend.forecast")).isEqualTo("Unavailable");
        assertThat(user.selectedOption("Harvest crop and unit series"))
                .isEqualTo("Dahlia Mix · stems · 4 wk");
        assertThat(user.selectedOption("Trend analysis window")).isEqualTo("Latest 6 weeks");
        assertThat(user.isButtonEnabled("Analyze trends")).isTrue();

        user.click("Analyze trends")
                .awaitText("Forecast ready");
        assertThat(user.text("trend.forecast")).isEqualTo("72 stems");
        assertThat(user.text("trend.reliability")).isEqualTo("Strong");
        verify(trends, times(2)).analyzeQuantityTrend("Dahlia Mix", "stems", 6);
    }

    @Test
    @DisplayName("trend analysis discards an active result and runs the latest user selection")
    void coalescesTrendAnalysisToTheLatestSeriesAndWindow() throws Exception {
        TrendService trends = mock(TrendService.class);
        LocalDate firstWeek = LocalDate.of(2026, 7, 13);
        LocalDate latestWeek = LocalDate.of(2026, 8, 17);
        when(trends.listSeries()).thenReturn(List.of(
                new TrendService.TrendSeriesSummary(
                        "Dahlia Mix", "stems", 6, 12, firstWeek, latestWeek),
                new TrendService.TrendSeriesSummary(
                        "Nootka Rose", "stems", 6, 11, firstWeek, latestWeek)));
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(trends.analyzeQuantityTrend(anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    int call = calls.incrementAndGet();
                    if (call == 1) {
                        firstStarted.countDown();
                        assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                        return trendResult("Dahlia Mix", 41, firstWeek, latestWeek);
                    }
                    secondStarted.countDown();
                    assertThat(releaseSecond.await(5, TimeUnit.SECONDS)).isTrue();
                    return trendResult("Nootka Rose", 96, firstWeek, latestWeek);
                });
        SwingUserHarness user = SwingUserHarness.on(
                build(new TrendAnalysisTab(trends, new HarnessHost())));

        user.awaitOption("Harvest crop and unit series", "Dahlia Mix · stems · 6 wk")
                .select("Harvest crop and unit series", "Dahlia Mix · stems · 6 wk")
                .select("Trend analysis window", "Latest 6 weeks")
                .click("Analyze trends");
        assertThat(firstStarted.await(5, TimeUnit.SECONDS)).isTrue();

        user.select("Harvest crop and unit series", "Nootka Rose · stems · 6 wk")
                .select("Trend analysis window", "Latest 12 weeks")
                .awaitText("Selection changed · latest analysis queued");
        releaseFirst.countDown();
        assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
        user.awaitText("Analyzing Nootka Rose");
        assertThat(user.text("trend.forecast")).isEqualTo("Analyzing…");
        assertThat(user.selectedOption("Harvest crop and unit series"))
                .isEqualTo("Nootka Rose · stems · 6 wk");
        assertThat(user.selectedOption("Trend analysis window"))
                .isEqualTo("Latest 12 weeks");

        releaseSecond.countDown();
        user.awaitText("Forecast ready");
        assertThat(user.text("trend.forecast")).isEqualTo("96 stems");
        assertThat(calls).hasValue(2);
        verify(trends).analyzeQuantityTrend("Dahlia Mix", "stems", 6);
        verify(trends).analyzeQuantityTrend("Nootka Rose", "stems", 12);
    }

    @Test
    @DisplayName("worker searches inventory and opens the lowest-stock count queue")
    void searchesAndPrioritizesInventoryLikeAWorker() {
        InventoryService inventory = mock(InventoryService.class);
        List<Item> items = List.of(
                item(1L, "Nootka Rose", 42),
                item(2L, "Dahlia Mix", 7),
                item(3L, "Sweet Pea", 3),
                new Item(4L, "Wedding Flower Bucket", "Flowers/Plants",
                        85, "Per Unit", 31.25, 15, "Designer pickup"));
        when(inventory.getAllItems()).thenReturn(items);
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("4 items")
                .type("Inventory search", "Nootka")
                .awaitTableRows("inventory.table", 1);
        assertThat(user.tableValue("inventory.table", 0, 0)).isEqualTo("Nootka Rose");

        user.type("Inventory search", "")
                .setChecked("Show low-stock inventory only", true)
                .awaitTableRows("inventory.table", 2);
        assertThat(user.tableValue("inventory.table", 0, 0)).isEqualTo("Sweet Pea");
        assertThat(user.tableValue("inventory.table", 0, 5)).isEqualTo(3);

        user.selectRow("inventory.table", 0)
                .awaitText("Sweet Pea · 3 Per Stem");
        assertThat(user.transcript()).contains(
                "CHECK Show low-stock inventory only = true",
                "ROW inventory.table = 0");
    }

    @Test
    @DisplayName("worker keeps filtered inventory visible when refresh fails and retries")
    void recoversFromInventoryRefreshFailureWithoutLosingContext() {
        InventoryService inventory = mock(InventoryService.class);
        List<Item> initial = List.of(
                item(1L, "Nootka Rose", 42),
                item(2L, "Dahlia Mix", 7));
        List<Item> recovered = List.of(
                item(1L, "Nootka Rose", 55),
                item(2L, "Dahlia Mix", 7));
        when(inventory.getAllItems())
                .thenReturn(initial)
                .thenThrow(new IllegalStateException("Inventory database unavailable"))
                .thenReturn(recovered);
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("2 items")
                .type("Inventory search", "Rose")
                .awaitTableRows("inventory.table", 1)
                .click("Refresh")
                .awaitText("Inventory refresh failed · retry");

        assertThat(user.fieldValue("Inventory search")).isEqualTo("Rose");
        assertThat(user.tableRows("inventory.table")).isEqualTo(1);
        assertThat(user.tableValue("inventory.table", 0, 5)).isEqualTo(42);
        assertThat(user.isButtonEnabled("Refresh")).isTrue();

        user.click("Refresh")
                .awaitTableValue("inventory.table", 0, 5, 55);
        assertThat(user.fieldValue("Inventory search")).isEqualTo("Rose");
        verify(inventory, times(3)).getAllItems();
    }

    @Test
    @DisplayName("worker enters a physical count through the native inventory form")
    void recordsPhysicalStockCountThroughDialogLikeAWorker() {
        InventoryService inventory = mock(InventoryService.class);
        Item original = item(1L, "Nootka Rose", 42);
        Item counted = item(1L, "Nootka Rose", 37);
        when(inventory.getAllItems())
                .thenReturn(List.of(original))
                .thenReturn(List.of(counted));
        when(inventory.updateById(anyLong(), any(Item.class)))
                .thenReturn(counted);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .enterSpinnerAndChoose(
                        "Count “Nootka Rose”",
                        "Counted quantity for Nootka Rose",
                        37,
                        "Save count");
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("inventory.table", 1)
                .selectRow("inventory.table", 0)
                .click("Record stock count…")
                .awaitTableValue("inventory.table", 0, 5, 37)
                .awaitText("Nootka Rose saved · 37 Per Stem");

        ArgumentCaptor<Item> savedCount = ArgumentCaptor.forClass(Item.class);
        verify(inventory).updateById(anyLong(), savedCount.capture());
        assertThat(savedCount.getValue().getQuantity()).isEqualTo(37);
        assertThat(user.tableRows("inventory.table")).isEqualTo(1);
        assertThat(dialogs.transcript())
                .containsExactly(
                        "EDIT Count “Nootka Rose” · SPINNER Counted quantity for Nootka Rose = 37",
                        "OPTION Count “Nootka Rose” = Save count");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels inventory deletion before explicitly confirming it")
    void cancelsThenConfirmsInventoryDeletion() {
        InventoryService inventory = mock(InventoryService.class);
        Item product = item(2L, "Dahlia Mix", 7);
        when(inventory.getAllItems())
                .thenReturn(List.of(product))
                .thenReturn(List.of());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Delete inventory item?", false)
                .confirm("Delete inventory item?", true);
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("inventory.table", 1)
                .selectRow("inventory.table", 0)
                .click("Delete selected");
        assertThat(user.tableRows("inventory.table")).isEqualTo(1);
        verify(inventory, never()).deleteById(anyLong());

        user.click("Delete selected")
                .awaitTableRows("inventory.table", 0)
                .awaitText("Dahlia Mix deleted");
        verify(inventory).deleteById(2L);
        assertThat(dialogs.transcript()).containsExactly(
                "CONFIRM Delete inventory item? = false",
                "CONFIRM Delete inventory item? = true");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker corrects an invalid inventory edit inside the native modal form")
    void correctsInventoryEditInsideDialogWithoutLosingDraft() {
        InventoryService inventory = mock(InventoryService.class);
        Item original = item(1L, "Nootka Rose", 42);
        Item updated = new Item(1L, "Nootka Rose", "Flowers/Plants",
                5.25, "Per Stem", 1.1, 42, "Premium florist grade");
        when(inventory.getAllItems()).thenReturn(List.of(original), List.of(updated));
        when(inventory.updateById(anyLong(), any(Item.class))).thenReturn(updated);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Edit “Nootka Rose”", true, form -> form
                        .expectText("All values stay in place if a field needs correction.")
                        .type("Selling price", "-1")
                        .type("Item notes", "Premium florist grade"))
                .message("Check the highlighted field", "Selling price must be zero or greater")
                .confirmForm("Edit “Nootka Rose”", true, form -> form
                        .expectText("Selling price ($) *")
                        .type("Selling price", "5.25"));
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("inventory.table", 1)
                .selectRow("inventory.table", 0)
                .click("Edit selected")
                .awaitTableValue("inventory.table", 0, 2, "5.25")
                .awaitTableValue("inventory.table", 0, 6, "Premium florist grade")
                .awaitText("Nootka Rose updated");

        ArgumentCaptor<Item> saved = ArgumentCaptor.forClass(Item.class);
        verify(inventory).updateById(anyLong(), saved.capture());
        assertThat(saved.getValue().getPrice()).isEqualTo(5.25);
        assertThat(saved.getValue().getNotes()).isEqualTo("Premium florist grade");
        assertThat(dialogs.transcript()).contains(
                "MESSAGE Check the highlighted field",
                "EDIT Edit “Nootka Rose” · TYPE Selling price = \"5.25\"");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("duplicate inventory rename retains every modal edit for a safe retry")
    void retainsInventoryDraftAfterDuplicateRename() {
        InventoryService inventory = mock(InventoryService.class);
        Item original = new Item(11L, "Nootka Rose", "Flowers/Plants",
                4.5, "Per Stem", 1.2, 2, "Field A");
        Item conflict = new Item(22L, "Dahlia Mix", "Flowers/Plants",
                3.5, "Per Stem", 1.0, 12, "Field B");
        Item updated = new Item(11L, "Nootka Rose Premium", "Flowers/Plants",
                5.0, "Per Stem", 1.2, 7, "Keep this full draft");
        when(inventory.getAllItems())
                .thenReturn(List.of(original, conflict), List.of(updated, conflict));
        when(inventory.updateById(anyLong(), any(Item.class)))
                .thenThrow(new InventoryService.DuplicateItemNameException(22L, "Dahlia Mix"))
                .thenReturn(updated);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Edit “Nootka Rose”", true, form -> form
                        .type("Item name", "Dahlia Mix")
                        .type("Selling price", "5.00")
                        .type("Inventory quantity", "7")
                        .type("Item notes", "Keep this full draft"))
                .confirmForm("Edit “Nootka Rose”", true, form -> form
                        .expectText("Dahlia Mix")
                        .expectText("5.00")
                        .expectText("Keep this full draft")
                        .type("Item name", "Nootka Rose Premium"));
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("inventory.table", 2)
                .selectRow("inventory.table", 0)
                .click("Edit selected")
                .awaitText("Nootka Rose not renamed · name already used");
        assertThat(user.isButtonEnabled("Review existing SKU")).isTrue();

        user.click("Edit selected")
                .awaitTableValue("inventory.table", 0, 0, "Nootka Rose Premium")
                .awaitTableValue("inventory.table", 0, 2, "5.00")
                .awaitTableValue("inventory.table", 0, 5, 7)
                .awaitText("Nootka Rose Premium updated");

        ArgumentCaptor<Item> attempts = ArgumentCaptor.forClass(Item.class);
        verify(inventory, times(2)).updateById(anyLong(), attempts.capture());
        assertThat(attempts.getAllValues().get(1))
                .extracting(Item::getName, Item::getPrice, Item::getQuantity, Item::getNotes)
                .containsExactly("Nootka Rose Premium", 5.0, 7d, "Keep this full draft");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels an inventory export before saving the same captured rows")
    void cancelsThenCompletesVisibleInventoryExport(@TempDir Path tempDir) throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.getAllItems()).thenReturn(List.of(item(1L, "Nootka Rose", 42)));
        Path export = tempDir.resolve("filtered-inventory.csv");
        SwingDialogHarness dialogs = new SwingDialogHarness();
        SwingFileHarness files = new SwingFileHarness()
                .cancelSave("Export Visible Rows to CSV", "inventory_visible.csv")
                .saveAs("Export Visible Rows to CSV", "inventory_visible.csv", export.toFile());
        HarnessHost host = new HarnessHost();
        InventoryTab tab = new InventoryTab(inventory, host, dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("inventory.table", 1)
                .type("Inventory search", "Nootka")
                .click("Export visible CSV…")
                .awaitText("Inventory export cancelled · no file changed");
        assertThat(export).doesNotExist();
        assertThat(host.lastStatus).isEqualTo(
                "Inventory export cancelled. No file was changed.");

        user.click("Export visible CSV…")
                .awaitText("Inventory CSV saved · 1 visible rows");
        assertThat(Files.readString(export, StandardCharsets.UTF_8))
                .contains("Name,Category,Price,Unit,Cost,Quantity,Notes")
                .contains("Nootka Rose,Flowers/Plants,4.50,Per Stem,1.10,42,Field grown");
        assertThat(host.lastStatus).isEqualTo(
                "1 row(s) exported to filtered-inventory.csv");
        assertThat(files.transcript()).hasSize(2);
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels a market-day packing export without changing the reviewed load")
    void cancelsMarketDayPackingExport() {
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.PackLine line = new MarketDayPackingService.PackLine(
                "Nootka Rose", "stems", 20, 4.5, 90, 901L);
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        501L, "Harbor Floral Studio", "FLORIST", "Maya Chen",
                        "360-555-0148", "orders@harborfloral.example", "CONFIRMED",
                        marketDate, "Cooler pickup", List.of(line), 90, "PICKUP");
        MarketDayPackingService.ProductNeed covered =
                new MarketDayPackingService.ProductNeed(
                        "Nootka Rose", "stems", 20, 42, false, 0);
        when(packing.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(marketPlan(marketDate, customer, covered, 0));
        SwingDialogHarness dialogs = new SwingDialogHarness();
        SwingFileHarness files = new SwingFileHarness().cancelSave(
                "Save market-day packing export",
                "market-day-packing-" + marketDate + ".pdf");
        HarnessHost host = new HarnessHost();
        MarketDayTab tab = new MarketDayTab(packing, host, dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("stock OK");
        String reviewedPlan = user.fieldValue("Market day packing list");
        user
                .click("Export file…")
                .awaitText("Export cancelled · ready");

        assertThat(user.fieldValue("Market day packing list"))
                .isEqualTo(reviewedPlan);
        assertThat(host.lastStatus).isEqualTo(
                "Market-day export cancelled. No file was changed.");
        verify(packing, never()).generatePackingPdf(any());
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels an activity export while keeping the current filters")
    void cancelsActivityHistoryExport() {
        SyncHistoryService history = mock(SyncHistoryService.class);
        when(history.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenReturn(List.of());
        when(history.stats(anyList()))
                .thenReturn(new SyncHistoryService.HistoryStats(0, 0, 0));
        when(history.distinctConnectors(anyInt())).thenReturn(List.of());
        when(history.distinctOperations(anyInt())).thenReturn(List.of());
        SwingDialogHarness dialogs = new SwingDialogHarness();
        SwingFileHarness files = new SwingFileHarness().cancelSave(
                "Export the current activity list", "farm_activity_history.csv");
        SyncHistoryTab tab = new SyncHistoryTab(
                history, new HarnessHost(), dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitButtonEnabled("Export CSV…", true)
                .click("Export CSV…")
                .awaitText("CSV not saved · exports ready");

        assertThat(user.text("activity.exportStatus"))
                .isEqualTo("CSV not saved · exports ready");
        verify(history, never()).exportToCsv(anyString(), anyList());
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels and protects a rose PNG before replacing it atomically")
    void safelyCancelsThenReplacesRosePng(@TempDir Path tempDir) throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.getAllItems()).thenReturn(List.of());
        Path png = tempDir.resolve("reviewed-rose.png");
        byte[] original = "keep reviewed rose image".getBytes(StandardCharsets.UTF_8);
        Files.write(png, original);
        SwingFileHarness files = new SwingFileHarness()
                .cancelSave("Export L-System rose as PNG", "Nootka_Rose-lsystem.png")
                .saveAs("Export L-System rose as PNG", "Nootka_Rose-lsystem.png",
                        png.toFile())
                .saveAs("Export L-System rose as PNG", "Nootka_Rose-lsystem.png",
                        png.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing PNG?", false, form -> form
                        .expectText("reviewed-rose.png")
                        .expectText("Replace it?"))
                .confirmForm("Replace existing PNG?", true, form -> form
                        .expectText("reviewed-rose.png")
                        .expectText("Replace it?"));
        HarnessHost host = new HarnessHost();
        RoseVisualizerTab tab = new RoseVisualizerTab(inventory, host, dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.click("Export PNG…")
                .awaitText("PNG export cancelled");
        assertThat(Files.readAllBytes(png)).isEqualTo(original);

        user.click("Export PNG…")
                .awaitText("PNG not saved · existing file kept");
        assertThat(Files.readAllBytes(png)).isEqualTo(original);

        user.click("Export PNG…")
                .awaitText("PNG saved · reviewed-rose.png");
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(png.toFile());
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isGreaterThanOrEqualTo(640);
        assertThat(image.getHeight()).isGreaterThanOrEqualTo(520);
        assertThat(host.lastStatus).isEqualTo(
                "Exported rose visualizer PNG → reviewed-rose.png");
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels and protects an existing farm report before replacing it")
    void safelyCancelsThenReplacesFarmReport(@TempDir Path tempDir) throws Exception {
        ReportService reports = mock(ReportService.class);
        byte[] reportBytes = "%PDF-reviewed-farm-report".getBytes(StandardCharsets.UTF_8);
        when(reports.generateWeeklyReportPdf()).thenReturn(reportBytes);
        Path report = tempDir.resolve("weekly-report.pdf");
        byte[] existing = "keep existing report".getBytes(StandardCharsets.UTF_8);
        Files.write(report, existing);
        String suggested = "flowerfarm-weekly-" + LocalDate.now() + ".pdf";
        SwingFileHarness files = new SwingFileHarness()
                .cancelSave("Save farm report PDF", suggested)
                .saveAs("Save farm report PDF", suggested, report.toFile())
                .saveAs("Save farm report PDF", suggested, report.toFile());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing report?", false, form -> form
                        .expectText("weekly-report.pdf")
                        .expectText("Replace it?"))
                .confirmForm("Replace existing report?", true, form -> form
                        .expectText("weekly-report.pdf")
                        .expectText("Replace it?"));
        HarnessHost host = new HarnessHost();
        ReportsTab tab = new ReportsTab(reports, host, dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));

        user.click("Create 7-day report…")
                .awaitText("Report ready — save cancelled")
                .awaitButtonEnabled("Create 7-day report…", true);
        assertThat(Files.readAllBytes(report)).isEqualTo(existing);
        assertThat(host.lastStatus).isEqualTo(
                "Report save cancelled. No file was changed.");

        user.click("Create 7-day report…")
                .awaitText("Report not saved · existing file kept")
                .awaitButtonEnabled("Create 7-day report…", true);
        assertThat(Files.readAllBytes(report)).isEqualTo(existing);
        assertThat(host.lastStatus).isEqualTo(
                "Report not saved. Existing file kept: weekly-report.pdf");

        user.click("Create 7-day report…")
                .awaitText("Report saved")
                .awaitButtonEnabled("Create 7-day report…", true);
        assertThat(Files.readAllBytes(report)).isEqualTo(reportBytes);
        assertThat(host.lastStatus).isEqualTo("PDF report saved: weekly-report.pdf");
        verify(reports, times(3)).generateWeeklyReportPdf();
        files.assertComplete();
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker refuses an existing export replacement before explicitly overwriting it")
    void cancelsThenConfirmsExistingInventoryExportReplacement(
            @TempDir Path tempDir) throws Exception {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.getAllItems()).thenReturn(List.of(item(1L, "Nootka Rose", 42)));
        Path export = tempDir.resolve("existing-inventory.csv");
        Files.writeString(export, "keep this file", StandardCharsets.UTF_8);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing file", false, form -> form
                        .expectText("Replace “existing-inventory.csv”?")
                        .expectText("overwritten only if you continue"))
                .confirmForm("Replace existing file", true, form -> form
                        .expectText("Replace “existing-inventory.csv”?")
                        .expectText("overwritten only if you continue"));
        SwingFileHarness files = new SwingFileHarness()
                .saveAs("Export Visible Rows to CSV", "inventory_visible.csv", export.toFile())
                .saveAs("Export Visible Rows to CSV", "inventory_visible.csv", export.toFile());
        HarnessHost host = new HarnessHost();
        InventoryTab tab = new InventoryTab(inventory, host, dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("inventory.table", 1)
                .click("Export visible CSV…")
                .awaitText("Inventory export not saved · existing file kept");
        assertThat(Files.readString(export, StandardCharsets.UTF_8))
                .isEqualTo("keep this file");
        assertThat(host.lastStatus).isEqualTo(
                "Inventory export not saved. Existing file kept: existing-inventory.csv");

        user.click("Export visible CSV…")
                .awaitText("Inventory CSV saved · 1 visible rows");
        assertThat(Files.readString(export, StandardCharsets.UTF_8))
                .startsWith("Name,Category,Price,Unit,Cost,Quantity,Notes")
                .contains("Nootka Rose");
        assertThat(host.lastStatus).isEqualTo(
                "1 row(s) exported to existing-inventory.csv");
        dialogs.assertComplete();
        files.assertComplete();
    }

    @Test
    @DisplayName("worker creates a complete florist customer account")
    void createsCustomerLikeAWorker() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        List<Customer> savedCustomers = new CopyOnWriteArrayList<>();
        when(customers.search(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, String.class).toLowerCase();
            return savedCustomers.stream()
                    .filter(customer -> customer.getName().toLowerCase().contains(query))
                    .toList();
        });
        when(customers.getAll()).thenAnswer(invocation -> List.copyOf(savedCustomers));
        when(customers.add(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(501L);
            savedCustomers.add(customer);
            return customer;
        });
        stubEmptyOrders(orders);
        HarnessHost host = new HarnessHost();
        CrmTab tab = new CrmTab(customers, orders, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .type("Customer or business name", "Harbor Floral Studio")
                .type("Customer contact person", "Maya Chen")
                .type("Customer phone", "360-555-0148")
                .type("Customer email", "orders@harborfloral.example")
                .select("Customer type", "Florist")
                .type("Customer notes", "Thursday pickup · text on arrival")
                .click("Add customer")
                .awaitTableRows("crm.customers.table", 1)
                .awaitText("Customer saved · “Harbor Floral Studio”")
                .doubleClickRow("crm.customers.table", 0)
                .awaitText("Editing “Harbor Floral Studio”");

        assertThat(user.fieldValue("Customer or business name"))
                .isEqualTo("Harbor Floral Studio");
        assertThat(user.fieldValue("Customer notes"))
                .isEqualTo("Thursday pickup · text on arrival");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).add(saved.capture());
        assertThat(saved.getValue())
                .extracting(Customer::getName, Customer::getContactName,
                        Customer::getPhone, Customer::getEmail,
                        Customer::getCustomerType, Customer::getNotes)
                .containsExactly("Harbor Floral Studio", "Maya Chen", "360-555-0148",
                        "orders@harborfloral.example", "FLORIST",
                        "Thursday pickup · text on arrival");
        assertThat(host.lastStatus).contains(
                "Loaded “Harbor Floral Studio”. Update the fields, then choose Save edit.");
    }

    @Test
    @DisplayName("worker corrects a customer email without losing the account draft")
    void recoversFromCustomerValidationWithoutLosingDraft() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        List<Customer> savedCustomers = new CopyOnWriteArrayList<>();
        when(customers.search(anyString())).thenAnswer(invocation ->
                List.copyOf(savedCustomers));
        when(customers.getAll()).thenAnswer(invocation -> List.copyOf(savedCustomers));
        when(customers.add(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(502L);
            savedCustomers.add(customer);
            return customer;
        });
        stubEmptyOrders(orders);
        CrmTab tab = new CrmTab(customers, orders, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .type("Customer or business name", "Sound Floral Co-op")
                .type("Customer contact person", "Alex Morgan")
                .type("Customer email", "orders at soundfloral")
                .type("Customer phone", "360-555-0161")
                .select("Customer type", "Florist")
                .type("Customer notes", "Friday dock pickup")
                .click("Add customer")
                .awaitText("Customer not saved · correct highlighted field");

        verify(customers, never()).add(any(Customer.class));
        assertThat(user.fieldValue("Customer or business name"))
                .isEqualTo("Sound Floral Co-op");
        assertThat(user.fieldValue("Customer notes")).isEqualTo("Friday dock pickup");

        user.type("Customer email", "orders@soundfloral.example")
                .awaitText("Changed · ready to retry customer")
                .click("Add customer")
                .awaitTableRows("crm.customers.table", 1)
                .awaitText("Customer saved · “Sound Floral Co-op”");

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).add(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("orders@soundfloral.example");
        assertThat(saved.getValue().getNotes()).isEqualTo("Friday dock pickup");
    }

    @Test
    @DisplayName("worker creates a two-product pickup order and keeps its exact target")
    void createsMultiProductOrderLikeAWorker() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST",
                "Use the cooler entrance");
        List<CustomerOrder> savedOrders = new CopyOnWriteArrayList<>();
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenAnswer(invocation -> List.copyOf(savedOrders));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        when(orders.findByCustomer(anyLong()))
                .thenAnswer(invocation -> List.copyOf(savedOrders));
        when(orders.createReviewed(anyLong(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean())).thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(1);
                    String status = invocation.getArgument(2);
                    String notes = invocation.getArgument(3);
                    String handoff = invocation.getArgument(4);
                    List<OrderLine> lines = invocation.getArgument(5);
                    CustomerOrder created = new CustomerOrder(
                            customer, date, status, notes, handoff);
                    created.setId(91L);
                    lines.forEach(created::addLine);
                    savedOrders.add(created);
                    return created;
                });
        when(orders.addLineReviewed(anyLong(),
                any(OrderService.OrderReviewSnapshot.class), any(OrderLine.class)))
                .thenAnswer(invocation -> {
                    CustomerOrder target = savedOrders.get(0);
                    target.addLine(invocation.getArgument(2));
                    return target;
                });
        HarnessHost host = new HarnessHost();
        CrmTab tab = new CrmTab(customers, orders, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectTab("Orders")
                .awaitOption("New order customer", "Kitsap Blooms")
                .select("New order customer", "Kitsap Blooms")
                .type("Order date", LocalDate.now().toString())
                .select("New order handoff type", "Pickup")
                .type("Order notes", "Thursday 10 AM · cooler entrance")
                .type("Order product name", "Nootka Rose")
                .type("Order quantity", "40")
                .type("Order unit", "stems")
                .type("Order unit price", "4.50")
                .click("Create & select order")
                .awaitTableValue("crm.orders.table", 0, 5, 1);

        user.type("Order product name", "Dahlia Mix")
                .type("Order quantity", "6")
                .type("Order unit", "bunches")
                .type("Order unit price", "18.00")
                .click("Add product to selected order")
                .awaitTableValue("crm.orders.table", 0, 5, 2);

        assertThat(savedOrders).singleElement().satisfies(order -> {
            assertThat(order.getHandoffType()).isEqualTo("PICKUP");
            assertThat(order.getNotes()).isEqualTo("Thursday 10 AM · cooler entrance");
            assertThat(order.getLines()).extracting(OrderLine::getProductName)
                    .containsExactly("Nootka Rose", "Dahlia Mix");
            assertThat(order.lineTotal()).isEqualTo(288.0);
        });
        verify(orders).addLineReviewed(anyLong(),
                any(OrderService.OrderReviewSnapshot.class), any(OrderLine.class));
        assertThat(host.lastStatus).contains("Added “Dahlia Mix” to order #91");
    }

    @Test
    @DisplayName("worker corrects an order date and keeps every product and handoff detail")
    void recoversFromOrderValidationWithoutLosingDraft() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(8L, "Olympic Event Florals", "Jordan Lee",
                "orders@olympicevents.example", "360-555-0112", "FLORIST", "");
        List<CustomerOrder> savedOrders = new CopyOnWriteArrayList<>();
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenAnswer(invocation -> List.copyOf(savedOrders));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        when(orders.findByCustomer(anyLong()))
                .thenAnswer(invocation -> List.copyOf(savedOrders));
        when(orders.createReviewed(anyLong(), any(LocalDate.class), anyString(), anyString(),
                anyString(), anyList(), anyMap(), anyBoolean())).thenAnswer(invocation -> {
                    CustomerOrder created = new CustomerOrder(
                            customer, invocation.getArgument(1), invocation.getArgument(2),
                            invocation.getArgument(3), invocation.getArgument(4));
                    created.setId(92L);
                    List<OrderLine> lines = invocation.getArgument(5);
                    lines.forEach(created::addLine);
                    savedOrders.add(created);
                    return created;
                });
        CrmTab tab = new CrmTab(customers, orders, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectTab("Orders")
                .pressShortcut("crm.orderPanel", KeyStroke.getKeyStroke("F6"),
                        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .awaitOption("New order customer", "Olympic Event Florals")
                .select("New order customer", "Olympic Event Florals")
                .type("Order date", "08/31/2026")
                .select("New order handoff type", "Delivery")
                .type("Order notes", "Venue loading dock · call on approach")
                .type("Order product name", "Cafe au Lait Dahlia")
                .type("Order quantity", "36")
                .type("Order unit", "stems")
                .type("Order unit price", "5.75")
                .pressEnter("Order unit price")
                .awaitText("Order not created · check order date");

        verify(orders, never()).findByCustomer(anyLong());
        verify(orders, never()).createReviewed(anyLong(), any(LocalDate.class), anyString(),
                anyString(), anyString(), anyList(), anyMap(), anyBoolean());
        assertThat(user.fieldValue("Order product name")).isEqualTo("Cafe au Lait Dahlia");
        assertThat(user.fieldValue("Order quantity")).isEqualTo("36");
        assertThat(user.fieldValue("Order notes"))
                .isEqualTo("Venue loading dock · call on approach");
        assertThat(user.selectedOption("New order handoff type")).isEqualTo("Delivery");

        LocalDate correctedDate = LocalDate.now().plusDays(3);
        user.type("Order date", correctedDate.toString())
                .awaitText("Changed · ready to retry order")
                .pressShortcut("crm.orderPanel", KeyStroke.getKeyStroke("ctrl ENTER"),
                        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .awaitTableRows("crm.orders.table", 1)
                .awaitTableValue("crm.orders.table", 0, 5, 1);

        assertThat(savedOrders).singleElement().satisfies(order -> {
            assertThat(order.getOrderDate()).isEqualTo(correctedDate);
            assertThat(order.getHandoffType()).isEqualTo("DELIVERY");
            assertThat(order.getNotes()).isEqualTo("Venue loading dock · call on approach");
            assertThat(order.getLines()).singleElement().satisfies(line ->
                    assertThat(line.getProductName()).isEqualTo("Cafe au Lait Dahlia"));
        });
    }

    @Test
    @DisplayName("worker reviews exact CRM order consequences before confirming once")
    void cancelsThenConfirmsReviewedOrderLikeAWorker() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST",
                "Use the cooler entrance");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Call before delivery", "DELIVERY");
        draft.setId(42L);
        draft.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Call before delivery", "DELIVERY");
        confirmed.setId(42L);
        confirmed.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft), List.of(confirmed));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Confirm order for packing", false, form -> form
                        .expectText("Confirm order #42 · Kitsap Blooms")
                        .expectText("Products: 1")
                        .expectText("Inventory is not deducted until Complete order"))
                .confirmForm("Confirm order for packing", true, form -> form
                        .expectText("12 stems Dahlia Mix")
                        .expectText("Order total: $42.00")
                        .expectText("Call before delivery"));
        HarnessHost host = new HarnessHost();
        CrmTab tab = new CrmTab(customers, orders, host, dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectTab("Orders")
                .awaitTableRows("crm.orders.table", 1)
                .selectRow("crm.orders.table", 0)
                .click("Confirm order");
        assertThat(host.lastStatus).contains("Confirmation cancelled for order #42");
        verify(orders, never()).confirmReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));

        user.click("Confirm order")
                .awaitText("Order #42 confirmed once · review packing");
        verify(orders).confirmReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));
        assertThat(dialogs.transcript())
                .contains("CONFIRM Confirm order for packing = false",
                        "CONFIRM Confirm order for packing = true");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker reviews exact cancellation consequences before cancelling once")
    void cancelsThenApprovesOrderCancellationLikeAWorker() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST", "");
        CustomerOrder draft = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Cooler pickup", "PICKUP");
        draft.setId(44L);
        draft.addLine(new OrderLine("Nootka Rose", 10, "stems", 4.5));
        CustomerOrder cancelled = new CustomerOrder(
                customer, LocalDate.now(), "CANCELLED", "Cooler pickup", "PICKUP");
        cancelled.setId(44L);
        cancelled.addLine(new OrderLine("Nootka Rose", 10, "stems", 4.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(draft), List.of(cancelled));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Cancel order", false, form -> form
                        .expectText("Cancel order #44 · Kitsap Blooms · $45.00?")
                        .expectText("It does not deduct or restore inventory")
                        .expectText("Cancelled orders remain as history"))
                .confirmForm("Cancel order", true, form -> form
                        .expectText("10 stems Nootka Rose")
                        .expectText("Cooler pickup")
                        .expectText("cannot be reopened through this action"));
        CrmTab tab = new CrmTab(customers, orders, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectTab("Orders")
                .awaitTableRows("crm.orders.table", 1)
                .selectRow("crm.orders.table", 0)
                .select("More order actions", "Cancel selected order")
                .click("Run action");
        verify(orders, never()).cancelReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));

        user.click("Run action")
                .awaitText("Order #44 cancelled");
        verify(orders).cancelReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker reviews inventory consequences before completing an order once")
    void cancelsThenCompletesReviewedOrderLikeAWorker() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST", "");
        CustomerOrder confirmed = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Loaded at west dock", "DELIVERY");
        confirmed.setId(43L);
        confirmed.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        CustomerOrder completed = new CustomerOrder(
                customer, LocalDate.now(), "FULFILLED", "Loaded at west dock", "DELIVERY");
        completed.setId(43L);
        completed.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(confirmed), List.of(completed));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Complete order", false, form -> form
                        .expectText("Complete order #43 · Kitsap Blooms · $42.00?")
                        .expectText("deduct 12 stems inventory units")
                        .expectText("Continue only after the physical"))
                .confirmForm("Complete order", true, form -> form
                        .expectText("Loaded at west dock")
                        .expectText("completion service will still recheck live inventory")
                        .expectText("deducts the listed matching inventory now"));
        CrmTab tab = new CrmTab(customers, orders, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectTab("Orders")
                .awaitTableRows("crm.orders.table", 1)
                .selectRow("crm.orders.table", 0)
                .click("Complete order");
        verify(orders, never()).fulfillReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));

        user.click("Complete order")
                .awaitText("Order #43 completed once · inventory deducted");
        verify(orders).fulfillReviewed(
                anyLong(), any(OrderService.OrderReviewSnapshot.class));
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker cancels invoice saves and replacement before approving exact PDF bytes")
    void cancelsThenReplacesSelectedOrderInvoice(
            @TempDir Path tempDir) throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST", "");
        CustomerOrder order = new CustomerOrder(
                customer, LocalDate.now(), "CONFIRMED", "Cooler pickup", "PICKUP");
        order.setId(43L);
        order.addLine(new OrderLine("Dahlia Mix", 12, "stems", 3.5));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(order));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        byte[] invoiceBytes = {37, 80, 68, 70, 45, 49};
        when(orders.generateInvoicePdf(43L)).thenReturn(invoiceBytes);
        Path invoice = tempDir.resolve("kitsap-invoice.pdf");
        byte[] existingBytes = "keep reviewed invoice".getBytes(StandardCharsets.UTF_8);
        Files.write(invoice, existingBytes);
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirmForm("Replace existing file", false, form -> form
                        .expectText("Replace “kitsap-invoice.pdf”?")
                        .expectText("overwritten only if you continue"))
                .confirmForm("Replace existing file", true, form -> form
                        .expectText("Replace “kitsap-invoice.pdf”?")
                        .expectText("overwritten only if you continue"));
        SwingFileHarness files = new SwingFileHarness()
                .cancelSave("Save order invoice PDF", "invoice-order-43.pdf")
                .saveAs("Save order invoice PDF", "invoice-order-43.pdf", invoice.toFile())
                .saveAs("Save order invoice PDF", "invoice-order-43.pdf", invoice.toFile());
        CrmTab tab = new CrmTab(
                customers, orders, new HarnessHost(), dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectTab("Orders")
                .awaitTableRows("crm.orders.table", 1)
                .selectRow("crm.orders.table", 0)
                .select("More order actions", "Save invoice")
                .click("Run action");
        assertThat(Files.readAllBytes(invoice)).isEqualTo(existingBytes);
        verify(orders, never()).generateInvoicePdf(anyLong());

        user.click("Run action")
                .awaitButtonEnabled("Run action", true);
        assertThat(Files.readAllBytes(invoice)).isEqualTo(existingBytes);
        verify(orders, never()).generateInvoicePdf(anyLong());

        user.click("Run action")
                .awaitText("Invoice saved · order #43");
        assertThat(Files.readAllBytes(invoice)).isEqualTo(invoiceBytes);
        verify(orders).generateInvoicePdf(43L);
        dialogs.assertComplete();
        files.assertComplete();
    }

    @Test
    @DisplayName("worker closes and refuses statement replacement before approving exact PDF bytes")
    void closesThenReplacesCustomerStatement(
            @TempDir Path tempDir) throws Exception {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        CustomerStatementService statements = mock(CustomerStatementService.class);
        Customer customer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST", "");
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);
        CustomerStatementService.CustomerStatement statement =
                new CustomerStatementService.CustomerStatement(
                        to, from, to, 7L, "Kitsap Blooms", "Sam Rivera",
                        "orders@kitsapblooms.example", "360-555-0184", "FLORIST",
                        1, 42, 0, 0, 0, 42, List.of(),
                        "KITSAP BLOOMS ACCOUNT STATEMENT\nOrder #43 · paid $42.00");
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        stubEmptyOrders(orders);
        when(statements.build(7L, from, to)).thenReturn(statement);
        byte[] statementBytes = {37, 80, 68, 70, 45, 83};
        when(statements.generatePdf(statement)).thenReturn(statementBytes);
        String title = "Statement — Kitsap Blooms (" + from + " → " + to + ")";
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm(title, "Close", form -> form
                        .expectText("KITSAP BLOOMS ACCOUNT STATEMENT")
                        .expectText("Order #43 · paid $42.00"))
                .chooseForm(title, "Export PDF…", form -> form
                        .expectText("KITSAP BLOOMS ACCOUNT STATEMENT")
                        .expectText("Order #43 · paid $42.00"))
                .confirmForm("Replace existing file", false, form -> form
                        .expectText("Replace “kitsap-statement.pdf”?")
                        .expectText("overwritten only if you continue"))
                .chooseForm(title, "Export PDF…", form -> form
                        .expectText("KITSAP BLOOMS ACCOUNT STATEMENT")
                        .expectText("Order #43 · paid $42.00"))
                .confirmForm("Replace existing file", true, form -> form
                        .expectText("Replace “kitsap-statement.pdf”?")
                        .expectText("overwritten only if you continue"));
        Path statementFile = tempDir.resolve("kitsap-statement.pdf");
        byte[] existingBytes = "keep reviewed statement".getBytes(StandardCharsets.UTF_8);
        Files.write(statementFile, existingBytes);
        SwingFileHarness files = new SwingFileHarness()
                .saveAs("Save customer statement PDF", "statement-customer-7.pdf",
                        statementFile.toFile())
                .saveAs("Save customer statement PDF", "statement-customer-7.pdf",
                        statementFile.toFile());
        CrmTab tab = new CrmTab(customers, orders, statements, null,
                new HarnessHost(), dialogs, files);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Customers and orders current")
                .selectRow("crm.customers.table", 0)
                .click("Statement PDF…")
                .awaitText("Statement ready · 1 order");
        assertThat(Files.readAllBytes(statementFile)).isEqualTo(existingBytes);
        verify(statements, never()).generatePdf(any());

        user.click("Statement PDF…")
                .awaitButtonEnabled("Statement PDF…", true);
        assertThat(Files.readAllBytes(statementFile)).isEqualTo(existingBytes);
        verify(statements, never()).generatePdf(any());

        user.click("Statement PDF…")
                .awaitText("Statement PDF saved · 1 orders");
        assertThat(Files.readAllBytes(statementFile)).isEqualTo(statementBytes);
        verify(statements, times(3)).build(7L, from, to);
        verify(statements).generatePdf(statement);
        dialogs.assertComplete();
        files.assertComplete();
    }

    @Test
    @DisplayName("worker follows exact Market Day order and product handoffs then returns to packing")
    void followsExactMarketOrderAndProductHandoffs() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer otherCustomer = new Customer(8L, "Olympic Event Florals", "Jordan Lee",
                "orders@olympicevents.example", "360-555-0112", "FLORIST", "");
        Customer targetCustomer = new Customer(7L, "Kitsap Blooms", "Sam Rivera",
                "orders@kitsapblooms.example", "360-555-0184", "FLORIST",
                "Use the rear cooler entrance");
        CustomerOrder other = new CustomerOrder(otherCustomer, LocalDate.now(),
                "CONFIRMED", "Front desk", "DELIVERY");
        other.setId(201L);
        OrderLine otherLine = new OrderLine("Nootka Rose", 10, "stems", 4.5);
        otherLine.setId(701L);
        other.addLine(otherLine);
        CustomerOrder target = new CustomerOrder(targetCustomer, LocalDate.now(),
                "CONFIRMED", "Thursday 10 AM · cooler entrance", "PICKUP");
        target.setId(202L);
        OrderLine rose = new OrderLine("Nootka Rose", 20, "stems", 4.5);
        rose.setId(702L);
        target.addLine(rose);
        OrderLine dahlia = new OrderLine("Dahlia Mix", 6, "bunches", 18);
        dahlia.setId(703L);
        target.addLine(dahlia);
        when(customers.search(anyString())).thenReturn(List.of(otherCustomer, targetCustomer));
        when(customers.getAll()).thenReturn(List.of(otherCustomer, targetCustomer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of(other, target));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());

        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        MarketDayPackingService.PackLine packLine =
                new MarketDayPackingService.PackLine(
                        "Dahlia Mix", "bunches", 6, 18, 108, 703L);
        MarketDayPackingService.CustomerPack pack =
                new MarketDayPackingService.CustomerPack(
                        202L, "Kitsap Blooms", "FLORIST", "Sam Rivera",
                        "360-555-0184", "orders@kitsapblooms.example", "CONFIRMED",
                        LocalDate.now(), "Thursday 10 AM · cooler entrance",
                        List.of(packLine), 108, "PICKUP");
        MarketDayPackingService.ProductNeed unitIssue =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "bunches", 6, 20, false, 0,
                        true, "stems", false);
        MarketDayPackingService.MarketDayPlan plan =
                new MarketDayPackingService.MarketDayPlan(
                        LocalDate.now(), LocalDate.now(), LocalDate.now(), "CONFIRMED",
                        1, 108, List.of(pack), List.of(unitIssue), 1,
                        "MARKET DAY PACKING LIST\nDahlia Mix unit review");
        when(packing.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(plan);

        JourneyWorkspaceHost host = new JourneyWorkspaceHost();
        CrmTab crmTab = new CrmTab(customers, orders, host);
        MarketDayTab marketDayTab = new MarketDayTab(packing, host);
        JTabbedPane workspace = onEdt(() -> {
            JTabbedPane tabs = new JTabbedPane();
            tabs.addTab("Market Day", marketDayTab.getUIComponent());
            tabs.addTab("Customers & Orders", crmTab.getUIComponent());
            host.attach(tabs, crmTab);
            return tabs;
        });
        SwingUserHarness user = SwingUserHarness.on(workspace);
        initialize(crmTab);
        initialize(marketDayTab);

        user.awaitText("1 packing issue")
                .click("Open order #202")
                .awaitText("Reviewing order #202 from Market Day")
                .awaitText("Selected #202 · Kitsap Blooms · Confirmed");
        assertThat(user.fieldValue("Order customer filter")).isEmpty();
        assertThat(user.selectedOption("Order status filter")).isEqualTo("All statuses");
        assertThat(user.fieldValue("Selected order saved instructions"))
                .isEqualTo("Thursday 10 AM · cooler entrance");
        assertThat(user.selectedOption("Selected order handoff type")).isEqualTo("Pickup");

        user.click("Return to Market Day")
                .awaitText("1 packing issue")
                .click("Review unit #202")
                .awaitText("Correcting Dahlia Mix on order #202 from Market Day")
                .awaitText("Editing “Dahlia Mix” · save changes or cancel");
        assertThat(user.text("crm.selectedOrder"))
                .isEqualTo("Editing “Dahlia Mix” · save changes or cancel");
        assertThat(user.selectedOption("Products on the selected order"))
                .contains("Dahlia Mix", "6", "bunches");
        assertThat(user.fieldValue("Order product name")).isEqualTo("Dahlia Mix");
        assertThat(user.fieldValue("Order quantity")).isEqualTo("6");
        assertThat(user.fieldValue("Order unit")).isEqualTo("bunches");
        assertThat(user.fieldValue("Order unit price")).isEqualTo("18.00");

        user.click("Return to Market Day")
                .awaitText("1 packing issue");
        assertThat(host.selectedTabs).containsExactly(
                "Customers & Orders", "Market Day",
                "Customers & Orders", "Market Day");
        assertThat(host.lastStatus).contains("order #202");
    }

    @Test
    @DisplayName("worker routes a market-day shortfall, rebuilds, and checks the load packed")
    void resolvesMarketDayShortfallAndPacksLikeAWorker() {
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.PackLine line =
                new MarketDayPackingService.PackLine(
                        "Dahlia Mix", "stems", 12, 10, 120, 701L);
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        301L, "Kitsap Blooms", "FLORIST", "Sam Rivera",
                        "360-555-0184", "orders@kitsapblooms.example", "CONFIRMED",
                        marketDate, "Rear cooler pickup", List.of(line), 120, "PICKUP");
        MarketDayPackingService.ProductNeed shortfall =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 12, 5, true, 7);
        MarketDayPackingService.ProductNeed covered =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 12, 30, false, 0);
        MarketDayPackingService.MarketDayPlan blocked = marketPlan(
                marketDate, customer, shortfall, 1);
        MarketDayPackingService.MarketDayPlan ready = marketPlan(
                marketDate, customer, covered, 0);
        when(packing.buildPlan(any(LocalDate.class), anyInt(),
                anyBoolean(), anyBoolean())).thenReturn(blocked, ready);
        HarnessHost host = new HarnessHost();
        MarketDayTab tab = new MarketDayTab(packing, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("1 stock gap")
                .click("Resolve stock shortfalls");
        assertThat(host.inventoryProducts).containsExactly("Dahlia Mix");
        assertThat(host.selectedTabs).contains("Inventory");

        user.setSpinner("Packing window in days", 3)
                .awaitText("Packing scope changed · rebuild required")
                .click("Build packing list")
                .awaitText("stock OK")
                .setChecked("Order 301 packed for Kitsap Blooms on Pickup · " + marketDate, true)
                .awaitText("1 checked · ready to complete");

        assertThat(user.text("marketDay.packingProgress"))
                .isEqualTo("1 of 1 packed · 0 remaining");
        assertThat(user.named("marketDay.fulfillConfirmed", AbstractButton.class).isEnabled())
                .isTrue();
        verify(packing).buildPlan(marketDate, 3, false, false);
    }

    @Test
    @DisplayName("worker cancels market completion review before completing the checked load once")
    void cancelsThenConfirmsMarketDayCompletion() {
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.PackLine line =
                new MarketDayPackingService.PackLine(
                        "Nootka Rose", "stems", 20, 4.5, 90, 901L);
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        501L, "Harbor Floral Studio", "FLORIST", "Maya Chen",
                        "360-555-0148", "orders@harborfloral.example", "CONFIRMED",
                        marketDate, "Cooler pickup", List.of(line), 90, "PICKUP");
        MarketDayPackingService.ProductNeed covered =
                new MarketDayPackingService.ProductNeed(
                        "Nootka Rose", "stems", 20, 42, false, 0);
        MarketDayPackingService.MarketDayPlan ready = marketPlan(
                marketDate, customer, covered, 0);
        MarketDayPackingService.MarketDayPlan completed = emptyMarketPlan(marketDate);
        when(packing.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(ready, completed);
        when(packing.fulfillConfirmedOrders(any(MarketDayPackingService.MarketDayPlan.class)))
                .thenReturn(new MarketDayPackingService.FulfillBatchResult(
                        1, 1, 0, 0, List.of("Order #501 completed"),
                        Set.of(501L), Set.of()));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .choose("Complete confirmed orders", "Cancel")
                .choose("Complete confirmed orders", "Complete checked orders");
        MarketDayTab tab = new MarketDayTab(packing, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        String packedLabel = "Order 501 packed for Harbor Floral Studio on Pickup · "
                + marketDate;
        user.awaitText("stock OK")
                .setChecked(packedLabel, true)
                .awaitText("1 checked · ready to complete")
                .click("Complete confirmed orders…");
        verify(packing, never()).fulfillConfirmedOrders(
                any(MarketDayPackingService.MarketDayPlan.class));
        assertThat(user.isChecked(packedLabel)).isTrue();
        assertThat(user.isButtonEnabled("Complete confirmed orders…")).isTrue();

        user.click("Complete confirmed orders…")
                .awaitText("1 completed · 0 failed");
        verify(packing).fulfillConfirmedOrders(
                any(MarketDayPackingService.MarketDayPlan.class));
        assertThat(user.text("marketDay.fulfillmentStatus"))
                .contains("1 completed", "0 failed");
        assertThat(dialogs.transcript()).containsExactly(
                "OPTION Complete confirmed orders = Cancel",
                "OPTION Complete confirmed orders = Complete checked orders");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker retries only the retained failed load after partial market completion")
    void retriesOnlyFailedMarketLoadAfterPartialCompletion() {
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.CustomerPack completedCustomer =
                new MarketDayPackingService.CustomerPack(
                        501L, "Harbor Floral Studio", "FLORIST", "Maya Chen",
                        "360-555-0148", "orders@harborfloral.example", "CONFIRMED",
                        marketDate, "Cooler pickup",
                        List.of(new MarketDayPackingService.PackLine(
                                "Nootka Rose", "stems", 20, 4.5, 90, 901L)),
                        90, "PICKUP");
        MarketDayPackingService.CustomerPack failedCustomer =
                new MarketDayPackingService.CustomerPack(
                        502L, "Sound Floral Co-op", "FLORIST", "Alex Morgan",
                        "360-555-0161", "orders@soundfloral.example", "CONFIRMED",
                        marketDate, "Loading dock delivery",
                        List.of(new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "bunches", 5, 20, 100, 902L)),
                        100, "DELIVERY");
        List<MarketDayPackingService.ProductNeed> covered = List.of(
                new MarketDayPackingService.ProductNeed(
                        "Nootka Rose", "stems", 20, 60, false, 0),
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "bunches", 5, 20, false, 0));
        MarketDayPackingService.MarketDayPlan initial =
                new MarketDayPackingService.MarketDayPlan(
                        marketDate, marketDate, marketDate, "CONFIRMED", 2, 190,
                        List.of(completedCustomer, failedCustomer), covered, 0,
                        "MARKET DAY PACKING LIST\n2 confirmed customer loads");
        MarketDayPackingService.MarketDayPlan retry =
                new MarketDayPackingService.MarketDayPlan(
                        marketDate, marketDate, marketDate, "CONFIRMED", 1, 100,
                        List.of(failedCustomer), List.of(covered.get(1)), 0,
                        "MARKET DAY PACKING LIST\nSound Floral Co-op retry");
        when(packing.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(initial, retry, emptyMarketPlan(marketDate));
        when(packing.fulfillConfirmedOrders(
                any(MarketDayPackingService.MarketDayPlan.class)))
                .thenReturn(
                        new MarketDayPackingService.FulfillBatchResult(
                                2, 1, 0, 1,
                                List.of("Order #501 completed",
                                        "Order #502 FAILED: inventory count changed"),
                                Set.of(501L), Set.of(502L)),
                        new MarketDayPackingService.FulfillBatchResult(
                                1, 1, 0, 0,
                                List.of("Order #502 completed"),
                                Set.of(502L), Set.of()));
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .chooseForm("Complete confirmed orders", "Complete checked orders", form -> form
                        .expectText("Complete 2 checked confirmed orders")
                        .expectText("Order #501 · Harbor Floral Studio")
                        .expectText("Order #502 · Sound Floral Co-op")
                        .expectText("Total: 2 confirmed orders"))
                .chooseForm("Complete confirmed orders", "Complete checked orders", form -> form
                        .expectText("Complete 1 checked confirmed order")
                        .expectText("Order #502 · Sound Floral Co-op")
                        .expectText("Total: 1 confirmed order"));
        MarketDayTab tab = new MarketDayTab(packing, new HarnessHost(), dialogs);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        String completedLabel = "Order 501 packed for Harbor Floral Studio on Pickup · "
                + marketDate;
        String failedLabel = "Order 502 packed for Sound Floral Co-op on Delivery · "
                + marketDate;
        user.awaitText("2 confirmed customer loads")
                .setChecked(completedLabel, true)
                .setChecked(failedLabel, true)
                .awaitText("2 checked · ready to complete")
                .click("Complete confirmed orders…")
                .awaitText("1 completed · 1 failed · retry remaining")
                .awaitText("1 of 1 packed · 0 remaining");

        assertThat(user.text("marketDay.fulfillmentResultDetails"))
                .contains("Order #501 completed", "Order #502 FAILED",
                        "completed orders are already saved");
        assertThat(user.isChecked(failedLabel)).isTrue();
        assertThat(user.isButtonEnabled("Complete confirmed orders…")).isTrue();
        assertThat(user.auditInteractiveSurface().defects()).isEmpty();
        assertThat(user.auditInteractiveSurface().controls())
                .noneMatch(control -> control.selector().contains("Order 501 packed"));

        user.click("Complete confirmed orders…")
                .awaitText("1 completed · 0 failed");

        ArgumentCaptor<MarketDayPackingService.MarketDayPlan> attempts =
                ArgumentCaptor.forClass(MarketDayPackingService.MarketDayPlan.class);
        verify(packing, times(2)).fulfillConfirmedOrders(attempts.capture());
        assertThat(attempts.getAllValues().get(0).customers())
                .extracting(MarketDayPackingService.CustomerPack::orderId)
                .containsExactly(501L, 502L);
        assertThat(attempts.getAllValues().get(1).customers())
                .extracting(MarketDayPackingService.CustomerPack::orderId)
                .containsExactly(502L);
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("VIEWER stays read-only while search, refresh, reports, and exports remain usable")
    void enforcesViewerPermissionsAcrossPriorityWorkspaces() {
        InventoryService inventory = mock(InventoryService.class);
        when(inventory.getAllItems()).thenReturn(List.of(item(1L, "Nootka Rose", 42)));
        HarnessHost viewerHost = new HarnessHost();
        viewerHost.canWrite = false;

        AddItemTab addItemTab = new AddItemTab(inventory, viewerHost);
        SwingUserHarness addItem = SwingUserHarness.on(build(addItemTab));
        onEdt(() -> {
            addItemTab.applyRolePermissions(false);
            return null;
        });
        addItem.awaitText("VIEWER · read only");
        assertThat(addItem.isFieldEditable("Item name")).isFalse();
        assertThat(addItem.isButtonEnabled("Add item to inventory")).isFalse();

        InventoryTab inventoryTab = new InventoryTab(inventory, viewerHost);
        SwingUserHarness inventoryUser = SwingUserHarness.on(build(inventoryTab));
        initialize(inventoryTab);
        inventoryUser.awaitTableRows("inventory.table", 1)
                .selectRow("inventory.table", 0);
        assertThat(inventoryUser.isFieldEditable("Inventory search")).isTrue();
        assertThat(inventoryUser.isButtonEnabled("Refresh")).isTrue();
        assertThat(inventoryUser.isButtonEnabled("Record stock count…")).isFalse();
        assertThat(inventoryUser.isButtonEnabled("Edit selected")).isFalse();
        assertThat(inventoryUser.isButtonEnabled("Low-stock reorder…")).isTrue();
        assertThat(inventoryUser.isButtonEnabled("Export visible CSV…")).isTrue();

        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        when(customers.search(anyString())).thenReturn(List.of());
        when(customers.getAll()).thenReturn(List.of());
        stubEmptyOrders(orders);
        CrmTab crmTab = new CrmTab(customers, orders, viewerHost);
        SwingUserHarness crm = SwingUserHarness.on(build(crmTab));
        initialize(crmTab);
        crm.awaitText("Customers and orders current");
        onEdt(() -> {
            crmTab.applyRolePermissions(false);
            return null;
        });
        assertThat(crm.isFieldEditable("Customer search")).isTrue();
        assertThat(crm.isFieldEditable("Customer or business name")).isFalse();
        assertThat(crm.isButtonEnabled("Refresh CRM")).isTrue();
        assertThat(crm.isButtonEnabled("Add customer")).isFalse();
        crm.selectTab("Orders");
        assertThat(crm.isFieldEditable("Order customer filter")).isTrue();
        assertThat(crm.isFieldEditable("Order product name")).isFalse();
        assertThat(controlEnabled(crm.auditInteractiveSurface(),
                "selector", "Order status filter")).isTrue();

        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.PackLine packLine =
                new MarketDayPackingService.PackLine(
                        "Nootka Rose", "stems", 20, 4.5, 90, 801L);
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        401L, "Harbor Floral Studio", "FLORIST", "Maya Chen",
                        "360-555-0148", "orders@harborfloral.example", "CONFIRMED",
                        marketDate, "Cooler pickup", List.of(packLine), 90, "PICKUP");
        MarketDayPackingService.ProductNeed covered =
                new MarketDayPackingService.ProductNeed(
                        "Nootka Rose", "stems", 20, 42, false, 0);
        when(packing.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(marketPlan(marketDate, customer, covered, 0));
        MarketDayTab marketDayTab = new MarketDayTab(packing, viewerHost);
        SwingUserHarness marketDay = SwingUserHarness.on(build(marketDayTab));
        initialize(marketDayTab);
        marketDay.awaitText("stock OK");
        onEdt(() -> {
            marketDayTab.applyRolePermissions(false);
            return null;
        });
        SwingUserHarness.SurfaceAudit viewerMarket = marketDay.auditInteractiveSurface();
        assertThat(marketDay.isFieldEditable("Market or delivery date")).isTrue();
        assertThat(marketDay.isButtonEnabled("Build packing list")).isTrue();
        assertThat(marketDay.isButtonEnabled("Export file…")).isTrue();
        assertThat(marketDay.isButtonEnabled("Complete confirmed orders…")).isFalse();
        assertThat(controlEnabled(viewerMarket, "button",
                "Order 401 packed for Harbor Floral Studio on Pickup · " + marketDate))
                .isFalse();

        onEdt(() -> {
            addItemTab.applyRolePermissions(true);
            inventoryTab.applyRolePermissions(true);
            crmTab.applyRolePermissions(true);
            marketDayTab.applyRolePermissions(true);
            return null;
        });
        assertThat(addItem.isFieldEditable("Item name")).isTrue();
        assertThat(inventoryUser.isButtonEnabled("Record stock count…")).isTrue();
        assertThat(crm.isFieldEditable("Order product name")).isTrue();
        assertThat(controlEnabled(marketDay.auditInteractiveSurface(), "button",
                "Order 401 packed for Harbor Floral Studio on Pickup · " + marketDate))
                .isTrue();
    }

    @Test
    @DisplayName("HAND cannot clear activity history and OWNER must confirm the destructive action")
    void enforcesOwnerOnlyHistoryClearWithCancellation() {
        SyncHistoryService history = mock(SyncHistoryService.class);
        when(history.filter(anyString(), anyString(), nullable(Boolean.class),
                anyString(), anyInt())).thenReturn(List.of());
        when(history.stats(anyList()))
                .thenReturn(new SyncHistoryService.HistoryStats(0, 0, 0));
        when(history.distinctConnectors(anyInt())).thenReturn(List.of());
        when(history.distinctOperations(anyInt())).thenReturn(List.of());

        HarnessHost handHost = new HarnessHost();
        handHost.canClearHistory = false;
        SyncHistoryTab handTab = new SyncHistoryTab(history, handHost);
        SwingUserHarness hand = SwingUserHarness.on(build(handTab));
        assertThat(hand.isButtonEnabled("Clear history…")).isFalse();
        assertThat(hand.isButtonEnabled("Export CSV…")).isTrue();
        hand.awaitText("VIEWER / HAND · read and export only");

        HarnessHost ownerHost = new HarnessHost();
        ownerHost.canClearHistory = true;
        SwingDialogHarness dialogs = new SwingDialogHarness()
                .confirm("Clear all activity history?", false)
                .confirm("Clear all activity history?", true);
        SyncHistoryTab ownerTab = new SyncHistoryTab(history, ownerHost, dialogs);
        SwingUserHarness owner = SwingUserHarness.on(build(ownerTab));
        initialize(ownerTab);
        owner.awaitText("0 activities · 0 completed · 0 need attention");
        assertThat(owner.isButtonEnabled("Clear history…")).isTrue();
        owner.awaitText("No activities match these filters")
                .click("Clear history…");
        verify(history, never()).clearAll();

        owner.click("Clear history…")
                .awaitButtonEnabled("Clear history…", true);
        verify(history).clearAll();
        assertThat(ownerHost.lastStatus).isEqualTo("Activity history cleared.");
        assertThat(dialogs.transcript()).containsExactly(
                "CONFIRM Clear all activity history? = false",
                "CONFIRM Clear all activity history? = true");
        dialogs.assertComplete();
    }

    @Test
    @DisplayName("worker can address repeated Dashboard actions through unique accessible wires")
    void routesRepeatedDashboardActionsByAccessibleWire() {
        HarnessHost host = new HarnessHost();
        DashboardTab tab = new DashboardTab(
                mock(InventoryService.class), mock(HarvestService.class),
                mock(OrderService.class), mock(IrrigationAdvisorService.class),
                mock(MarketDayPackingService.class), mock(MorningBriefingService.class),
                mock(DayCloseoutService.class), host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));

        user.clickAccessible("Log harvest from Week harvest metric")
                .clickAccessible("Log harvest from Today’s work actions")
                .clickAccessible("Create order from Week revenue metric")
                .clickAccessible("Create order from Today’s work actions");

        assertThat(host.selectedTabs).containsExactly(
                "Harvest Log", "Harvest Log",
                "Customers & Orders", "Customers & Orders");
        assertThat(user.auditInteractiveSurface().defects()).isEmpty();
        assertThat(user.transcript()).containsExactly(
                "CLICK ACCESSIBLE Log harvest from Week harvest metric",
                "CLICK ACCESSIBLE Log harvest from Today’s work actions",
                "CLICK ACCESSIBLE Create order from Week revenue metric",
                "CLICK ACCESSIBLE Create order from Today’s work actions");
    }

    @Test
    @DisplayName("empty Inventory onboards the first product and distinguishes a filtered miss")
    void onboardsEmptyInventoryThenRecoversAFilteredView() {
        InventoryService inventory = mock(InventoryService.class);
        List<Item> items = new CopyOnWriteArrayList<>();
        when(inventory.getAllItems()).thenAnswer(invocation -> List.copyOf(items));
        InventoryTab tab = new InventoryTab(inventory, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("0 items")
                .awaitText("No inventory yet")
                .awaitButtonEnabled("Refresh", true);
        assertThat(user.tableRows("inventory.table")).isZero();
        assertThat(user.isButtonEnabled("Record stock count…")).isFalse();
        assertThat(user.isButtonEnabled("Edit selected")).isFalse();
        assertThat(user.isButtonEnabled("Delete selected")).isFalse();
        assertThat(user.isButtonEnabled("Export visible CSV…")).isFalse();

        items.add(item(901L, "First Farm Bouquet", 8));
        user.click("Refresh")
                .awaitTableRows("inventory.table", 1)
                .type("Inventory search", "missing product")
                .awaitText("No inventory matches this view");

        assertThat(user.tableRows("inventory.table")).isZero();
        user.click("Clear search")
                .awaitTableRows("inventory.table", 1);
        assertThat(user.tableValue("inventory.table", 0, 0))
                .isEqualTo("First Farm Bouquet");
    }

    @Test
    @DisplayName("empty Harvest Log onboards the first cut and explains a filtered miss")
    void onboardsFirstHarvestThenExplainsAFilteredEmptyResult() {
        HarvestService harvest = mock(HarvestService.class);
        List<HarvestEntry> history = new CopyOnWriteArrayList<>();
        stubHarvestHistory(harvest, history);
        when(harvest.missingInventoryProducts(anyList())).thenReturn(List.of());
        when(harvest.add(any(HarvestEntry.class))).thenAnswer(invocation -> {
            HarvestEntry saved = harvest(902L, invocation.getArgument(0));
            history.add(saved);
            return saved;
        });
        HarvestLogTab tab = new HarvestLogTab(harvest, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("0 entries · 0 total")
                .awaitText("No harvests yet")
                .awaitButtonEnabled("Add harvest", true);
        assertThat(user.isButtonEnabled("Add harvest")).isTrue();
        assertThat(user.visibleExtent("harvest.loadSelected").height())
                .as("load action stays out of the create workflow until a row is selected")
                .isZero();
        assertThat(user.visibleExtent("harvest.saveEdit").height())
                .as("save action stays out of the create workflow until editing begins")
                .isZero();

        user.type("Harvest crop or variety", "First Sweet Pea Cut")
                .type("Harvest quantity", "18")
                .type("Harvest unit", "stems")
                .type("Harvest bed or field", "Tunnel 1")
                .type("Harvest notes", "First recorded harvest")
                .click("Add harvest")
                .awaitTableRows("harvest.history.table", 1)
                .type("Filter by crop", "not recorded")
                .click("Apply filters")
                .awaitTableRows("harvest.history.table", 0)
                .awaitText("No harvests match these filters");

        assertThat(user.tableRows("harvest.history.table")).isZero();
        user.click("Show all")
                .awaitTableRows("harvest.history.table", 1);
        assertThat(user.tableValue("harvest.history.table", 0, 2))
                .isEqualTo("First Sweet Pea Cut");
    }

    @Test
    @DisplayName("empty CRM onboards its first account and keeps search misses honest")
    void onboardsFirstCustomerAndOrderThenExplainsASearchMiss() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        List<Customer> accounts = new CopyOnWriteArrayList<>();
        when(customers.search(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0, String.class).toLowerCase();
            return accounts.stream()
                    .filter(customer -> customer.getName().toLowerCase().contains(query))
                    .toList();
        });
        when(customers.getAll()).thenAnswer(invocation -> List.copyOf(accounts));
        when(customers.add(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId(903L);
            accounts.add(customer);
            return customer;
        });
        stubEmptyOrders(orders);
        CrmTab tab = new CrmTab(customers, orders, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("No customers yet")
                .selectTab("Orders")
                .awaitText("No orders yet");
        assertThat(user.isButtonEnabled("Create & select order")).isFalse();

        user.selectTab("Customers")
                .type("Customer or business name", "First Farm Florist")
                .type("Customer email", "first@florist.example")
                .select("Customer type", "Florist")
                .click("Add customer")
                .awaitTableRows("crm.customers.table", 1)
                .type("Customer search", "unknown account")
                .click("Search")
                .awaitText("No customers match this search");

        assertThat(user.tableRows("crm.customers.table")).isZero();
        user.clickAccessible("Show all customers")
                .awaitTableRows("crm.customers.table", 1);
        assertThat(user.tableValue("crm.customers.table", 0, 1))
                .isEqualTo("First Farm Florist");
    }

    @Test
    @DisplayName("empty Market Day guides order creation before presenting the first load")
    void onboardsEmptyMarketDayThenPresentsTheFirstLoad() {
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.PackLine line =
                new MarketDayPackingService.PackLine(
                        "First Farm Bouquet", "bouquets", 4, 25, 100, 904L);
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        905L, "First Farm Florist", "FLORIST", "Avery Stone",
                        "360-555-0199", "first@florist.example", "CONFIRMED",
                        marketDate, "Cooler pickup", List.of(line), 100, "PICKUP");
        MarketDayPackingService.ProductNeed covered =
                new MarketDayPackingService.ProductNeed(
                        "First Farm Bouquet", "bouquets", 4, 10, false, 0);
        MarketDayPackingService.MarketDayPlan ready =
                new MarketDayPackingService.MarketDayPlan(
                        marketDate, marketDate, marketDate, "CONFIRMED", 1, 100,
                        List.of(customer), List.of(covered), 0,
                        "MARKET DAY PACKING LIST\nFirst Farm Bouquet · 4 bouquets\n"
                                + "First Farm Florist");
        when(packing.buildPlan(any(LocalDate.class), anyInt(),
                anyBoolean(), anyBoolean()))
                .thenReturn(emptyMarketPlan(marketDate),
                        ready);
        MarketDayTab tab = new MarketDayTab(packing, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("No orders are ready in this date window");
        assertThat(user.isButtonEnabled("Resolve stock shortfalls")).isFalse();
        assertThat(user.isButtonEnabled("Complete confirmed orders…")).isFalse();
        assertThat(user.isButtonEnabled("Export file…")).isTrue();

        user.click("Build packing list")
                .awaitText("stock OK");
        assertThat(user.fieldValue("Market day packing list"))
                .contains("First Farm Bouquet")
                .contains("First Farm Florist");
    }

    @Test
    @DisplayName("empty Trend Analysis asks for evidence and never fabricates a forecast")
    void refusesToFabricateATrendWithoutHarvestHistory() {
        TrendService trends = mock(TrendService.class);
        when(trends.listSeries()).thenReturn(List.of());
        when(trends.analyzeQuantityTrend()).thenReturn(new TrendService.TrendResult(
                0, null,
                "Record at least 3 separate weeks for the same crop and unit before forecasting.",
                "", "units", 0, Double.NaN, null, null));
        HarnessHost host = new HarnessHost();
        SwingUserHarness user = SwingUserHarness.on(
                build(new TrendAnalysisTab(trends, host)));

        user.awaitText("No series listed")
                .click("Analyze trends")
                .awaitText("Analysis needs attention");

        assertThat(user.text("trend.forecast")).isEqualTo("Needs history");
        assertThat(user.text("trend.context")).contains("No crop series recorded");
        assertThat(user.tableRows("trend.weeklyTable")).isZero();
        user.selectTab("Model details");
        assertThat(user.fieldValue("Harvest trend model details"))
                .contains("Record at least 3 separate weeks")
                .doesNotContain("forecast ready");

        user.click("Open Harvest Log");
        assertThat(host.selectedTabs).containsExactly("Harvest Log");
    }

    @Test
    @DisplayName("empty Dashboard prioritizes the first harvest without unsafe shortcuts")
    void prioritizesFirstHarvestFromAnEmptyFarmSnapshot() {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        when(inventory.getAllItems()).thenReturn(List.of());
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(0, 0, 0, 0, 0, 10));
        when(harvest.totalQuantityLast7Days()).thenReturn(0.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(0.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(),
                0, 0, 0, 0, 0, 0));
        when(orders.realizedRevenuePrior7Days()).thenReturn(0.0);
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        HarnessHost host = new HarnessHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, null, null, null, null, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Updated");
        assertThat(user.fieldValue("Farm operations alerts"))
                .contains("New cut-flower farm")
                .contains("No completed or confirmed sales")
                .contains("No harvest logged in the last 7 days");
        assertThat(user.selectedOption("Ranked farm priorities"))
                .isEqualTo("Start here — set up your cut-flower farm");
        assertThat(user.text("dashboard.priorityStatus"))
                .contains("Priority 1 of");
        assertThat(user.isButtonEnabled("Open priority")).isTrue();

        user.click("Open priority");
        assertThat(host.selectedTabs).containsExactly("Start here");
        assertThat(host.lastStatus).contains("Start here");
    }

    @Test
    @DisplayName("dashboard identifies a last-good snapshot, preserves its route, and recovers")
    void recoversDashboardAfterAPopulatedSnapshotTurnsStale() {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        Item low = item(906L, "Nootka Rose", 2);
        Item recovered = item(906L, "Nootka Rose", 24);
        when(inventory.getAllItems())
                .thenReturn(List.of(low))
                .thenThrow(new IllegalStateException("inventory database unavailable"))
                .thenReturn(List.of(recovered));
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(1, 2, 2, 1, 2, 10),
                new InventoryService.InventoryKpiSnapshot(1, 24, 24, 0, 0, 10));
        when(harvest.totalQuantityLast7Days()).thenReturn(18.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(15.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                LocalDate.now().minusDays(6), LocalDate.now(),
                420, 0, 0, 2, 0, 0));
        when(orders.realizedRevenuePrior7Days()).thenReturn(350.0);
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        HarnessHost host = new HarnessHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, null, null, null, null, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Updated")
                .click("Refresh overview")
                .awaitText("Refresh failed · retry");

        assertThat(user.text("dashboard.priorityStatus"))
                .contains("last good snapshot");
        assertThat(user.selectedOption("Ranked farm priorities"))
                .contains("Restock Nootka Rose");
        assertThat(user.isButtonEnabled("Open priority")).isTrue();
        user.click("Open priority");
        assertThat(host.selectedTabs).containsExactly("Inventory");

        user.click("Refresh overview")
                .awaitText("Updated");
        assertThat(user.text("dashboard.priorityStatus"))
                .doesNotContain("last good snapshot");
        verify(inventory, times(3)).getAllItems();
    }

    @Test
    @DisplayName("harvest keeps an edit draft but locks stale-row writes until refresh recovers")
    void recoversHarvestDraftAfterAPopulatedSnapshotTurnsStale() {
        HarvestService harvest = mock(HarvestService.class);
        HarvestEntry initial = new HarvestEntry(907L, LocalDate.now(), "Nootka Rose",
                24, "stems", "Field A", "Morning cut");
        HarvestEntry recovered = new HarvestEntry(907L, LocalDate.now(),
                "Nootka Rose · current", 31, "stems", "Field A", "Morning cut");
        when(harvest.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenReturn(List.of(initial))
                .thenThrow(new IllegalStateException("harvest database unavailable"))
                .thenReturn(List.of(recovered));
        when(harvest.totalsByProductUnit()).thenReturn(
                Map.of("Nootka Rose", 24.0),
                Map.of("Nootka Rose · current", 31.0));
        when(harvest.totalsByProductUnit(anyList())).thenAnswer(invocation ->
                harvestTotals(invocation.getArgument(0)));
        HarvestLogTab tab = new HarvestLogTab(harvest, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("harvest.history.table", 1)
                .selectRow("harvest.history.table", 0)
                .click("Load selected")
                .type("Harvest notes", "Retained correction draft")
                .type("Filter by crop", "Nootka")
                .click("Refresh")
                .awaitText("Harvest refresh failed · previous snapshot shown · retry");

        assertThat(user.fieldValue("Harvest notes")).isEqualTo("Retained correction draft");
        assertThat(user.fieldValue("Filter by crop")).isEqualTo("Nootka");
        assertThat(user.tableValue("harvest.history.table", 0, 2)).isEqualTo("Nootka Rose");
        assertThat(user.isButtonEnabled("Save edit")).isFalse();

        user.click("Refresh")
                .awaitTableValue("harvest.history.table", 0, 2, "Nootka Rose · current");
        assertThat(user.fieldValue("Harvest notes")).isEqualTo("Retained correction draft");
        assertThat(user.isButtonEnabled("Save edit")).isTrue();
    }

    @Test
    @DisplayName("CRM keeps an account draft but locks stale writes until refresh recovers")
    void recoversCustomerDraftAfterAPopulatedSnapshotTurnsStale() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        Customer initial = new Customer(908L, "Kitsap Blooms", "Maya Chen", "", "",
                "WHOLESALE", "Original account note");
        Customer recovered = new Customer(908L, "Kitsap Blooms", "Authoritative Contact", "", "",
                "WHOLESALE", "Saved account note");
        when(customers.search(anyString()))
                .thenReturn(List.of(initial))
                .thenThrow(new IllegalStateException("customer database unavailable"))
                .thenReturn(List.of(recovered));
        when(customers.getAll()).thenReturn(List.of(initial), List.of(recovered));
        stubEmptyOrders(orders);
        CrmTab tab = new CrmTab(customers, orders, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableRows("crm.customers.table", 1)
                .selectRow("crm.customers.table", 0)
                .click("Load selected")
                .type("Customer notes", "Retained relationship correction")
                .click("Refresh CRM")
                .awaitText(
                        "CRM refresh failed · Customers unavailable · previous snapshot shown · retry");

        assertThat(user.fieldValue("Customer notes"))
                .isEqualTo("Retained relationship correction");
        assertThat(user.tableValue("crm.customers.table", 0, 2)).isEqualTo("Maya Chen");
        assertThat(user.isButtonEnabled("Add customer")).isFalse();
        assertThat(user.isButtonEnabled("Save edit")).isFalse();
        assertThat(user.isButtonEnabled("Delete selected")).isFalse();
        assertThat(user.isButtonEnabled("Statement PDF…")).isTrue();

        user.click("Refresh CRM")
                .awaitTableValue("crm.customers.table", 0, 2, "Authoritative Contact");
        assertThat(user.fieldValue("Customer notes"))
                .isEqualTo("Retained relationship correction");
        assertThat(user.isButtonEnabled("Save edit")).isTrue();
    }

    @Test
    @DisplayName("CRM names the failed refresh source and rejects a partial mixed snapshot")
    void rejectsPartialCrmSnapshotWhenInventoryRefreshFails() {
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        InventoryService inventory = mock(InventoryService.class);
        Customer customer = new Customer(909L, "Kitsap Blooms", "Maya Chen", "", "",
                "WHOLESALE", "Standing Friday pickup");
        CustomerOrder original = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Original snapshot");
        original.setId(911L);
        original.addLine(new OrderLine("Dahlia Mix", 8, "stems", 3.5));
        CustomerOrder newer = new CustomerOrder(
                customer, LocalDate.now(), "DRAFT", "Newer order source response");
        newer.setId(912L);
        newer.addLine(new OrderLine("Nootka Rose", 12, "stems", 2.75));
        when(customers.search(anyString())).thenReturn(List.of(customer));
        when(customers.getAll()).thenReturn(List.of(customer));
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class)))
                .thenReturn(List.of(original), List.of(newer), List.of(newer));
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
        when(inventory.getAllItems())
                .thenReturn(List.of(item(911L, "Dahlia Mix", 30)))
                .thenThrow(new IllegalStateException("inventory database unavailable"))
                .thenReturn(List.of(item(912L, "Nootka Rose", 45)));
        CrmTab tab = new CrmTab(customers, orders, null, inventory, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitTableValue("crm.orders.table", 0, 0, 911L)
                .click("Refresh CRM")
                .awaitText(
                        "CRM refresh failed · Inventory unavailable · previous snapshot shown · retry");

        assertThat(user.tableRows("crm.orders.table")).isOne();
        assertThat(user.tableValue("crm.orders.table", 0, 0)).isEqualTo(911L);
        assertThat(user.text("crm.refreshStatus")).contains("Inventory unavailable");
        assertThat(user.named("crm.refreshStatus", javax.swing.JLabel.class)
                .getAccessibleContext().getAccessibleDescription())
                .contains("Inventory could not be loaded")
                .contains("partial refresh response was discarded")
                .contains("inventory database unavailable");

        user.click("Refresh CRM")
                .awaitTableValue("crm.orders.table", 0, 0, 912L);
        assertThat(user.text("crm.refreshStatus")).isEqualTo("Customers and orders current");
    }

    @Test
    @DisplayName("market day keeps reviewed packing but locks completion until rebuild recovers")
    void recoversMarketDayChecklistAfterAPopulatedPlanTurnsStale() {
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate marketDate = LocalDate.now();
        MarketDayPackingService.PackLine line = new MarketDayPackingService.PackLine(
                "Dahlia Mix", "stems", 12, 10, 120, 909L);
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        910L, "Kitsap Blooms", "FLORIST", "Maya Chen", "", "",
                        "CONFIRMED", marketDate, "Cooler pickup", List.of(line),
                        120, "PICKUP");
        MarketDayPackingService.ProductNeed covered =
                new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 12, 30, false, 0);
        MarketDayPackingService.MarketDayPlan initial = marketPlan(
                marketDate, customer, covered, 0);
        MarketDayPackingService.MarketDayPlan recovered =
                new MarketDayPackingService.MarketDayPlan(
                        initial.marketDate(), initial.from(), initial.to(), initial.scope(),
                        initial.orderCount(), initial.pipelineValue(), initial.customers(),
                        initial.pickList(), initial.shortfallSkuCount(),
                        "RECOVERED CURRENT PACKING LIST\nDahlia Mix 12 stems\nKitsap Blooms");
        when(packing.buildPlan(any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(initial)
                .thenThrow(new IllegalStateException("packing database unavailable"))
                .thenReturn(recovered);
        MarketDayTab tab = new MarketDayTab(packing, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);
        String packedLabel = "Order 910 packed for Kitsap Blooms on Pickup · " + marketDate;

        user.awaitText("stock OK")
                .setChecked(packedLabel, true)
                .awaitText("1 checked · ready to complete")
                .click("Build packing list")
                .awaitText("Packing list refresh failed · previous plan shown · retry");

        assertThat(user.isChecked(packedLabel)).isTrue();
        assertThat(user.isButtonEnabled("Complete confirmed orders…")).isFalse();
        assertThat(user.isButtonEnabled("Export file…")).isTrue();
        assertThat(user.text("marketDay.fulfillmentStatus"))
                .isEqualTo("Refresh failed · previous plan shown · completion locked");

        user.click("Build packing list")
                .awaitText("stock OK");
        assertThat(user.fieldValue("Market day packing list"))
                .contains("RECOVERED CURRENT PACKING LIST");
        assertThat(user.isChecked(packedLabel)).isTrue();
        assertThat(user.isButtonEnabled("Complete confirmed orders…")).isTrue();
        verify(packing, times(3)).buildPlan(
                any(LocalDate.class), anyInt(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("dashboard marks failed optional sources while keeping core metrics current")
    void distinguishesCurrentCoreMetricsFromRetainedDashboardContext() {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        OrderService orders = mock(OrderService.class);
        IrrigationAdvisorService irrigation = mock(IrrigationAdvisorService.class);
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        LocalDate today = LocalDate.now();
        HarvestService.BedProductionReport bedContext =
                new HarvestService.BedProductionReport(
                        today.minusDays(6).toString(), today.toString(), 1, 1, 18,
                        List.of(new HarvestService.BedProduction(
                                "Field A", 18, 1, Map.of("Nootka Rose", 18.0),
                                today.toString(), today.toString())), "Field A production");
        IrrigationAdvisorService.IrrigationAdvice irrigationContext =
                new IrrigationAdvisorService.IrrigationAdvice(
                        "Port Orchard, WA", "CLIMATOLOGY", today.toString(),
                        IrrigationAdvisorService.SeasonBand.PEAK_DRY_SUMMER,
                        IrrigationAdvisorService.Priority.HIGH,
                        "Water before afternoon heat", List.of("Run drip before 10 AM"),
                        List.of("Field A"), null, null, null, null, null, "Dry pattern");
        MarketDayPackingService.CustomerPack customer =
                new MarketDayPackingService.CustomerPack(
                        911L, "Kitsap Blooms", "FLORIST", "Maya Chen", "", "",
                        "CONFIRMED", today, "Cooler pickup",
                        List.of(new MarketDayPackingService.PackLine(
                                "Dahlia Mix", "stems", 12, 10, 120, 912L)),
                        120, "PICKUP");
        MarketDayPackingService.MarketDayPlan shortPlan = marketPlan(
                today, customer, new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 12, 6, true, 6), 1);
        MarketDayPackingService.MarketDayPlan recoveredPlan = marketPlan(
                today, customer, new MarketDayPackingService.ProductNeed(
                        "Dahlia Mix", "stems", 12, 30, false, 0), 0);
        when(inventory.getAllItems()).thenReturn(
                List.of(item(913L, "Nootka Rose", 2)),
                List.of(item(913L, "Nootka Rose", 18)),
                List.of(item(913L, "Nootka Rose", 20)));
        when(inventory.inventoryKpis(anyInt())).thenReturn(
                new InventoryService.InventoryKpiSnapshot(1, 2, 2, 1, 2, 10),
                new InventoryService.InventoryKpiSnapshot(1, 18, 18, 0, 0, 10),
                new InventoryService.InventoryKpiSnapshot(1, 20, 20, 0, 0, 10));
        when(harvest.totalQuantityLast7Days()).thenReturn(18.0);
        when(harvest.totalQuantityPrior7Days()).thenReturn(16.0);
        when(harvest.dailyQuantitiesLast7Days()).thenReturn(new double[7]);
        when(harvest.productionByBedLast7Days())
                .thenReturn(bedContext)
                .thenThrow(new IllegalStateException("bed report unavailable"))
                .thenReturn(bedContext);
        when(orders.weekRevenueSummary()).thenReturn(new OrderService.WeekRevenueSummary(
                today.minusDays(6), today, 100, 0, 0, 1, 0, 0));
        when(orders.realizedRevenuePrior7Days()).thenReturn(80.0);
        when(orders.dailyRevenueLast7Days()).thenReturn(new double[7]);
        when(irrigation.adviseClimatology())
                .thenReturn(irrigationContext)
                .thenThrow(new IllegalStateException("weather context unavailable"))
                .thenReturn(irrigationContext);
        when(packing.planForDay(any(LocalDate.class)))
                .thenReturn(shortPlan)
                .thenThrow(new IllegalStateException("packing context unavailable"))
                .thenReturn(recoveredPlan);
        HarnessHost host = new HarnessHost();
        DashboardTab tab = new DashboardTab(
                inventory, harvest, orders, irrigation, packing, null, null, host);
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        initialize(tab);

        user.awaitText("Updated")
                .click("Refresh overview")
                .awaitText("3 contexts unavailable");

        assertThat(user.text("dashboard.overviewStatus"))
                .contains("Updated", "3 contexts unavailable")
                .doesNotContain("Refresh failed");
        assertThat(user.fieldValue("Farm operations alerts"))
                .contains("Partial update: Bed production, Irrigation, Market Day unavailable")
                .contains("previous snapshot · verify")
                .contains("core metrics are current");
        assertThat(user.text("dashboard.priorityStatus")).isEqualTo("All caught up");
        assertThat(user.isButtonEnabled("Open priority")).isFalse();
        assertThat(host.lastStatus)
                .contains("Dashboard core metrics refreshed", "Bed production, Irrigation, Market Day");

        user.click("Refresh overview")
                .awaitText("Updated");
        assertThat(user.text("dashboard.overviewStatus"))
                .doesNotContain("unavailable");
        assertThat(user.fieldValue("Farm operations alerts"))
                .doesNotContain("Partial update", "previous snapshot");
        assertThat(user.selectedOption("Ranked farm priorities"))
                .contains("Watering high", "Water before afternoon heat");
    }

    @Test
    @DisplayName("reports automatically applies the latest range entered during an active preview")
    void coalescesReportPreviewToTheLatestEnteredRange() throws Exception {
        ReportService reports = mock(ReportService.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        List<LocalDate> requestedStarts = new CopyOnWriteArrayList<>();
        when(reports.preview(any(LocalDate.class), any(LocalDate.class))).thenAnswer(invocation -> {
            LocalDate from = invocation.getArgument(0);
            LocalDate to = invocation.getArgument(1);
            requestedStarts.add(from);
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                return new ReportService.ReportPreview(
                        from, to, 1, 1, 1, 0, 100, 0);
            }
            return new ReportService.ReportPreview(
                    from, to, 9, 4, 3, 1, 900, 250);
        });
        ReportsTab tab = new ReportsTab(reports, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));
        LocalDate latestFrom = LocalDate.now().minusDays(13);

        user.click("Preview range");
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        user.type("Report start date", latestFrom.toString())
                .awaitText("Range changed · latest preview queued");
        release.countDown();

        user.awaitText("Range preview ready");
        assertThat(user.text("reports.preview.harvest")).isEqualTo("9 logs");
        assertThat(user.text("reports.preview.committedRevenue")).isEqualTo("$900.00");
        assertThat(requestedStarts)
                .containsExactly(LocalDate.now().minusDays(6), latestFrom);
        assertThat(calls).hasValue(2);
        assertThat(user.isButtonEnabled("Preview range")).isTrue();
    }

    @Test
    @DisplayName("reports label retained metrics with their previous range after preview failure")
    void labelsRetainedReportMetricsUntilPreviewRecovery() {
        ReportService reports = mock(ReportService.class);
        LocalDate today = LocalDate.now();
        LocalDate initialFrom = today.minusDays(6);
        LocalDate widerFrom = today.minusDays(13);
        when(reports.preview(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new ReportService.ReportPreview(
                        initialFrom, today, 4, 2, 1, 1, 120, 70))
                .thenThrow(new IllegalStateException("report database unavailable"))
                .thenReturn(new ReportService.ReportPreview(
                        widerFrom, today, 11, 5, 4, 1, 850, 200));
        ReportsTab tab = new ReportsTab(reports, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab));

        user.click("Preview range")
                .awaitText("Range preview ready")
                .type("Report start date", widerFrom.toString())
                .click("Preview range")
                .awaitText("Preview failed · previous range shown");

        assertThat(user.text("reports.preview.committedRevenue")).isEqualTo("$120.00");
        assertThat(user.fieldValue("Report contents and export status"))
                .contains("report database unavailable")
                .contains(initialFrom.toString(), today.toString())
                .contains("remain from", "before treating them as current");

        user.click("Preview range")
                .awaitText("Range preview ready");
        assertThat(user.text("reports.preview.committedRevenue")).isEqualTo("$850.00");
        assertThat(user.fieldValue("Report contents and export status"))
                .contains(widerFrom.toString(), today.toString());
        verify(reports, times(3)).preview(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("primary and nested workflows expose accessible controls with live button wires")
    void auditsPriorityInteractiveSurface() {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        ReportService reports = mock(ReportService.class);
        SyncHistoryService history = mock(SyncHistoryService.class);
        TrendService trends = mock(TrendService.class);
        IrrigationAdvisorService irrigation = mock(IrrigationAdvisorService.class);
        MorningBriefingService briefing = mock(MorningBriefingService.class);
        DayCloseoutService closeout = mock(DayCloseoutService.class);
        CustomerStatementService statements = mock(CustomerStatementService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        SeasonPlanService seasons = mock(SeasonPlanService.class);
        com.flowerfarm.service.ProductionStudioService production =
                mock(com.flowerfarm.service.ProductionStudioService.class);
        when(trends.listSeries()).thenReturn(List.of());
        HarnessHost host = new HarnessHost();
        FarmTabCatalog.Catalog catalog = new FarmTabCatalog(
                inventory, trends, harvest, history, customers, orders, reports,
                irrigation, packing, briefing, closeout, statements, knowledge, seasons, production)
                .create(host);
        List<SwingUserHarness.RoutedSurfaceAudit> routedAudits = new ArrayList<>();
        List<String> routes = new ArrayList<>();
        for (FlowerFarmTab tab : catalog.tabs()) {
            SwingUserHarness user = SwingUserHarness.on(build(tab));
            List<SwingUserHarness.RoutedSurfaceAudit> tabAudits =
                    user.auditEveryTabSurface();
            routedAudits.addAll(tabAudits);
            tabAudits.forEach(audit -> routes.add(
                    tab.getTabTitle() + " → " + audit.route()));
        }

        assertThat(routedAudits).allSatisfy(routed -> {
            SwingUserHarness.SurfaceAudit audit = routed.audit();
            assertThat(audit.controls()).isNotEmpty();
            assertThat(audit.defects()).isEmpty();
            assertThat(audit.controls())
                    .filteredOn(control -> "field".equals(control.kind())
                            || "selector".equals(control.kind()))
                    .allSatisfy(control -> assertThat(control.selector()).isNotBlank());
            assertThat(audit.controls())
                    .filteredOn(control -> "button".equals(control.kind()))
                    .allSatisfy(control -> assertThat(control.wired()).isTrue());
        });
        assertThat(routes).anyMatch(route -> route.contains("Dashboard → Inventory charts"));
        assertThat(routes).anyMatch(route -> route.contains("Harvest Log → Batch entry"));
        assertThat(routes).anyMatch(route -> route.contains("Harvest Log → Production totals"));
        assertThat(routes).anyMatch(route -> route.contains("Season Planner → Weekly pace"));
        assertThat(routes).anyMatch(route -> route.contains("Season Planner → Plan insights"));
        assertThat(routes).anyMatch(route ->
                route.contains("Season Planner → Availability outlook"));
        assertThat(routes).anyMatch(route -> route.contains("Customers & Orders → Orders"));
        assertThat(routes).anyMatch(route -> route.contains("Reports → Products"));
        assertThat(routes).anyMatch(route -> route.contains("Reports → Customers"));
        assertThat(routes).anyMatch(route -> route.contains("Trend Analysis → Model details"));
        assertThat(routes).anyMatch(route -> route.contains("Reference & Planning → Event services"));
        assertThat(routes).anyMatch(route -> route.contains("Reference & Planning → Growth visualizer"));
        assertThat(routedAudits.stream()
                .flatMap(audit -> audit.audit().controls().stream()).toList())
                .hasSizeGreaterThanOrEqualTo(180)
                .anySatisfy(control -> {
                    assertThat(control.kind()).isEqualTo("JSlider");
                    assertThat(control.selector()).isEqualTo("Rose growth recursion depth");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("runtime harness publishes and opens every application workflow route")
    void runtimeHarnessCoversTheWholeApplicationCatalog() {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        ReportService reports = mock(ReportService.class);
        SyncHistoryService history = mock(SyncHistoryService.class);
        TrendService trends = mock(TrendService.class);
        IrrigationAdvisorService irrigation = mock(IrrigationAdvisorService.class);
        MorningBriefingService briefing = mock(MorningBriefingService.class);
        DayCloseoutService closeout = mock(DayCloseoutService.class);
        CustomerStatementService statements = mock(CustomerStatementService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        SeasonPlanService seasons = mock(SeasonPlanService.class);
        com.flowerfarm.service.ProductionStudioService production =
                mock(com.flowerfarm.service.ProductionStudioService.class);
        when(trends.listSeries()).thenReturn(List.of());
        HarnessHost host = new HarnessHost();
        FarmTabCatalog.Catalog catalog = new FarmTabCatalog(
                inventory, trends, harvest, history, customers, orders, reports,
                irrigation, packing, briefing, closeout, statements, knowledge, seasons, production)
                .create(host, new SwingDialogHarness().safelyCancelUnscripted(),
                        new SwingFileHarness().cancelUnscripted());
        JTabbedPane primary = new JTabbedPane();
        primary.getAccessibleContext().setAccessibleName("Farm workflow navigation");
        JRootPane root = onEdt(() -> {
            for (FlowerFarmTab tab : catalog.tabs()) {
                primary.addTab(tab.getTabTitle(), tab.getUIComponent());
            }
            JRootPane application = new JRootPane();
            application.setContentPane(primary);
            application.setSize(1400, 900);
            application.doLayout();
            return application;
        });
        FarmTabNavigator navigator = new FarmTabNavigator(primary, catalog.tabs());
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(root, navigator::select);
        SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);

        HarnessSnapshot initial = plugin.observe();
        List<List<String>> routes = (List<List<String>>) initial.metadata().get("routes");
        assertThat(initial.metadata()).containsEntry("routeCount", 37);
        assertThat(routes).hasSize(37).doesNotHaveDuplicates()
                .contains(List.of("Dashboard", "Inventory charts"))
                .contains(List.of("Field science"))
                .contains(List.of("Season Planner", "Plan insights"))
                .contains(List.of("Season Planner", "Availability outlook"))
                .contains(List.of("Harvest Log", "Production totals"))
                .contains(List.of("Customers & Orders", "Orders"))
                .contains(List.of("Reports", "Customers"))
                .contains(List.of("Trend Analysis", "Model details"))
                .contains(List.of("Reference & Planning", "Event services"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Frost & crop templates"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Plantings & work", "Plan plantings"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Plantings & work", "Field work"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Design recipes"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Economics & handoffs", "Recipe profitability"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Economics & handoffs", "Production costs"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Economics & handoffs", "Planned + live availability"))
                .contains(List.of("Reference & Planning", "Production studio",
                        "Economics & handoffs", "External handoffs"))
                .contains(List.of("Reference & Planning", "Growth visualizer"));

        int observedControls = 0;
        int observedReadables = 0;
        int observedLayouts = 0;
        org.assertj.core.api.SoftAssertions selectorAssertions =
                new org.assertj.core.api.SoftAssertions();
        for (int index = 0; index < routes.size(); index++) {
            List<String> route = routes.get(index);
            plugin.execute(new HarnessCommand("whole-app-route-" + index,
                    "navigate-route", Map.of("path", route)));
            HarnessSnapshot snapshot = plugin.observe();
            assertThat(snapshot.route()).as("selected route").isEqualTo(route);
            selectorAssertions.assertThat(snapshot.controls())
                    .as("controls on %s", route).isNotEmpty();
            for (var control : snapshot.controls()) {
                selectorAssertions.assertThat(control.selector())
                        .as("selector on %s", route).isNotBlank();
                selectorAssertions.assertThat(control.commandable())
                        .as("commandable %s on %s", control.selector(), route)
                        .isTrue();
            }
            selectorAssertions.assertThat(snapshot.metadata())
                    .as("addressability on %s", route)
                    .containsEntry("unaddressableControlCount", 0L);
            observedControls += snapshot.controls().size();
            List<com.flowerfarm.automation.HarnessControl> readableControls =
                    (List<com.flowerfarm.automation.HarnessControl>) snapshot.metadata().get("readableControls");
            for (int readableIndex = 0; readableIndex < readableControls.size(); readableIndex++) {
                var readable = readableControls.get(readableIndex);
                var result = plugin.execute(new HarnessCommand("catalog-read-" + index + "-" + readableIndex,
                        "read-control", Map.of("selector", readable.selector())));
                selectorAssertions.assertThat(result.success()).as("readable %s on %s", readable.selector(), route)
                        .isTrue();
                selectorAssertions.assertThat(result.output()).containsKey("contextToken");
            }
            List<com.flowerfarm.automation.HarnessControl> layoutControls =
                    (List<com.flowerfarm.automation.HarnessControl>) snapshot.metadata().get("layoutControls");
            for (int layoutIndex = 0; layoutIndex < layoutControls.size(); layoutIndex++) {
                var layout = layoutControls.get(layoutIndex);
                var result = plugin.execute(new HarnessCommand("catalog-layout-" + index + "-" + layoutIndex,
                        "read-layout", Map.of("selector", layout.selector(), "kind", layout.kind())));
                selectorAssertions.assertThat(result.success()).as("layout %s on %s", layout.selector(), route)
                        .isTrue();
            }
            observedReadables += readableControls.size();
            observedLayouts += layoutControls.size();
        }
        selectorAssertions.assertAll();
        assertThat(observedControls)
                .as("runtime controls observed across every published route")
                .isGreaterThanOrEqualTo(180);
        assertThat(observedReadables).as("addressable read-only outputs across all routes").isPositive();
        assertThat(observedLayouts).as("addressable layouts across all routes").isPositive();
    }

    @Test
    @DisplayName("option matrix enumerates and restores every supported control choice")
    void exhaustiveOptionMatrixHasExactControlEvidence() {
        JPanel first = new JPanel();
        JComboBox<String> selector = new JComboBox<>(new String[]{"Rose", "Dahlia", "Tulip"});
        selector.getAccessibleContext().setAccessibleName("Fixture selector");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1, 0, 3, 1));
        spinner.getAccessibleContext().setAccessibleName("Fixture spinner");
        JSlider slider = new JSlider(0, 4, 2);
        slider.getAccessibleContext().setAccessibleName("Fixture slider");
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Rose", 4}, {"Dahlia", 2}},
                new Object[]{"Flower", "Quantity"}));
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getAccessibleContext().setAccessibleName("Fixture table");
        JList<String> list = new JList<>(new String[]{"Cut", "Condition", "Pack"});
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.getAccessibleContext().setAccessibleName("Fixture list");
        JCheckBox check = new JCheckBox("Fixture check", true);
        check.getAccessibleContext().setAccessibleName("Fixture check");
        JRadioButton firstRadio = new JRadioButton("First", true);
        firstRadio.getAccessibleContext().setAccessibleName("Fixture radio one");
        JRadioButton secondRadio = new JRadioButton("Second");
        secondRadio.getAccessibleContext().setAccessibleName("Fixture radio two");
        ButtonGroup group = new ButtonGroup();
        group.add(firstRadio);
        group.add(secondRadio);
        AtomicInteger enters = new AtomicInteger();
        JTextField field = new JTextField("ready");
        field.getAccessibleContext().setAccessibleName("Fixture field");
        field.addActionListener(event -> enters.incrementAndGet());
        JTextArea area = new JTextArea();
        area.getAccessibleContext().setAccessibleName("Fixture area");
        JPanel longView = new JPanel();
        longView.setPreferredSize(new Dimension(300, 300));
        JScrollPane scroll = new JScrollPane(longView);
        scroll.setPreferredSize(new Dimension(100, 100));
        scroll.getAccessibleContext().setAccessibleName("Fixture scroll");
        first.add(selector);
        first.add(spinner);
        first.add(slider);
        first.add(table);
        first.add(list);
        first.add(check);
        first.add(firstRadio);
        first.add(secondRadio);
        first.add(field);
        first.add(area);
        first.add(scroll);
        JTabbedPane tabs = new JTabbedPane();
        tabs.getAccessibleContext().setAccessibleName("Fixture tabs");
        tabs.addTab("Options", first);
        tabs.addTab("Other", new JPanel());

        List<SwingUserHarness.RoutedOptionSweep> sweeps = SwingUserHarness.on(tabs)
                .layoutAt(1400, 900)
                .exerciseEveryOptionOnEveryTabSurface();

        assertThat(sweeps).allSatisfy(sweep -> {
            assertThat(sweep.exercised()).containsExactlyElementsOf(sweep.available());
            assertThat(sweep.restored()).isTrue();
        });
        assertThat(option(sweeps, "Fixture tabs").available()).hasSize(2);
        assertThat(option(sweeps, "Fixture selector").available()).hasSize(3);
        assertThat(option(sweeps, "Fixture spinner").available()).hasSize(4);
        assertThat(option(sweeps, "Fixture slider").available()).hasSize(5);
        assertThat(option(sweeps, "Fixture scroll").available())
                .anyMatch(value -> value.startsWith("vertical "))
                .anyMatch(value -> value.startsWith("horizontal "));
        assertThat(option(sweeps, "Fixture table").available()).hasSize(7);
        assertThat(option(sweeps, "Fixture list").available()).hasSize(4);
        assertThat(option(sweeps, "Fixture check").available())
                .containsExactly("false", "true");
        assertThat(option(sweeps, "Fixture radio one").available()).containsExactly("true");
        assertThat(option(sweeps, "Fixture radio two").available()).containsExactly("true");
        assertThat(option(sweeps, "Fixture field").available())
                .containsExactly("blank", "restore original", "Enter");
        assertThat(enters).hasValue(1);
        assertThat(selector.getSelectedIndex()).isZero();
        assertThat(spinner.getValue()).isEqualTo(1);
        assertThat(slider.getValue()).isEqualTo(2);
        assertThat(check.isSelected()).isTrue();
        assertThat(firstRadio.isSelected()).isTrue();
        assertThat(secondRadio.isSelected()).isFalse();
        assertThat(field.getText()).isEqualTo("ready");
        assertThat(area.getText()).isEmpty();
    }

    @Test
    @DisplayName("option matrix and button storm exercise every enabled workflow control")
    void buttonStormClicksEveryEnabledWorkflowControl() {
        InventoryService inventory = mock(InventoryService.class);
        HarvestService harvest = mock(HarvestService.class);
        CustomerService customers = mock(CustomerService.class);
        OrderService orders = mock(OrderService.class);
        MarketDayPackingService packing = mock(MarketDayPackingService.class);
        ReportService reports = mock(ReportService.class);
        SyncHistoryService history = mock(SyncHistoryService.class);
        TrendService trends = mock(TrendService.class);
        IrrigationAdvisorService irrigation = mock(IrrigationAdvisorService.class);
        MorningBriefingService briefing = mock(MorningBriefingService.class);
        DayCloseoutService closeout = mock(DayCloseoutService.class);
        CustomerStatementService statements = mock(CustomerStatementService.class);
        KnowledgeService knowledge = mock(KnowledgeService.class);
        SeasonPlanService seasons = mock(SeasonPlanService.class);
        com.flowerfarm.service.ProductionStudioService production =
                mock(com.flowerfarm.service.ProductionStudioService.class);
        int currentYear = LocalDate.now().getYear();
        SeasonPlanService.SeasonSummary emptySeason = new SeasonPlanService.SeasonSummary(
                0, 0, 0, 0, 0, 0, null, null);
        when(seasons.availableSeasons()).thenReturn(List.of(currentYear));
        when(seasons.analyze(anyInt())).thenReturn(new SeasonPlanService.SeasonAnalysis(
                List.of(), emptySeason, List.of(), List.of(), List.of(), List.of(), 0, 0, 0));
        when(trends.listSeries()).thenReturn(List.of());
        try (SwingUserHarness.AsyncFailureMonitor asyncFailures =
                     SwingUserHarness.monitorAsyncFailures()) {
            SwingDialogHarness dialogs = new SwingDialogHarness().safelyCancelUnscripted();
            SwingFileHarness files = new SwingFileHarness().cancelUnscripted();
            FarmTabCatalog.Catalog catalog = new FarmTabCatalog(
                    inventory, trends, harvest, history, customers, orders, reports,
                    irrigation, packing, briefing, closeout, statements, knowledge, seasons,
                    production)
                    .create(new HarnessHost(), dialogs, files);
            List<SwingUserHarness.RoutedOptionSweep> optionSweeps = new ArrayList<>();
            List<SwingUserHarness.RoutedButtonSweep> sweeps = new ArrayList<>();
            List<String> transcripts = new ArrayList<>();

            for (FlowerFarmTab tab : catalog.tabs()) {
                SwingUserHarness user = SwingUserHarness.on(build(tab)).layoutAt(1400, 900);
                optionSweeps.addAll(user.exerciseEveryOptionOnEveryTabSurface());
                sweeps.addAll(user.clickEveryEnabledButtonOnEveryTabSurface());
                transcripts.addAll(user.transcript());
            }

            assertThat(optionSweeps).isNotEmpty().allSatisfy(sweep -> {
                assertThat(sweep.route()).isNotBlank();
                assertThat(sweep.selector()).isNotBlank();
                assertThat(sweep.exercised())
                        .as("all options for %s '%s' on %s",
                                sweep.kind(), sweep.selector(), sweep.route())
                        .containsExactlyElementsOf(sweep.available());
                assertThat(sweep.restored())
                        .as("original state restored for %s '%s' on %s",
                                sweep.kind(), sweep.selector(), sweep.route())
                        .isTrue();
            });
            assertThat(optionSweeps.stream()
                    .map(SwingUserHarness.RoutedOptionSweep::kind).distinct().toList())
                    .contains("tabs", "selector", "spinner", "slider", "scroll", "table",
                            "toggle", "field");
            assertThat(optionSweeps.stream()
                    .mapToInt(sweep -> sweep.exercised().size()).sum())
                    .as("finite choices and practical field boundaries exercised")
                    .isGreaterThanOrEqualTo(250);
            assertThat(sweeps).allSatisfy(sweep ->
                    assertThat(sweep.clicked().size() + sweep.unavailable().size())
                            .isEqualTo(sweep.discovered().size()));
            assertThat(sweeps.stream().mapToInt(sweep -> sweep.discovered().size()).sum())
                    .as("enabled application buttons discovered before the storm")
                    .isGreaterThanOrEqualTo(65);
            assertThat(sweeps.stream().mapToInt(sweep -> sweep.clicked().size()).sum())
                    .as("production button listeners actually activated")
                    .isGreaterThanOrEqualTo(55);
            assertThat(sweeps.stream().mapToInt(sweep -> sweep.unavailable().size()).sum())
                    .as("buttons discovered as enabled but locked by an earlier storm action")
                    .isZero();
            assertThat(transcripts).noneMatch(line -> line.contains("SETTLE TIMEOUT"));
            assertThat(transcripts).anyMatch(
                    line -> line.startsWith("ROUTED BUTTON STORM CLICK"));
            assertThat(dialogs.transcript()).anyMatch(line -> line.startsWith("SAFE CANCEL"));
            asyncFailures.assertNone();
            dialogs.assertComplete();
            files.assertComplete();
        }
    }

    @Test
    @DisplayName("compact harvest layout keeps entry visible and history reachable by scrolling")
    void auditsEffectiveViewportClippingAtCompactDesktopSize() {
        HarvestService harvest = mock(HarvestService.class);
        HarvestLogTab tab = new HarvestLogTab(harvest, new HarnessHost());
        SwingUserHarness user = SwingUserHarness.on(build(tab)).layoutAt(1050, 700);

        SwingUserHarness.VisibleExtent workspace =
                user.visibleExtent("harvest.workspace");
        SwingUserHarness.VisibleExtent crop =
                user.visibleExtent("harvest.crop");
        SwingUserHarness.VisibleExtent quantity =
                user.visibleExtent("harvest.quantity");
        SwingUserHarness.VisibleExtent add =
                user.visibleExtent("harvest.add");

        onEdt(() -> {
            javax.swing.JScrollPane scroll = user.named(
                    "harvest.workspaceScroll", javax.swing.JScrollPane.class);
            scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
            return null;
        });
        SwingUserHarness.VisibleExtent history = user.visibleExtent("harvest.history.table");

        assertThat(history.height())
                .as("actual harvest rows reachable through the compact workspace scroll")
                .isGreaterThanOrEqualTo(50);
        assertThat(workspace.height())
                .as("actual quick-entry workspace visible before scrolling to history")
                .isGreaterThanOrEqualTo(240);
        assertThat(crop.height())
                .as("primary crop input visible without scrolling")
                .isGreaterThanOrEqualTo(24);
        assertThat(quantity.height())
                .as("primary quantity input visible without scrolling")
                .isGreaterThanOrEqualTo(24);
        assertThat(add.height())
                .as("primary harvest action remains visible below optional details")
                .isGreaterThanOrEqualTo(34);
        assertThat(user.transcript()).contains("LAYOUT 1050x700");
    }

    private static void stubHarvestHistory(
            HarvestService harvest, List<HarvestEntry> history) {
        when(harvest.filter(anyString(), anyString(), anyString(),
                nullable(LocalDate.class), nullable(LocalDate.class)))
                .thenAnswer(invocation -> {
                    String crop = invocation.getArgument(0, String.class)
                            .toLowerCase(java.util.Locale.ROOT);
                    String bed = invocation.getArgument(1, String.class)
                            .toLowerCase(java.util.Locale.ROOT);
                    String notes = invocation.getArgument(2, String.class)
                            .toLowerCase(java.util.Locale.ROOT);
                    LocalDate from = invocation.getArgument(3);
                    LocalDate to = invocation.getArgument(4);
                    return history.stream()
                            .filter(entry -> entry.getCropName().toLowerCase(
                                    java.util.Locale.ROOT).contains(crop))
                            .filter(entry -> entry.getBedOrField().toLowerCase(
                                    java.util.Locale.ROOT).contains(bed))
                            .filter(entry -> entry.getNotes().toLowerCase(
                                    java.util.Locale.ROOT).contains(notes))
                            .filter(entry -> from == null
                                    || !entry.getHarvestDate().isBefore(from))
                            .filter(entry -> to == null
                                    || !entry.getHarvestDate().isAfter(to))
                            .toList();
                });
        when(harvest.totalsByProductUnit()).thenAnswer(
                invocation -> harvestTotals(history));
        when(harvest.totalsByProductUnit(anyList())).thenAnswer(
                invocation -> harvestTotals(invocation.getArgument(0)));
    }

    private static Map<String, Double> harvestTotals(List<HarvestEntry> entries) {
        Map<String, Double> totals = new java.util.LinkedHashMap<>();
        for (HarvestEntry entry : entries) {
            totals.merge(entry.getCropName(), entry.getQuantity(), Double::sum);
        }
        return totals;
    }

    private static HarvestEntry harvest(long id, HarvestEntry entry) {
        return new HarvestEntry(id, entry.getHarvestDate(), entry.getCropName(),
                entry.getQuantity(), entry.getUnit(), entry.getBedOrField(), entry.getNotes());
    }

    private static void stubSeasonAnalysis(
            SeasonPlanService plans, List<SeasonPlanEntry> entries) {
        when(plans.availableSeasons()).thenReturn(List.of(LocalDate.now().getYear()));
        when(plans.analyze(anyInt())).thenAnswer(invocation -> {
            int year = invocation.getArgument(0);
            List<SeasonPlanEntry> view = entries.stream()
                    .filter(entry -> entry.getSeasonYear() == year)
                    .toList();
            int active = (int) view.stream()
                    .filter(entry -> entry.getStatus() != SeasonPlanStatus.COMPLETE).count();
            int beds = (int) view.stream().map(entry -> entry.getBedOrField()
                    .toLowerCase(java.util.Locale.ROOT)).distinct().count();
            int crops = (int) view.stream().map(entry -> entry.getCropName()
                    .toLowerCase(java.util.Locale.ROOT)).distinct().count();
            double bedFeet = view.stream().mapToDouble(SeasonPlanEntry::getBedFeet).sum();
            long stems = view.stream().mapToLong(SeasonPlanEntry::getExpectedStems).sum();
            LocalDate first = view.stream().map(SeasonPlanEntry::getHarvestStart)
                    .min(LocalDate::compareTo).orElse(null);
            LocalDate last = view.stream().map(SeasonPlanEntry::getHarvestEnd)
                    .max(LocalDate::compareTo).orElse(null);
            SeasonPlanService.SeasonSummary summary = new SeasonPlanService.SeasonSummary(
                    view.size(), active, beds, crops, bedFeet, stems, first, last);
            List<SeasonPlanService.PlanPerformance> performance = view.stream()
                    .map(entry -> new SeasonPlanService.PlanPerformance(
                            entry, 0, 0, 0, 0, SeasonPlanService.PlanPace.NOT_STARTED))
                    .toList();
            return new SeasonPlanService.SeasonAnalysis(
                    view, summary, performance, List.of(), List.of(), List.of(), 0, 0, 0);
        });
    }

    private static SeasonPlanEntry seasonSuccession(SeasonPlanEntry source, int days) {
        return new SeasonPlanEntry(source.getSeasonYear(), source.getCropName(),
                source.getVariety(), source.getBedOrField(), source.getSowDate().plusDays(days),
                source.getTransplantDate() == null ? null
                        : source.getTransplantDate().plusDays(days),
                source.getHarvestStart().plusDays(days),
                source.getHarvestEnd().plusDays(days), source.getBedFeet(),
                source.getExpectedStems(), SeasonPlanStatus.PLANNED, source.getNotes());
    }

    private static void stubKnowledgeSearch(
            KnowledgeService knowledge, List<KnowledgeEntry> lessons) {
        when(knowledge.search(any(KnowledgeService.SearchRequest.class)))
                .thenAnswer(invocation -> {
                    KnowledgeService.SearchRequest request = invocation.getArgument(0);
                    String query = request.query().toLowerCase(java.util.Locale.ROOT);
                    String tag = request.tag().toLowerCase(java.util.Locale.ROOT);
                    return lessons.stream()
                            .filter(entry -> query.isBlank()
                                    || knowledgeText(entry).contains(query))
                            .filter(entry -> tag.isBlank() || entry.getTags().contains(tag))
                            .filter(entry -> !Boolean.TRUE.equals(request.followUpDue())
                                    || (entry.getFollowUpDate() != null
                                    && !entry.getFollowUpDate().isAfter(LocalDate.now())))
                            .toList();
                });
        when(knowledge.distinctTags()).thenAnswer(invocation -> lessons.stream()
                .flatMap(entry -> entry.getTags().stream()).sorted().distinct().toList());
    }

    private static String knowledgeText(KnowledgeEntry entry) {
        return String.join(" ", entry.getSummary(), entry.getCropName(), entry.getVariety(),
                entry.getBedOrField(), entry.getObservation(), entry.getActionTaken(),
                entry.getResult(), String.join(" ", entry.getTags()))
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static KnowledgeEntry knowledge(
            long id, LocalDate observedOn, String summary, String crop,
            LocalDate followUp, KnowledgeConfidence confidence, List<String> tags) {
        KnowledgeEntry entry = new KnowledgeEntry(
                observedOn, summary, KnowledgeKind.PRACTICE, crop, "", "Field A", "Growing",
                summary + " observed in the field.", "Reviewed the growing routine.",
                "Farm crew", "Documented for the next planting.", followUp, confidence, tags);
        entry.setId(id);
        return entry;
    }

    private static KnowledgeEntry knowledge(long id, KnowledgeEntry source) {
        KnowledgeEntry entry = new KnowledgeEntry(
                source.getObservedOn(), source.getSummary(), source.getKind(),
                source.getCropName(), source.getVariety(), source.getBedOrField(),
                source.getProductionStage(), source.getObservation(), source.getActionTaken(),
                source.getPerformedBy(), source.getResult(), source.getFollowUpDate(),
                source.getConfidence(), source.getTags());
        entry.setId(id);
        entry.setHarvestId(source.getHarvestId());
        entry.setInventoryItemId(source.getInventoryItemId());
        entry.setOrderId(source.getOrderId());
        entry.setCustomerId(source.getCustomerId());
        return entry;
    }

    private static JComponent build(FlowerFarmTab tab) {
        return onEdt(tab::getUIComponent);
    }

    private static SwingUserHarness.RoutedOptionSweep option(
            List<SwingUserHarness.RoutedOptionSweep> sweeps, String selector) {
        return sweeps.stream()
                .filter(sweep -> selector.equals(sweep.selector()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No option evidence for selector '" + selector + "'"));
    }

    private static Item item(long id, String name, int quantity) {
        return new Item(id, name, "Flowers/Plants", 4.5, "Per Stem", 1.1,
                quantity, "Field grown");
    }

    private static TrendService.TrendResult trendResult(
            String crop, double forecast, LocalDate firstWeek, LocalDate latestWeek) {
        return new TrendService.TrendResult(
                forecast, crop + " latest-selection forecast", null,
                crop, "stems", 3, forecast - 8, latestWeek, latestWeek.plusWeeks(1),
                List.of(
                        new TrendService.TrendPoint(firstWeek, forecast - 24, forecast - 23),
                        new TrendService.TrendPoint(
                                firstWeek.plusWeeks(1), forecast - 16, forecast - 15),
                        new TrendService.TrendPoint(latestWeek, forecast - 8, forecast - 7)),
                8, forecast - 31, 0.92);
    }

    private static void stubEmptyOrders(OrderService orders) {
        when(orders.filter(anyString(), anyString(), nullable(LocalDate.class),
                nullable(LocalDate.class))).thenReturn(List.of());
        when(orders.confirmedCompletionQueue()).thenReturn(List.of());
    }

    private static MarketDayPackingService.MarketDayPlan marketPlan(
            LocalDate date, MarketDayPackingService.CustomerPack customer,
            MarketDayPackingService.ProductNeed need, int shortfallCount) {
        return new MarketDayPackingService.MarketDayPlan(
                date, date, date, "CONFIRMED", 1, 120,
                List.of(customer), List.of(need), shortfallCount,
                "MARKET DAY PACKING LIST\nDahlia Mix 12 stems\nKitsap Blooms");
    }

    private static MarketDayPackingService.MarketDayPlan emptyMarketPlan(LocalDate date) {
        return new MarketDayPackingService.MarketDayPlan(
                date, date, date, "CONFIRMED", 0, 0,
                List.of(), List.of(), 0,
                "MARKET DAY PACKING LIST\nNo confirmed orders in this window");
    }

    private static boolean controlEnabled(
            SwingUserHarness.SurfaceAudit audit, String kind, String selector) {
        return audit.controls().stream()
                .filter(control -> kind.equals(control.kind())
                        && selector.equals(control.selector()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + kind + " control named '" + selector + "'"))
                .enabled();
    }

    private static void initialize(FlowerFarmTab tab) {
        onEdt(() -> {
            tab.initialize();
            return null;
        });
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> task) {
        if (SwingUtilities.isEventDispatchThread()) {
            return call(task);
        }
        Object[] result = new Object[1];
        Throwable[] failure = new Throwable[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result[0] = task.call();
                } catch (Throwable throwable) {
                    failure[0] = throwable;
                }
            });
        } catch (Exception exception) {
            throw new AssertionError("Could not build the Swing workflow", exception);
        }
        if (failure[0] != null) {
            throw new AssertionError("Could not build the Swing workflow", failure[0]);
        }
        @SuppressWarnings("unchecked")
        T typed = (T) result[0];
        return typed;
    }

    private static <T> T call(java.util.concurrent.Callable<T> task) {
        try {
            return task.call();
        } catch (Exception exception) {
            throw new AssertionError("Could not build the Swing workflow", exception);
        }
    }

    private static final class HarnessHost implements TabHost {
        private final List<String> selectedTabs = new ArrayList<>();
        private List<String> inventoryProducts = List.of();
        private int refreshCount;
        private String lastStatus = "";
        private String eventBrief = "";
        private boolean canWrite = true;
        private boolean canClearHistory = true;

        @Override
        public void refreshAll() {
            refreshCount++;
        }

        @Override
        public void selectTab(String tabTitle) {
            selectedTabs.add(tabTitle);
        }

        @Override
        public void setStatus(String message) {
            lastStatus = message;
        }

        @Override
        public void openStartHere() {
            selectedTabs.add("Start here");
            lastStatus = "Opened Start here";
        }

        @Override
        public void runTrendAnalysis() {
            selectedTabs.add("Trend Analysis");
        }

        @Override
        public boolean canMutateData() {
            return canWrite;
        }

        @Override
        public boolean canClearHistory() {
            return canClearHistory;
        }

        @Override
        public void showInventoryProducts(
                List<String> productNames, String returnTab, String returnLabel) {
            inventoryProducts = productNames == null ? List.of() : List.copyOf(productNames);
            selectedTabs.add("Inventory");
            lastStatus = "Opened Inventory for " + String.join(", ", inventoryProducts);
        }

        @Override
        public boolean showEventOrderDraft(String occasion, String services, String brief) {
            eventBrief = brief == null ? "" : brief;
            selectedTabs.add("Customers & Orders");
            lastStatus = "Prepared event brief for " + services;
            return true;
        }
    }

    private static final class JourneyWorkspaceHost implements TabHost {
        private final List<String> selectedTabs = new ArrayList<>();
        private JTabbedPane workspace;
        private CrmTab crmTab;
        private String lastStatus = "";

        private void attach(JTabbedPane tabs, CrmTab crm) {
            workspace = tabs;
            crmTab = crm;
        }

        @Override
        public void refreshAll() {
            // Each journey initializes its deterministic tabs directly.
        }

        @Override
        public void selectTab(String tabTitle) {
            selectedTabs.add(tabTitle);
            for (int index = 0; index < workspace.getTabCount(); index++) {
                if (tabTitle.equals(workspace.getTitleAt(index))) {
                    workspace.setSelectedIndex(index);
                    return;
                }
            }
            throw new AssertionError("No workspace tab named '" + tabTitle + "'");
        }

        @Override
        public void setStatus(String message) {
            lastStatus = message;
        }

        @Override
        public void showOrder(long orderId, String returnTab, String returnLabel) {
            selectTab("Customers & Orders");
            crmTab.showOrder(orderId, returnTab, returnLabel);
        }

        @Override
        public void showOrderLineCorrection(
                long orderId, Long lineId, String productName,
                String returnTab, String returnLabel) {
            selectTab("Customers & Orders");
            crmTab.showOrderLineCorrection(
                    orderId, lineId, productName, returnTab, returnLabel);
        }

        @Override
        public void runTrendAnalysis() {
            selectTab("Trend Analysis");
        }
    }
}
