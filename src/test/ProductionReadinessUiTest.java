package com.flowerfarm.gui;

import com.flowerfarm.auth.DesktopSession;
import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.auth.FarmUser;
import com.flowerfarm.connector.ConnectorRegistry;
import com.flowerfarm.service.CustomerService;
import com.flowerfarm.service.CustomerStatementService;
import com.flowerfarm.service.DayCloseoutService;
import com.flowerfarm.service.HarvestService;
import com.flowerfarm.service.InventoryService;
import com.flowerfarm.service.IrrigationAdvisorService;
import com.flowerfarm.service.MarketDayPackingService;
import com.flowerfarm.service.MorningBriefingService;
import com.flowerfarm.service.OrderService;
import com.flowerfarm.service.ReportService;
import com.flowerfarm.service.SyncHistoryService;
import com.flowerfarm.service.TrendService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Desktop production-readiness access scenarios")
class ProductionReadinessUiTest {

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
        return new FlowerFarmGUI(
                mock(InventoryService.class), mock(TrendService.class),
                mock(ConnectorRegistry.class), mock(HarvestService.class),
                mock(SyncHistoryService.class), mock(CustomerService.class),
                mock(OrderService.class), mock(ReportService.class),
                mock(IrrigationAdvisorService.class), mock(MarketDayPackingService.class),
                mock(MorningBriefingService.class), mock(DayCloseoutService.class),
                mock(CustomerStatementService.class), gate, session);
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
