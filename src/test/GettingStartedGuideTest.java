package com.flowerfarm.gui;

import com.flowerfarm.auth.DesktopSession;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.gui.harness.SwingMenuHarness;
import com.flowerfarm.model.FarmProfile;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GettingStartedGuideTest {

    @Test
    @DisplayName("the guide is bundled in the app and the printable docs copy is identical")
    void bundledGuideMatchesDocsCopy() throws IOException {
        String bundled = GettingStartedGuide.markdown();
        String docs = Files.readString(Path.of("docs", "GETTING_STARTED.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertThat(bundled).startsWith("# Getting started with FlowersForever");
        assertThat(docs).isEqualTo(bundled);
    }

    @Test
    @DisplayName("the guide stays one page and covers install, first steps, backups, updates, and help")
    void guideIsOnePageAndCoversTheBasics() {
        String guide = GettingStartedGuide.markdown();
        int words = guide.strip().split("\\s+").length;

        assertThat(words).as("one printed page").isBetween(200, 450);
        assertThat(guide).contains("## 1. Install", "## 2. Choose your farm", "## 3. Your first ten minutes",
                "## 4. A normal day", "## 5. Keep your records safe", "## 6. Updates and help");
        assertThat(guide).contains("More info", "Run anyway", "not code-signed");
        assertThat(guide).doesNotContainIgnoringCase("lorem").doesNotContain("TODO");
    }

    @Test
    @DisplayName("the renderer turns the guide's Markdown into escaped, structured HTML")
    void rendererHandlesTheMarkdownSubset() {
        String html = GettingStartedGuide.toHtml("""
                # Title & more
                Intro line one
                continues here with **bold** and <tags>.

                ## Steps
                1. First **File → Back up now…**
                2. Second
                - Bullet a
                - Bullet b
                Closing words
                """);

        assertThat(html).startsWith("<html><body").endsWith("</body></html>");
        assertThat(html).contains("<h1>Title &amp; more</h1>");
        assertThat(html).contains("<p>Intro line one continues here with <b>bold</b> and &lt;tags&gt;.</p>");
        assertThat(html).contains("<h2>Steps</h2>");
        assertThat(html).contains("<ol><li>First <b>File → Back up now…</b></li><li>Second</li></ol>");
        assertThat(html).contains("<ul><li>Bullet a</li><li>Bullet b</li></ul>");
        assertThat(html).contains("<p>Closing words</p>");
        assertThat(html).doesNotContain("**").doesNotContain("<tags>");
    }

    @Test
    @DisplayName("menu paths are read from bold arrow references only")
    void menuPathsAreParsed() {
        List<GettingStartedGuide.MenuPath> paths = GettingStartedGuide.menuPaths(
                "Use **File → Back up now…** then **Help → Check for updates…**; **Run anyway** is not a path.");

        assertThat(paths).containsExactly(
                new GettingStartedGuide.MenuPath("File", "Back up now…"),
                new GettingStartedGuide.MenuPath("Help", "Check for updates…"));
        assertThat(paths.get(0)).hasToString("File → Back up now…");
    }

    @Test
    @DisplayName("every menu path the guide names exists in the real menu bar")
    void everyMenuPathInTheGuideExists() {
        JMenuBar bar = gui(mock(FarmDialogs.class)).buildMenuBar();
        List<GettingStartedGuide.MenuPath> paths = GettingStartedGuide.menuPaths(GettingStartedGuide.markdown());

        assertThat(paths).hasSizeGreaterThanOrEqualTo(10);
        List<String> available = menuPaths(bar);
        assertThat(paths).allSatisfy(path ->
                assertThat(available).as("menu path named in the guide").contains(path.toString()));
    }

    @Test
    @DisplayName("Help opens the guide first, in a read-only Close-only dialog with no window")
    void helpMenuOpensTheGuide() {
        FarmDialogs dialogs = mock(FarmDialogs.class);
        when(dialogs.showOption(any(), any(), any(), anyInt(), anyInt(), any(), any(), any()))
                .thenReturn(0);
        JMenuBar bar = gui(dialogs).buildMenuBar();
        JMenu help = menu(bar, "Help");

        JMenuItem first = help.getItem(0);
        assertThat(first.getText()).isEqualTo(GettingStartedGuide.MENU_LABEL);
        assertThat(first.getMnemonic()).isNotZero();
        first.doClick();

        ArgumentCaptor<Object> message = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object[]> options = ArgumentCaptor.forClass(Object[].class);
        verify(dialogs).showOption(any(), message.capture(), eq(GettingStartedGuide.TITLE),
                eq(JOptionPane.DEFAULT_OPTION), eq(JOptionPane.PLAIN_MESSAGE), isNull(Icon.class),
                options.capture(), eq("Close"));
        assertThat(options.getValue()).containsExactly("Close");
        assertThat(message.getValue()).isInstanceOf(JScrollPane.class);
        JEditorPane page = (JEditorPane) ((JScrollPane) message.getValue()).getViewport().getView();
        assertThat(page.isEditable()).isFalse();
        assertThat(page.getContentType()).isEqualTo("text/html");
        assertThat(page.getAccessibleContext().getAccessibleName()).isEqualTo(GettingStartedGuide.TITLE);
        assertThat(page.getText()).contains("Getting started with FlowersForever", "Keep your records safe");
    }

    @Test
    @DisplayName("the human menu harness can open and close the guide")
    void menuHarnessOpensAndClosesTheGuide() {
        SwingDialogHarness dialogs = new SwingDialogHarness().choose(GettingStartedGuide.TITLE, "Close");
        SwingMenuHarness.on(gui(dialogs).buildMenuBar()).click(GettingStartedGuide.MENU_LABEL);

        dialogs.assertComplete();
        assertThat(dialogs.transcript()).containsExactly("OPTION " + GettingStartedGuide.TITLE + " = Close");
    }

    private static JMenu menu(JMenuBar bar, String label) {
        for (int index = 0; index < bar.getMenuCount(); index++) {
            JMenu candidate = bar.getMenu(index);
            if (candidate != null && label.equals(candidate.getText())) {
                return candidate;
            }
        }
        throw new AssertionError("no menu " + label);
    }

    private static List<String> menuPaths(JMenuBar bar) {
        List<String> paths = new ArrayList<>();
        for (int index = 0; index < bar.getMenuCount(); index++) {
            JMenu menu = bar.getMenu(index);
            if (menu != null) {
                collect(menu.getText(), menu, paths);
            }
        }
        return paths;
    }

    private static void collect(String prefix, JMenu menu, List<String> paths) {
        for (int index = 0; index < menu.getItemCount(); index++) {
            JMenuItem item = menu.getItem(index);
            if (item == null) {
                continue;
            }
            paths.add(prefix + " → " + item.getText());
            if (item instanceof JMenu submenu) {
                collect(prefix + " → " + submenu.getText(), submenu, paths);
            }
        }
    }

    private static FlowerFarmGUI gui(FarmDialogs dialogs) {
        GuiLoginGate gate = mock(GuiLoginGate.class);
        when(gate.isEnabled()).thenReturn(false);
        return new FlowerFarmGUI(
                mock(InventoryService.class), mock(TrendService.class),
                mock(ConnectorRegistry.class), mock(HarvestService.class),
                mock(SyncHistoryService.class), mock(CustomerService.class),
                mock(OrderService.class), mock(ReportService.class),
                mock(IrrigationAdvisorService.class), mock(MarketDayPackingService.class),
                mock(MorningBriefingService.class), mock(DayCloseoutService.class),
                mock(CustomerStatementService.class), mock(KnowledgeService.class),
                mock(SeasonPlanService.class), mock(com.flowerfarm.service.ProductionStudioService.class),
                gate, new DesktopSession(), new FarmProfile(),
                new com.flowerfarm.gui.automation.SwingHarnessBridge(),
                dialogs, mock(FarmFileChooser.class));
    }
}
