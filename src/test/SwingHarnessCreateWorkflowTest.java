package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.tabs.AddItemTab;
import com.flowerfarm.gui.tabs.TabHost;
import com.flowerfarm.model.FarmProfile;
import com.flowerfarm.model.Item;
import com.flowerfarm.repository.InventoryRepository;
import com.flowerfarm.service.InventoryCsvExporter;
import com.flowerfarm.service.InventoryReportGenerator;
import com.flowerfarm.service.InventoryService;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwingHarnessCreateWorkflowTest {
    private static final String TOKEN = "desktop-create-workflow-token-123456";

    @Test
    void addsCustomInventoryThroughTheRealFormServiceAndGatewayAndReplaysOnlyOnce() throws Exception {
        InventoryRepository repository = mock(InventoryRepository.class);
        AtomicReference<Item> stored = new AtomicReference<>();
        when(repository.findAllByNameIgnoreCaseForUpdate(anyString())).thenReturn(List.of());
        when(repository.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            item.setId(71L);
            stored.set(item);
            return item;
        });
        when(repository.findById(71L)).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        InventoryService service = new InventoryService(repository, "unused-seed.csv",
                mock(InventoryReportGenerator.class), mock(InventoryCsvExporter.class), mock(FarmProfile.class));
        TabHost host = mock(TabHost.class);
        when(host.canMutateData()).thenReturn(true);
        CountDownLatch refreshed = new CountDownLatch(1);
        doAnswer(invocation -> { refreshed.countDown(); return null; }).when(host).refreshAll();
        AtomicReference<JComponent> content = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> content.set(
                new AddItemTab(service, host, mock(FarmDialogs.class)).getUIComponent()));
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(content.get(), ignored -> true);
        HarnessProperties properties = properties();
        HarnessGateway gateway = new HarnessGateway(
                new HarnessRegistry(List.of(new SwingHarnessPlugin(bridge))), properties);

        execute(gateway, "name", "replace-text", "Item name", "Community bouquet workshop");
        execute(gateway, "category", "choose-option", "Item category", "Farm workshops");
        execute(gateway, "unit", "choose-option", "Selling unit", "Per workshop place");
        execute(gateway, "price", "replace-text", "Selling price", "45.00");
        execute(gateway, "cost", "replace-text", "Cost per unit", "12.50");
        execute(gateway, "quantity", "replace-text", "Inventory quantity", "8");
        execute(gateway, "notes", "replace-text", "Item notes", "Hands-on flowers · September");

        var category = gateway.execute(TOKEN, SwingHarnessPlugin.ID, new HarnessCommand(
                "read-category", "read-control", Map.of("selector", "Item category")));
        assertThat(category.output()).containsEntry("editable", true).containsEntry("selected", "Farm workshops");
        assertThat(gateway.observe(TOKEN, SwingHarnessPlugin.ID).toString()).doesNotContain("Farm workshops");

        HarnessCommand save = new HarnessCommand("save-custom-item", "activate-button",
                Map.of("selector", "Add item to inventory"));
        assertThat(gateway.execute(TOKEN, SwingHarnessPlugin.ID, save).success()).isTrue();
        assertThat(refreshed.await(3, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> { });
        assertThat(service.findById(71L)).get().extracting(Item::getName, Item::getCategory, Item::getUnit,
                Item::getPrice, Item::getCost, Item::getQuantity, Item::getNotes)
                .containsExactly("Community bouquet workshop", "Farm workshops", "Per workshop place",
                        45.0, 12.5, 8, "Hands-on flowers · September");
        assertThat(gateway.execute(TOKEN, SwingHarnessPlugin.ID, save).replayed()).isTrue();
        verify(repository, times(1)).save(any(Item.class));

        var closedChoice = gateway.execute(TOKEN, SwingHarnessPlugin.ID, new HarnessCommand(
                "closed-choice", "choose-option",
                Map.of("selector", "Flower product starter library", "value", "Unlisted starter")));
        assertThat(closedChoice.output()).containsEntry("status", "FAILED");
        properties.setAllowDrafts(false);
        assertThatThrownBy(() -> execute(gateway, "blocked", "choose-option", "Item category", "Blocked category"))
                .isInstanceOf(HarnessPolicyException.class);
    }

    private static void execute(HarnessGateway gateway, String id, String action, String selector, String value) {
        assertThat(gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                new HarnessCommand(id, action, Map.of("selector", selector, "value", value))).success()).isTrue();
    }

    private static HarnessProperties properties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowDrafts(true);
        properties.setAllowSensitiveRead(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        return properties;
    }
}
