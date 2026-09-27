package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessControl;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessSnapshot;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingSelectorLifecycleTest {
    private static final String TOKEN = "selector-lifecycle-token-123456789";

    @Test
    void namedButtonKeepsItsAdvertisedSelectorAsItsVisibleCountChanges() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton button = onEdt(() -> button("inventory.reviewDrafts", "Review drafts (1)", clicks));
        Fixture fixture = fixture(button);

        assertControl(fixture, "inventory.reviewDrafts", "Review drafts (1)", true, true);
        onEdt(() -> { button.setText("Review drafts (3)"); return null; });
        assertControl(fixture, "inventory.reviewDrafts", "Review drafts (3)", true, true);

        fixture.completed("stable-name", "activate-button", Map.of("selector", "inventory.reviewDrafts"));
        fixture.completed("legacy-label", "activate-button", Map.of("selector", "Review drafts (3)"));
        fixture.failed("old-label", "activate-button", Map.of("selector", "Review drafts (1)"),
                "IllegalArgumentException");
        assertThat(clicks).hasValue(2);
    }

    @Test
    void namedFieldKeepsItsSelectorAcrossAccessibleLabelChangesWithoutPublishingItsValue() throws Exception {
        JTextField field = onEdt(() -> field("customer.contact", "Contact name", "private draft 4a91"));
        Fixture fixture = fixture(field);
        assertControl(fixture, "customer.contact", "Contact name", true, true);

        onEdt(() -> { field.getAccessibleContext().setAccessibleName("Primary contact"); return null; });
        assertControl(fixture, "customer.contact", "Primary contact", true, true);
        assertThat(fixture.observe().toString()).doesNotContain("private draft 4a91");
        fixture.completed("edit-by-name", "replace-text",
                Map.of("selector", "customer.contact", "value", "Morgan"));
        fixture.completed("edit-by-label", "replace-text",
                Map.of("selector", "Primary contact", "value", "Sam"));
        assertThat(SwingSelectorLifecycleTest.<String>onEdt(field::getText)).isEqualTo("Sam");
        fixture.failed("old-field-label", "replace-text",
                Map.of("selector", "Contact name", "value", "Wrong"), "IllegalArgumentException");
        assertThat(SwingSelectorLifecycleTest.<String>onEdt(field::getText)).isEqualTo("Sam");
    }

    @Test
    void namedTableKeepsItsSelectorWhenTheModelAndCountLabelChange() throws Exception {
        JTable table = onEdt(() -> {
            JTable result = new JTable(new DefaultTableModel(new Object[][]{{"Dahlia"}}, new Object[]{"Crop"}));
            result.setName("harvest.rows");
            result.getAccessibleContext().setAccessibleName("Harvest rows (1)");
            return result;
        });
        Fixture fixture = fixture(table);
        assertControl(fixture, "harvest.rows", "Harvest rows (1)", true, true);
        onEdt(() -> {
            ((DefaultTableModel) table.getModel()).addRow(new Object[]{"Rose"});
            table.getAccessibleContext().setAccessibleName("Harvest rows (2)");
            return null;
        });

        assertControl(fixture, "harvest.rows", "Harvest rows (2)", true, true);
        fixture.completed("select-second", "select-row", Map.of("table", "harvest.rows", "row", 1));
        assertThat(onEdt(table::getSelectedRow)).isEqualTo(1);
        assertThat(fixture.bridge().readControl("harvest.rows").get("rows"))
                .isEqualTo(List.of(List.of("Dahlia"), List.of("Rose")));
    }

    @Test
    void unnamedButtonFallsBackToItsCurrentHumanLabel() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton button = onEdt(() -> button(null, "Pack 1 order", clicks));
        Fixture fixture = fixture(button);
        assertControl(fixture, "Pack 1 order", "Pack 1 order", true, true);
        onEdt(() -> { button.setText("Pack 2 orders"); return null; });
        assertControl(fixture, "Pack 2 orders", "Pack 2 orders", true, true);
        fixture.failed("expired-label", "activate-button", Map.of("selector", "Pack 1 order"),
                "IllegalArgumentException");
        fixture.completed("current-label", "activate-button", Map.of("selector", "Pack 2 orders"));
        assertThat(clicks).hasValue(1);
    }

    @Test
    void unnamedFieldRemainsOperableByItsAccessibleLabel() throws Exception {
        JTextField field = onEdt(() -> field(null, "Harvest notes", ""));
        Fixture fixture = fixture(field);
        assertControl(fixture, "Harvest notes", "Harvest notes", true, true);
        fixture.completed("unnamed-field", "replace-text",
                Map.of("selector", "Harvest notes", "value", "First cut"));
        assertThat(SwingSelectorLifecycleTest.<String>onEdt(field::getText)).isEqualTo("First cut");
    }

    @Test
    void duplicateExplicitNamesFallBackToUniqueLabelsAndRejectTheSharedName() throws Exception {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        Fixture fixture = fixture(onEdt(() -> panel(
                button("shared.save", "Save customer", first),
                button("shared.save", "Save order", second))));

        assertControl(fixture, "Save customer", "Save customer", true, true);
        assertControl(fixture, "Save order", "Save order", true, true);
        fixture.failed("ambiguous-name", "activate-button", Map.of("selector", "shared.save"),
                "IllegalArgumentException");
        fixture.completed("first-label", "activate-button", Map.of("selector", "Save customer"));
        fixture.completed("second-label", "activate-button", Map.of("selector", "Save order"));
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(1);
    }

    @Test
    void duplicateNamesAndLabelsAreNeverAdvertisedAsCommandable() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        Fixture fixture = fixture(onEdt(() -> panel(
                button("shared.save", "Save", clicks), button("shared.save", "Save", clicks))));

        assertThat(fixture.observe().controls()).hasSize(2).allMatch(control -> !control.commandable());
        fixture.failed("duplicate-name", "activate-button", Map.of("selector", "shared.save"),
                "IllegalArgumentException");
        fixture.failed("duplicate-label", "activate-button", Map.of("selector", "Save"),
                "IllegalArgumentException");
        assertThat(clicks).hasValue(0);
    }

    @Test
    void explicitNameCannotShadowAnotherButtonsVisibleText() throws Exception {
        AtomicInteger original = new AtomicInteger();
        AtomicInteger other = new AtomicInteger();
        Fixture fixture = fixture(onEdt(() -> {
            JButton originalButton = button("orders.save", "Save order", original);
            JButton otherButton = button("other.action", "orders.save", other);
            otherButton.getAccessibleContext().setAccessibleName("Other action");
            return panel(originalButton, otherButton);
        }));

        assertControl(fixture, "Save order", "Save order", true, true);
        assertControl(fixture, "other.action", "orders.save", true, true);
        fixture.failed("text-shadow", "activate-button", Map.of("selector", "orders.save"),
                "IllegalArgumentException");
        fixture.completed("original-human-label", "activate-button", Map.of("selector", "Save order"));
        assertThat(original).hasValue(1);
        assertThat(other).hasValue(0);
    }

    @Test
    void aNameAndAccessibleLabelCollisionRejectsActionsAcrossDifferentControlTypes() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JTextField field = onEdt(() -> field("customer.notes", "shared.action", "Keep this draft"));
        Fixture fixture = fixture(onEdt(() -> panel(button("shared.action", "Save order", clicks), field)));

        assertControl(fixture, "Save order", "Save order", true, true);
        assertControl(fixture, "customer.notes", "shared.action", true, true);
        fixture.failed("cross-type-button", "activate-button", Map.of("selector", "shared.action"),
                "IllegalArgumentException");
        fixture.failed("cross-type-field", "replace-text",
                Map.of("selector", "shared.action", "value", "Wrong"), "IllegalArgumentException");
        assertThatThrownBy(() -> fixture.bridge().readControl("shared.action"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("found 2");
        assertThat(clicks).hasValue(0);
        assertThat(SwingSelectorLifecycleTest.<String>onEdt(field::getText)).isEqualTo("Keep this draft");
    }

    @Test
    void aNonInteractiveNamesakeCannotBeBypassedByChoosingAnActionType() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        Fixture fixture = fixture(onEdt(() -> {
            JLabel label = new JLabel("Saved order status");
            label.setName("orders.save");
            return panel(button("orders.save", "Save order", clicks), label);
        }));

        assertControl(fixture, "Save order", "Save order", true, true);
        fixture.failed("label-shadow", "activate-button", Map.of("selector", "orders.save"),
                "IllegalArgumentException");
        assertThat(clicks).hasValue(0);
    }

    @Test
    void removingACollisionRestoresTheExplicitSelectorOnTheNextObservation() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton action = onEdt(() -> button("orders.save", "Save order", clicks));
        JLabel collision = onEdt(() -> { JLabel label = new JLabel("Other"); label.setName("orders.save"); return label; });
        JPanel root = onEdt(() -> panel(action, collision));
        Fixture fixture = fixture(root);
        assertControl(fixture, "Save order", "Save order", true, true);

        onEdt(() -> { root.remove(collision); return null; });
        assertControl(fixture, "orders.save", "Save order", true, true);
        fixture.completed("resolved-collision", "activate-button", Map.of("selector", "orders.save"));
        assertThat(clicks).hasValue(1);
    }

    @Test
    void rebuildingTheSameNameRequiresFreshContextAndNeverReplaysTheRejectedAction() throws Exception {
        AtomicInteger oldClicks = new AtomicInteger();
        AtomicInteger newClicks = new AtomicInteger();
        JPanel root = onEdt(() -> panel(button("orders.save", "Save order", oldClicks)));
        Fixture fixture = fixture(root);
        String oldContext = fixture.context();
        onEdt(() -> {
            root.removeAll();
            root.add(button("orders.save", "Save rebuilt order", newClicks));
            return null;
        });
        Map<String, Object> stale = Map.of("selector", "orders.save", "expected-context", oldContext);
        fixture.failed("old-tree", "activate-button", stale, "DesktopContextChangedException");
        assertControl(fixture, "orders.save", "Save rebuilt order", true, true);
        String freshContext = fixture.context();
        assertThat(freshContext).isNotEqualTo(oldContext);
        assertThat(fixture.execute("old-tree", "activate-button", stale).replayed()).isTrue();
        assertThat(oldClicks).hasValue(0);
        assertThat(newClicks).hasValue(0);

        fixture.completed("new-tree", "activate-button",
                Map.of("selector", "orders.save", "expected-context", freshContext));
        assertThat(oldClicks).hasValue(0);
        assertThat(newClicks).hasValue(1);
    }

    @Test
    void removingAControlDoesNotLeaveItsAdvertisedSelectorAttachedToAnOldInstance() throws Exception {
        AtomicInteger removedClicks = new AtomicInteger();
        AtomicInteger remainingClicks = new AtomicInteger();
        JButton removed = onEdt(() -> button("orders.save", "Save order", removedClicks));
        JPanel root = onEdt(() -> panel(removed, button("orders.refresh", "Refresh orders", remainingClicks)));
        Fixture fixture = fixture(root);
        assertControl(fixture, "orders.save", "Save order", true, true);
        onEdt(() -> { root.remove(removed); return null; });

        assertThat(fixture.observe().controls()).extracting(HarnessControl::selector).containsExactly("orders.refresh");
        fixture.failed("removed-name", "activate-button", Map.of("selector", "orders.save"),
                "IllegalArgumentException");
        fixture.failed("removed-label", "activate-button", Map.of("selector", "Save order"),
                "IllegalArgumentException");
        assertThat(removedClicks).hasValue(0);
        assertThat(remainingClicks).hasValue(0);
    }

    @Test
    void disablingANamedControlKeepsItsSelectorButPreventsDispatch() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton button = onEdt(() -> button("orders.save", "Save order", clicks));
        Fixture fixture = fixture(button);
        onEdt(() -> { button.setEnabled(false); button.setText("Saving order…"); return null; });
        assertControl(fixture, "orders.save", "Saving order…", false, true);
        fixture.failed("disabled-name", "activate-button", Map.of("selector", "orders.save"),
                "IllegalArgumentException");
        assertThat(clicks).hasValue(0);
        onEdt(() -> { button.setEnabled(true); button.setText("Save order"); return null; });
        fixture.completed("enabled-name", "activate-button", Map.of("selector", "orders.save"));
        assertThat(clicks).hasValue(1);
    }

    @Test
    void aStableSelectorDoesNotMakeAContextFromBeforeALabelChangeCurrent() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton button = onEdt(() -> button("market.complete", "Complete 1 order", clicks));
        Fixture fixture = fixture(button);
        String before = fixture.context();
        onEdt(() -> { button.setText("Complete 4 orders"); return null; });
        fixture.failed("old-count", "activate-button",
                Map.of("selector", "market.complete", "expected-context", before),
                "DesktopContextChangedException");
        assertThat(clicks).hasValue(0);
        assertControl(fixture, "market.complete", "Complete 4 orders", true, true);
        fixture.completed("reviewed-count", "activate-button",
                Map.of("selector", "market.complete", "expected-context", fixture.context()));
        assertThat(clicks).hasValue(1);
    }

    @Test
    void hiddenNamedControlsCannotBeReachedThroughTheirPreviouslyAdvertisedSelector() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        JButton button = onEdt(() -> button("orders.save", "Save order", clicks));
        JPanel root = onEdt(() -> panel(button));
        Fixture fixture = fixture(root);
        assertControl(fixture, "orders.save", "Save order", true, true);
        onEdt(() -> { button.setVisible(false); return null; });
        assertThat(fixture.observe().controls()).isEmpty();
        fixture.failed("hidden-name", "activate-button", Map.of("selector", "orders.save"),
                "IllegalArgumentException");
        assertThat(clicks).hasValue(0);
    }

    @Test
    void advertisedNamesDoNotBypassGatewayPermissions() throws Exception {
        AtomicInteger clicks = new AtomicInteger();
        Fixture fixture = fixture(onEdt(() -> button("orders.save", "Save order", clicks)));
        assertControl(fixture, "orders.save", "Save order", true, true);
        fixture.properties().setAllowDestructive(false);
        assertThatThrownBy(() -> fixture.execute("permission-blocked", "activate-button",
                Map.of("selector", "orders.save"))).isInstanceOf(HarnessPolicyException.class);
        onEdt(() -> null);
        assertThat(clicks).hasValue(0);
    }

    private static void assertControl(Fixture fixture, String selector, String label,
                                      boolean enabled, boolean commandable) {
        assertThat(fixture.observe().controls()).filteredOn(control -> control.selector().equals(selector))
                .singleElement().satisfies(control -> {
                    assertThat(control.label()).isEqualTo(label);
                    assertThat(control.enabled()).isEqualTo(enabled);
                    assertThat(control.commandable()).isEqualTo(commandable);
                });
    }

    private static JButton button(String name, String text, AtomicInteger clicks) {
        JButton button = new JButton(text);
        button.setName(name);
        button.addActionListener(event -> clicks.incrementAndGet());
        return button;
    }

    private static JTextField field(String name, String label, String text) {
        JTextField field = new JTextField(text);
        field.setName(name);
        field.getAccessibleContext().setAccessibleName(label);
        return field;
    }

    private static JPanel panel(JComponent... children) {
        JPanel panel = new JPanel();
        for (JComponent child : children) panel.add(child);
        return panel;
    }

    private static Fixture fixture(JComponent root) throws Exception {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        onEdt(() -> { bridge.attach(root, ignored -> false); return null; });
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowSensitiveRead(true);
        properties.setAllowDrafts(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        HarnessGateway gateway = new HarnessGateway(
                new HarnessRegistry(List.of(new SwingHarnessPlugin(bridge))), properties);
        return new Fixture(bridge, gateway, properties);
    }

    private static <T> T onEdt(Callable<T> operation) throws Exception {
        FutureTask<T> task = new FutureTask<>(operation);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private record Fixture(SwingHarnessBridge bridge, HarnessGateway gateway, HarnessProperties properties) {
        HarnessSnapshot observe() {
            return gateway.observe(TOKEN, SwingHarnessPlugin.ID);
        }

        String context() {
            return (String) observe().metadata().get("contextToken");
        }

        HarnessResponse execute(String id, String action, Map<String, Object> arguments) throws Exception {
            HarnessResponse response = gateway.execute(TOKEN, SwingHarnessPlugin.ID,
                    new HarnessCommand(id, action, arguments));
            onEdt(() -> null);
            return response;
        }

        void completed(String id, String action, Map<String, Object> arguments) throws Exception {
            execute(id, action, arguments);
            assertThat(bridge.actionStatus(id).output()).containsEntry("status", "COMPLETED")
                    .containsEntry("completed", true);
        }

        void failed(String id, String action, Map<String, Object> arguments, String failure) throws Exception {
            execute(id, action, arguments);
            assertThat(bridge.actionStatus(id).output()).containsEntry("status", "FAILED")
                    .containsEntry("completed", true).containsEntry("failureType", failure);
        }
    }
}
