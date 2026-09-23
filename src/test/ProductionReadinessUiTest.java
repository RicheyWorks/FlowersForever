package com.flowerfarm.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.flowerfarm.auth.DesktopSession;
import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.auth.FarmUser;
import com.flowerfarm.auth.FarmUserDirectory;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingFileHarness;
import com.flowerfarm.gui.harness.SwingMenuHarness;
import com.flowerfarm.gui.harness.SwingUserHarness;
import com.flowerfarm.gui.tabs.HarvestLogTab;
import com.flowerfarm.gui.tabs.CrmTab;
import com.flowerfarm.gui.tabs.InventoryTab;
import com.flowerfarm.gui.tabs.MarketDayTab;
import com.flowerfarm.gui.tabs.ProductionStudioTab;
import com.flowerfarm.connector.ConnectorResult;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.Customer;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.KnowledgeService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import com.flowerfarm.service.SeasonPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.MatteBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Desktop production-readiness access scenarios")
class ProductionReadinessUiTest {

    @Test
    @DisplayName("frame status and integrations chrome follows runtime theme changes")
    void frameChromeRethemesAtRuntime() throws Exception {
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        JPanel south = gui(gate, session).buildSouthBar();
        JPanel status = (JPanel) named(south, "farm.statusBar");
        JScrollPane connectors = (JScrollPane) named(south, "farm.connectorScroll");

        try {
            FlatDarkLaf.setup();
            FarmUi.installThemeDefaults();
            SwingUtilities.invokeAndWait(() -> {
                SwingUtilities.updateComponentTreeUI(south);
                FarmUi.refreshTheme(south);
            });

            assertThat(status.getBackground()).isEqualTo(FarmUi.surfaceColor());
            assertThat(((MatteBorder) status.getBorder()).getMatteColor())
                    .isEqualTo(FarmUi.borderColor());
            assertThat(((MatteBorder) connectors.getBorder()).getMatteColor())
                    .isEqualTo(FarmUi.borderColor());
            JButton importCsv = button(connectors, "Import CSV");
            assertThat(importCsv.getToolTipText()).contains("HAND or OWNER");
            assertThat(importCsv.getAccessibleContext().getAccessibleDescription())
                    .contains("Import CSV", "HAND or OWNER");
        } finally {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
        }
    }

    @Test
    @DisplayName("desktop menus expose complete keyboard and assistive-technology access")
    void menuKeyboardAccess() {
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        JMenuBar menu = gui(gate, session).buildMenuBar();

        assertThat(menu.getMenu(0).getMnemonic()).isEqualTo(KeyEvent.VK_F);
        assertThat(menu.getMenu(1).getMnemonic()).isEqualTo(KeyEvent.VK_V);
        assertThat(menu.getMenu(2).getMnemonic()).isEqualTo(KeyEvent.VK_T);
        assertThat(menu.getMenu(3).getMnemonic()).isEqualTo(KeyEvent.VK_A);
        assertThat(menu.getMenu(4).getMnemonic()).isEqualTo(KeyEvent.VK_H);
        assertThat(menu.getMenu(0).getAccessibleContext().getAccessibleDescription())
                .contains("Import farm data");
        assertThat(menuItem(menu, "Import CSV…").getMnemonic()).isEqualTo(KeyEvent.VK_C);
        assertThat(menuItem(menu, "Refresh All Tabs").getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        assertThat(menuItem(menu, "Dark Mode").getAccelerator()).isEqualTo(
                KeyStroke.getKeyStroke(KeyEvent.VK_D,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        assertThat(menuItem(menu, "PNW Plant Guide").getAccelerator())
                .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        assertThat(menuItem(menu, "Wedding & Memorial Services")
                .getAccessibleContext().getAccessibleDescription())
                .contains("catering", "photography");
        assertThat(menuItem(menu, "My access").getAccessibleContext()
                .getAccessibleDescription()).contains("current worker");
    }

    @Test
    @DisplayName("login form is addressable through human-facing keyboard labels")
    void loginFormHasHarnessableCredentialsAndMnemonics() {
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = new GuiLoginGate(
                true, false, mock(FarmUserDirectory.class), session);
        JPanel login = gate.buildLoginPanel();
        SwingUserHarness user = SwingUserHarness.on(login);

        user.type("Farm username", "field-hand")
                .type("Farm password", "temporary-secret");

        assertThat(user.fieldValue("Farm username")).isEqualTo("field-hand");
        assertThat(user.fieldValue("Farm password")).isEqualTo("temporary-secret");
        assertThat(user.auditInteractiveSurface().defects()).isEmpty();
        assertThat(labels(login)).filteredOn(label -> "Username".equals(label.getText()))
                .singleElement().satisfies(label -> {
                    assertThat(label.getDisplayedMnemonic()).isEqualTo(KeyEvent.VK_U);
                    assertThat(label.getLabelFor()).isNotNull();
                });
        assertThat(labels(login)).filteredOn(label -> "Password".equals(label.getText()))
                .singleElement().satisfies(label -> {
                    assertThat(label.getDisplayedMnemonic()).isEqualTo(KeyEvent.VK_P);
                    assertThat(label.getLabelFor()).isNotNull();
                });
    }

    @Test
    @DisplayName("menu harness audits every command and safely activates non-terminal actions")
    void menuHarnessCoversInstalledShellCommands() {
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        SwingDialogHarness dialogs = new SwingDialogHarness().safelyCancelUnscripted();
        SwingFileHarness files = new SwingFileHarness().cancelUnscripted();
        FlowerFarmGUI gui = gui(gate, session, dialogs, files);
        gui.buildSouthBar();
        SwingMenuHarness menu = SwingMenuHarness.on(gui.buildMenuBar());

        SwingMenuHarness.MenuAudit audit = menu.audit();
        assertThat(audit.controls()).hasSizeGreaterThanOrEqualTo(13);
        assertThat(audit.defects()).isEmpty();
        assertThat(audit.controls()).filteredOn(SwingMenuHarness.MenuControl::enabled)
                .allSatisfy(control -> {
                    assertThat(control.wired()).isTrue();
                    assertThat(control.mnemonic()).isNotZero();
                    assertThat(control.description()).isNotBlank();
                });

        assertThat(menu.exerciseEveryToggleState()).singleElement().satisfies(sweep -> {
            assertThat(sweep.label()).isEqualTo("Dark Mode");
            assertThat(sweep.exercised()).containsExactlyElementsOf(sweep.available());
            assertThat(sweep.restored()).isTrue();
        });

        SwingMenuHarness.MenuSweep sweep = menu.clickEveryEnabledExcept(
                "Import CSV…", "Import Excel…", "Exit");
        assertThat(sweep.intentionallyExcluded())
                .containsExactly("Import CSV…", "Import Excel…", "Exit");
        assertThat(sweep.clicked()).contains(
                "Export All to CSV…", "Refresh All Tabs", "Dark Mode",
                "Run Harvest Forecast", "My access", "PNW Plant Guide",
                "Wedding & Memorial Services", "PNW Rose Growing Guide",
                "About FlowersForever");
        assertThat(files.transcript()).containsExactly(
                "SAFE CANCEL SAVE Export Full Inventory to CSV · suggested exported_inventory.csv");
        assertThat(dialogs.transcript()).anyMatch(line -> line.contains("My access"));
        assertThat(dialogs.transcript()).anyMatch(line -> line.contains("About"));
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
    }

    @Test
    @DisplayName("integrations tray exposes every connector action to the human harness")
    void integrationsTrayIsFullyAddressable() throws Exception {
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        ConnectorRegistry registry = mock(ConnectorRegistry.class);
        when(registry.runImport(anyString())).thenReturn(
                ConnectorResult.fail("safe harness import", "expected test boundary"));
        when(registry.runExport(anyString())).thenReturn(
                ConnectorResult.fail("safe harness export", "expected test boundary"));
        when(registry.runSync(anyString())).thenReturn(
                ConnectorResult.fail("safe harness sync", "expected test boundary"));
        SwingDialogHarness dialogs = new SwingDialogHarness().safelyCancelUnscripted();
        SwingFileHarness files = new SwingFileHarness().cancelUnscripted();
        JPanel south = gui(gate, session, registry, dialogs, files).buildSouthBar();
        SwingUserHarness user = SwingUserHarness.on(south).layoutAt(1400, 180);

        user.setChecked("Toggle integrations tray", true);
        SwingUserHarness.SurfaceAudit audit = user.auditInteractiveSurface();
        assertThat(audit.defects()).isEmpty();
        assertThat(audit.controls()).filteredOn(control -> "button".equals(control.kind()))
                .hasSizeGreaterThanOrEqualTo(22)
                .extracting(SwingUserHarness.Control::selector)
                .contains("Toggle integrations tray", "Import CSV", "Export CSV",
                        "Sync Shopify", "Import Airtable", "Send Webhook",
                        "Sync Farmbrite", "Sync Floranext");

        JScrollPane connectors = (JScrollPane) named(south, "farm.connectorScroll");
        try (SwingUserHarness.AsyncFailureMonitor failures =
                     SwingUserHarness.monitorAsyncFailures()) {
            SwingUserHarness.ButtonSweep sweep = SwingUserHarness.on(
                            (JPanel) connectors.getViewport().getView())
                    .layoutAt(1400, 180)
                    .clickEveryEnabledButton();
            assertThat(sweep.discovered()).hasSize(21);
            assertThat(sweep.clicked()).containsExactlyElementsOf(sweep.discovered());
            assertThat(sweep.skipped()).isEmpty();
            verify(registry, timeout(5000).times(8)).runImport(anyString());
            verify(registry, timeout(5000).times(8)).runExport(anyString());
            verify(registry, timeout(5000).times(4)).runSync(anyString());
            SwingUtilities.invokeAndWait(() -> { });
            failures.assertNone();
        }
        user.setChecked("Toggle integrations tray", false)
                .setChecked("Toggle integrations tray", true);
        assertThat(files.transcript()).containsExactly(
                "SAFE CANCEL SAVE Export Full Inventory to CSV · suggested exported_inventory.csv");
        assertThat(dialogs.transcript().stream()
                .filter(line -> line.startsWith("SAFE CLOSE MESSAGE")).count()).isEqualTo(20);
    }

    @Test
    @DisplayName("desktop exit prompt makes keeping unfinished order work the safe choice")
    void exitPromptExplainsExplicitDiscard() {
        String warning = "Customers & Orders still has 1 unfinished product entry.";

        assertThat(FlowerFarmGUI.exitPrompt(warning, false)).contains(
                warning, "Keep working", "without losing anything",
                "Discard and exit", "intentional");
        assertThat(FlowerFarmGUI.exitPrompt(warning, true)).contains(
                warning, "Keep working", "Discard and sign out", "signing out");
        assertThat(FlowerFarmGUI.combinedExitWarning(
                "Customers & Orders has an unfinished product.",
                "Market Day has a checked load.")).contains(
                        "Customers & Orders has an unfinished product.",
                        "Market Day has a checked load.");
        assertThat(FlowerFarmGUI.combinedExitWarning(null, "  ")).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exitAndSignOutKeepHarvestDraftWhenCancelled(boolean signingOut) throws Exception {
        DesktopSession session = new DesktopSession();
        session.setAuthEnabled(true);
        session.set(new FarmUser("field-hand", "", FarmRole.HAND));
        SwingDialogHarness dialogs = new SwingDialogHarness().chooseForm(
                "Unfinished work — " + (signingOut ? "sign out" : "exit"),
                "Keep working", form -> form.expectText("Harvest Log"));
        FlowerFarmGUI gui = gui(mock(GuiLoginGate.class), session, dialogs,
                new SwingFileHarness().cancelUnscripted());
        HarvestLogTab harvest = new HarvestLogTab(mock(HarvestService.class), gui);
        ReflectionTestUtils.setField(gui, "harvestTab", harvest);
        SwingUtilities.invokeAndWait(() -> {
            gui.buildSouthBar();
            SwingUserHarness.on(harvest.getUIComponent())
                    .type("Harvest crop or variety", "Dahlia draft")
                    .type("Harvest quantity", "72");
            assertThat(gui.confirmApplicationClose(signingOut)).isFalse();
            assertThat(SwingUserHarness.on(harvest.getUIComponent())
                    .fieldValue("Harvest crop or variety")).isEqualTo("Dahlia draft");
            assertThat(SwingUserHarness.on(harvest.getUIComponent())
                    .fieldValue("Harvest quantity")).isEqualTo("72");
        });
        assertThat(session.current().username()).isEqualTo("field-hand");
        dialogs.assertComplete();
    }

    @ParameterizedTest
    @CsvSource({"false, 0", "true, 0", "false, 1", "true, 1"})
    void retainedInventoryWorkRequiresExplicitDiscard(boolean signingOut, int choice)
            throws Exception {
        DesktopSession session = new DesktopSession();
        session.setAuthEnabled(true);
        session.set(new FarmUser("field-hand", "", FarmRole.HAND));
        InventoryTab inventory = mock(InventoryTab.class);
        when(inventory.applicationExitWarning()).thenReturn(
                "Inventory has an unsaved stock count and a retained item edit.");
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenAnswer(invocation -> {
                    assertThat(String.valueOf((Object) invocation.getArgument(1))).contains(
                            "Inventory", "unsaved stock count", "retained item edit",
                            "without losing anything", "intentional");
                    assertThat((Object) invocation.getArgument(2)).isEqualTo(
                            "Unfinished work — " + (signingOut ? "sign out" : "exit"));
                    assertThat((Object[]) invocation.getArgument(6)).containsExactly(
                            "Keep working", signingOut ? "Discard and sign out" : "Discard and exit");
                    assertThat((Object) invocation.getArgument(7)).isEqualTo("Keep working");
                    return choice;
                });
        FlowerFarmGUI gui = gui(mock(GuiLoginGate.class), session, dialogs,
                new SwingFileHarness().cancelUnscripted());
        ReflectionTestUtils.setField(gui, "inventoryTab", inventory);
        SwingUtilities.invokeAndWait(() -> {
            gui.buildSouthBar();
            assertThat(gui.confirmApplicationClose(signingOut)).isEqualTo(choice == 1);
        });
        assertThat(session.current().username()).isEqualTo("field-hand");
        verify(dialogs).showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any());
    }

    @ParameterizedTest(name = "{0}: sign out={1}, close response={2}")
    @CsvSource({
            "new, false, 0", "new, true, -1", "new, false, 1",
            "edit, true, 0", "edit, false, -1", "edit, true, 1",
            "failed-add, false, 0", "failed-add, true, -1", "failed-add, true, 1",
            "failed-edit, true, 0", "failed-edit, false, -1", "failed-edit, false, 1"
    })
    void customerOnlyDraftRequiresExplicitShellDiscard(String draftKind, boolean signingOut, int choice)
            throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String prompt = String.valueOf((Object) invocation.getArgument(1));
                    assertThat(prompt).contains("Customers & Orders", "customer", "Keep working",
                            "without losing anything", "intentional")
                            .doesNotContain("unfinished product", "unfinished new-order", "write still running");
                    assertThat((String) invocation.getArgument(2)).isEqualTo(
                            "Unfinished work — " + (signingOut ? "sign out" : "exit"));
                    assertThat((Object[]) invocation.getArgument(6)).containsExactly(
                            "Keep working", signingOut ? "Discard and sign out" : "Discard and exit");
                    assertThat((Object) invocation.getArgument(7)).isEqualTo("Keep working");
                    return choice;
                });
        CustomerExitFixture fixture = new CustomerExitFixture(dialogs);
        fixture.prepareDraft(draftKind.contains("edit"));
        boolean failed = draftKind.startsWith("failed");
        if (failed) {
            if (draftKind.endsWith("edit")) {
                when(fixture.customers.update(eq(11L), any(Customer.class)))
                        .thenThrow(new IllegalStateException("Customer database unavailable"));
            } else {
                when(fixture.customers.add(any(Customer.class)))
                        .thenThrow(new IllegalStateException("Customer database unavailable"));
            }
            fixture.user.click(draftKind.endsWith("edit") ? "Save edit" : "Add customer");
            fixture.user.awaitText("Customer not saved · retry available");
        }

        SwingUtilities.invokeAndWait(() ->
                assertThat(fixture.gui.confirmApplicationClose(signingOut)).isEqualTo(choice == 1));

        fixture.assertRawCustomerDraft();
        assertThat(fixture.session.current().username()).isEqualTo("field-hand");
        assertThat(fixture.crm.hasPendingSave()).isFalse();
        verify(dialogs).showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any());
        verify(dialogs, never()).showConfirm(any(), any(), anyString(), anyInt(), anyInt());
        verify(fixture.customers, times(failed && draftKind.endsWith("add") ? 1 : 0))
                .add(any(Customer.class));
        verify(fixture.customers, times(failed && draftKind.endsWith("edit") ? 1 : 0))
                .update(eq(11L), any(Customer.class));
        verify(fixture.customers, never()).delete(anyLong());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unchangedLoadedCustomerDoesNotTriggerAnUnfinishedWorkPrompt(boolean signingOut) throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    assertThat((String) invocation.getArgument(1)).isEqualTo("Sign out and exit?");
                    assertThat((String) invocation.getArgument(2)).isEqualTo("Sign out");
                    return JOptionPane.YES_OPTION;
                });
        CustomerExitFixture fixture = new CustomerExitFixture(dialogs);
        fixture.loadSavedCustomer();

        SwingUtilities.invokeAndWait(() -> {
            assertThat(fixture.crm.applicationExitWarning()).isNull();
            assertThat(fixture.gui.confirmApplicationClose(signingOut)).isTrue();
        });

        assertThat(fixture.user.fieldValue("Customer or business name")).isEqualTo("Harbor Floral");
        assertThat(fixture.user.fieldValue("Customer notes")).isEqualTo("Saved pickup instructions");
        assertThat(fixture.session.current().username()).isEqualTo("field-hand");
        verify(dialogs, never()).showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any());
        verify(dialogs, times(signingOut ? 1 : 0))
                .showConfirm(any(), any(), anyString(), anyInt(), anyInt());
        verify(fixture.customers, never()).add(any(Customer.class));
        verify(fixture.customers, never()).update(anyLong(), any(Customer.class));
    }

    @ParameterizedTest(name = "pending edit={0}, sign out={1}, response={2}")
    @CsvSource({"false, false, 0", "true, true, -1", "true, false, 1", "false, true, 1"})
    void pendingRealCustomerSaveUsesExitBeforeResult(boolean editing, boolean signingOut, int choice)
            throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    assertThat(String.valueOf((Object) invocation.getArgument(1))).contains(
                            "customer write still running", "does not undo records already saved",
                            "before trying again", "Any unsaved drafts will be lost");
                    assertThat((String) invocation.getArgument(2)).isEqualTo(
                            "Unfinished work — " + (signingOut ? "sign out" : "exit"));
                    assertThat((Object[]) invocation.getArgument(6))
                            .containsExactly("Keep working", "Exit before result");
                    assertThat((Object) invocation.getArgument(7)).isEqualTo("Keep working");
                    return choice;
                });
        CustomerExitFixture fixture = new CustomerExitFixture(dialogs);
        fixture.prepareDraft(editing);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        org.mockito.stubbing.Answer<Customer> blockedSave = invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).as("test releases pending customer write").isTrue();
            throw new IllegalStateException("Customer database unavailable");
        };
        if (editing) {
            when(fixture.customers.update(eq(11L), any(Customer.class))).thenAnswer(blockedSave);
        } else {
            when(fixture.customers.add(any(Customer.class))).thenAnswer(blockedSave);
        }
        try {
            fixture.user.click(editing ? "Save edit" : "Add customer");
            assertThat(entered.await(4, TimeUnit.SECONDS)).isTrue();

            SwingUtilities.invokeAndWait(() -> {
                assertThat(fixture.crm.hasPendingSave()).isTrue();
                assertThat(fixture.gui.confirmApplicationClose(signingOut)).isEqualTo(choice == 1);
            });

            fixture.assertRawCustomerDraft();
            assertThat(fixture.session.current().username()).isEqualTo("field-hand");
            verify(dialogs).showOption(any(), any(), anyString(), anyInt(), anyInt(), any(), any(), any());
        } finally {
            release.countDown();
        }
        fixture.user.awaitText("Customer not saved · retry available");
        fixture.assertRawCustomerDraft();
        assertThat(fixture.crm.hasPendingSave()).isFalse();
        verify(fixture.customers, times(editing ? 1 : 0)).update(eq(11L), any(Customer.class));
        verify(fixture.customers, times(editing ? 0 : 1)).add(any(Customer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"harvestTab", "inventoryTab", "crmTab", "marketDayTab", "productionStudioTab"})
    void exitDuringSubmittedWorkNeverPromisesToDiscardSavedRecords(String field) throws Exception {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any())).thenAnswer(invocation -> {
                    assertThat(String.valueOf((Object) invocation.getArgument(1))).contains(
                            "does not undo records already saved", "before trying again",
                            "Any unsaved drafts will be lost", "Keep working");
                    assertThat((Object[]) invocation.getArgument(6)).containsExactly(
                            "Keep working", "Exit before result");
                    assertThat((Object) invocation.getArgument(7)).isEqualTo("Keep working");
                    return JOptionPane.CLOSED_OPTION;
                });
        FlowerFarmGUI gui = gui(mock(GuiLoginGate.class), new DesktopSession(), dialogs,
                new SwingFileHarness().cancelUnscripted());
        Object tab;
        switch (field) {
            case "inventoryTab" -> {
                InventoryTab inventory = mock(InventoryTab.class);
                when(inventory.applicationExitWarning()).thenReturn("Inventory save is running.");
                when(inventory.hasPendingSave()).thenReturn(true);
                tab = inventory;
            }
            case "harvestTab" -> {
                HarvestLogTab harvest = mock(HarvestLogTab.class);
                when(harvest.applicationExitWarning()).thenReturn("Harvest save is running.");
                when(harvest.hasPendingSave()).thenReturn(true);
                tab = harvest;
            }
            case "crmTab" -> {
                CrmTab crm = mock(CrmTab.class);
                when(crm.applicationExitWarning()).thenReturn("Customer save is running.");
                when(crm.hasPendingSave()).thenReturn(true);
                tab = crm;
            }
            case "marketDayTab" -> {
                MarketDayTab market = mock(MarketDayTab.class);
                when(market.applicationExitWarning()).thenReturn("Packing completion is running.");
                when(market.hasPendingSave()).thenReturn(true);
                tab = market;
            }
            default -> {
                ProductionStudioTab studio = mock(ProductionStudioTab.class);
                when(studio.applicationExitWarning()).thenReturn("Task save is running.");
                when(studio.hasPendingSave()).thenReturn(true);
                tab = studio;
            }
        }
        ReflectionTestUtils.setField(gui, field, tab);
        SwingUtilities.invokeAndWait(() -> {
            gui.buildSouthBar();
            assertThat(gui.confirmApplicationClose(false)).isFalse();
        });
        verify(dialogs).showOption(any(), any(), anyString(), anyInt(), anyInt(),
                any(), any(), any());
    }

    @Test
    @DisplayName("cancelling the shell export leaves files alone and reports the outcome")
    void shellExportCancellationIsVisible() {
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        FlowerFarmGUI gui = gui(gate, session, dialogs,
                (parent, title, suggested) -> Optional.empty());
        JPanel south = gui.buildSouthBar();

        menuItem(gui.buildMenuBar(), "Export All to CSV…").doClick();

        JLabel status = (JLabel) named(south, "farm.statusMessage");
        assertThat(status.getText()).isEqualTo("Export cancelled. No file was changed.");
        verifyNoInteractions(dialogs);
    }

    @Test
    @DisplayName("shell export requires confirmation before replacing an existing file")
    void shellExportProtectsExistingFile(@TempDir Path tempDir) throws Exception {
        Path existing = Files.createFile(tempDir.resolve("inventory.csv"));
        DesktopSession session = new DesktopSession();
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showConfirm(any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(JOptionPane.NO_OPTION);
        FlowerFarmGUI gui = gui(gate, session, dialogs,
                (parent, title, suggested) -> Optional.of(existing.toFile()));
        JPanel south = gui.buildSouthBar();

        menuItem(gui.buildMenuBar(), "Export All to CSV…").doClick();

        JLabel status = (JLabel) named(south, "farm.statusMessage");
        assertThat(status.getText())
                .isEqualTo("Export cancelled. Existing file was not replaced.");
        assertThat(Files.size(existing)).isZero();
        verify(dialogs).showConfirm(any(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("viewer can create local exports but cannot import, sync, or publish")
    void viewerShellActionsAreCapabilityAccurateAcrossRoleTransitions() {
        DesktopSession session = new DesktopSession();
        session.setAuthEnabled(true);
        session.set(new FarmUser("viewer", "", FarmRole.VIEWER));
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(true);
        FlowerFarmGUI gui = gui(gate, session);

        JScrollPane connectors = gui.buildConnectorBar();
        JMenuBar menu = gui.buildMenuBar();
        gui.applyRoleToUi();

        assertThat(menuItem(menu, "Import CSV…").isEnabled()).isFalse();
        assertThat(menuItem(menu, "Import Excel…").isEnabled()).isFalse();
        assertThat(menuItem(menu, "Export All to CSV…").isEnabled()).isTrue();
        assertThat(button(connectors, "Import CSV").isEnabled()).isFalse();
        assertThat(button(connectors, "Export CSV").isEnabled()).isTrue();
        assertThat(button(connectors, "Export Excel").isEnabled()).isTrue();
        assertThat(button(connectors, "Sync Shopify").isEnabled()).isFalse();
        assertThat(button(connectors, "Export Shopify").isEnabled()).isFalse();

        session.set(new FarmUser("field-hand", "", FarmRole.HAND));
        gui.applyRoleToUi();

        assertThat(menuItem(menu, "Import CSV…").isEnabled()).isTrue();
        assertThat(button(connectors, "Import CSV").isEnabled()).isTrue();
        assertThat(button(connectors, "Sync Shopify").isEnabled()).isTrue();
        assertThat(button(connectors, "Export Shopify").isEnabled()).isTrue();
    }

    private static FlowerFarmGUI gui(GuiLoginGate gate, DesktopSession session) {
        return gui(gate, session, FarmDialogs.swing(), FarmFileChooser.swing());
    }

    private static final class CustomerExitFixture {
        private final CustomerService customers = mock(CustomerService.class);
        private final OrderService orders = mock(OrderService.class);
        private final DesktopSession session = new DesktopSession();
        private final FlowerFarmGUI gui;
        private final CrmTab crm;
        private final JComponent root;
        private final SwingUserHarness user;

        private CustomerExitFixture(FarmDialogs shellDialogs) throws Exception {
            Customer saved = new Customer(11L, "Harbor Floral", "Saved contact", "saved@example.test",
                    "360-555-0184", "WHOLESALE", "Saved pickup instructions");
            when(customers.search(anyString())).thenReturn(List.of(saved));
            when(customers.getAll()).thenReturn(List.of(saved));
            when(orders.filter(anyString(), anyString(), any(), any())).thenReturn(List.of());
            when(orders.confirmedCompletionQueue()).thenReturn(List.of());
            session.setAuthEnabled(true);
            session.set(new FarmUser("field-hand", "", FarmRole.HAND));
            gui = gui(mock(GuiLoginGate.class), session, shellDialogs,
                    new SwingFileHarness().cancelUnscripted());
            crm = new CrmTab(customers, orders, gui, mock(FarmDialogs.class));
            ReflectionTestUtils.setField(gui, "crmTab", crm);
            JComponent[] built = new JComponent[1];
            SwingUtilities.invokeAndWait(() -> {
                gui.buildSouthBar();
                built[0] = crm.getUIComponent();
                crm.initialize();
            });
            root = built[0];
            user = SwingUserHarness.on(root);
            user.awaitButtonEnabled("Refresh CRM", true);
        }

        private void loadSavedCustomer() throws Exception {
            SwingUtilities.invokeAndWait(() -> user.named("crm.customers.table", JTable.class)
                    .setRowSelectionInterval(0, 0));
            user.click("Load selected");
        }

        private void prepareDraft(boolean editing) throws Exception {
            if (editing) {
                loadSavedCustomer();
            }
            user.type("Customer or business name", "  Harbor Floral revised  ")
                    .type("Customer contact person", "  Maya Rivera  ")
                    .type("Customer phone", "  360-555-0191 ext 02  ")
                    .type("Customer email", "maya@example.test")
                    .select("Customer type", "Florist")
                    .type("Customer notes", "  Keep these exact delivery details  ");
        }

        private void assertRawCustomerDraft() {
            assertThat(user.fieldValue("Customer or business name")).isEqualTo("  Harbor Floral revised  ");
            assertThat(user.fieldValue("Customer contact person")).isEqualTo("  Maya Rivera  ");
            assertThat(user.fieldValue("Customer phone")).isEqualTo("  360-555-0191 ext 02  ");
            assertThat(user.fieldValue("Customer email")).isEqualTo("maya@example.test");
            assertThat(user.selectedOption("Customer type")).isEqualTo("Florist");
            assertThat(user.fieldValue("Customer notes")).isEqualTo("  Keep these exact delivery details  ");
        }
    }

    private static FlowerFarmGUI gui(GuiLoginGate gate, DesktopSession session,
                                     FarmDialogs dialogs, FarmFileChooser fileChooser) {
        return gui(gate, session, mock(ConnectorRegistry.class), dialogs, fileChooser);
    }

    private static FlowerFarmGUI gui(GuiLoginGate gate, DesktopSession session,
                                     ConnectorRegistry connectorRegistry,
                                     FarmDialogs dialogs, FarmFileChooser fileChooser) {
        return new FlowerFarmGUI(
                mock(InventoryService.class), mock(TrendService.class),
                connectorRegistry, mock(HarvestService.class),
                mock(SyncHistoryService.class), mock(CustomerService.class),
                mock(OrderService.class), mock(ReportService.class),
                mock(IrrigationAdvisorService.class), mock(MarketDayPackingService.class),
                mock(MorningBriefingService.class), mock(DayCloseoutService.class),
                mock(CustomerStatementService.class), mock(KnowledgeService.class),
                mock(SeasonPlanService.class), mock(com.flowerfarm.service.ProductionStudioService.class),
                gate, session, new FarmProfile(),
                new com.flowerfarm.gui.automation.SwingHarnessBridge(),
                dialogs, fileChooser);
    }

    private static JMenuItem menuItem(JMenuBar menuBar, String text) {
        for (int menuIndex = 0; menuIndex < menuBar.getMenuCount(); menuIndex++) {
            JMenu menu = menuBar.getMenu(menuIndex);
            for (int itemIndex = 0; itemIndex < menu.getItemCount(); itemIndex++) {
                JMenuItem item = menu.getItem(itemIndex);
                if (item != null && text.equals(item.getText())) {
                    return item;
                }
            }
        }
        throw new AssertionError("Menu item not found: " + text);
    }

    private static List<JLabel> labels(Component root) {
        List<JLabel> result = new ArrayList<>();
        collectLabels(root, result);
        return result;
    }

    private static void collectLabels(Component root, List<JLabel> result) {
        if (root instanceof JLabel label) {
            result.add(label);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectLabels(child, result);
            }
        }
    }

    private static Component named(Component root, String name) {
        if (name.equals(root.getName())) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = named(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JButton button(Component root, String text) {
        return descendants(root, JButton.class).stream()
                .filter(candidate -> text.equals(candidate.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Button not found: " + text));
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        visit(root, component -> {
            if (type.isInstance(component)) {
                matches.add(type.cast(component));
            }
        });
        return matches;
    }

    private static void visit(Component component,
                              java.util.function.Consumer<Component> consumer) {
        consumer.accept(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                visit(child, consumer);
            }
        }
    }
}
