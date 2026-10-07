package com.flowerfarm.gui.tabs;

import com.flowerfarm.model.FarmWorkspace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Workspace home navigation")
class WorkspaceHomeTabTest {

    @Test
    void routesOnlyVisibleDestinationsAndKeepsTheirOrder() throws Exception {
        RecordingHost host = new RecordingHost();
        WorkspaceHomeTab tab = new WorkspaceHomeTab(FarmWorkspace.WILDFLOWER_FARM,
                List.of("Harvest Log", "Inventory", "Inventory", "Workspace", "Knowledge Log"),
                host);
        JComponent root = onEdt(tab::getUIComponent);
        List<JButton> buttons = workflowButtons(root);

        assertThat(tab.getTabTitle()).isEqualTo("Workspace");
        assertThat(buttons).extracting(JButton::getText)
                .containsExactly("Harvest Log", "Inventory", "Knowledge Log", "Field science");
        assertThat(buttons).extracting(button -> button.getAccessibleContext().getAccessibleName())
                .containsExactly("Open Harvest Log", "Open Inventory", "Open Knowledge Log",
                        "Open Field science");
        onEdt(() -> buttons.get(1).doClick());
        assertThat(host.selected).containsExactly("Inventory");
    }

    @Test
    void headingsAndScopeMatchEachWorkspaceWithoutSpecializedClaims() throws Exception {
        assertHome(FarmWorkspace.ROSE_AND_EVENTS, "Rose & event farm");
        assertHome(FarmWorkspace.WILDFLOWER_FARM, "Wildflower farm");
        assertHome(FarmWorkspace.FLOWER_SHOP, "Flower shop");
        assertHome(FarmWorkspace.CANNABIS_FARM, "Cannabis farm");
        assertHome(FarmWorkspace.POTATO_FARM, "Potato farm");
        assertHome(FarmWorkspace.APPLE_ORCHARD, "Apple orchard");
        assertHome(FarmWorkspace.BERRY_FARM, "Berry farm");
        assertHome(FarmWorkspace.NURSERY_FARM, "Nursery farm");
        assertHome(FarmWorkspace.LIVESTOCK_FARM, "Livestock farm");
        assertHome(FarmWorkspace.POULTRY_FARM, "Poultry & eggs");
        assertHome(FarmWorkspace.DAIRY_FARM, "Dairy farm");
        assertHome(FarmWorkspace.VINEYARD_FARM, "Vineyard farm");
        assertHome(FarmWorkspace.HAY_FARM, "Hay & forage");
        assertHome(FarmWorkspace.HOPS_FARM, "Hops farm");
        assertHome(FarmWorkspace.GRAIN_FARM, "Grains farm");
        assertHome(FarmWorkspace.SEED_FARM, "Seed farm");
        assertHome(FarmWorkspace.NUT_ORCHARD, "Tree nut orchard");
        assertHome(FarmWorkspace.VEGETABLE_FARM, "Vegetable farm");
        assertHome(FarmWorkspace.AQUACULTURE_FARM, "Aquaculture farm");
        assertHome(FarmWorkspace.CHRISTMAS_TREE_FARM, "Christmas tree farm");
        assertHome(FarmWorkspace.APIARY_FARM, "Apiary farm");
        assertHome(FarmWorkspace.MUSHROOM_FARM, "Mushroom farm");
        assertHome(FarmWorkspace.CRANBERRY_FARM, "Cranberry farm");
        assertHome(FarmWorkspace.PUMPKIN_PATCH, "Pumpkin patch");
        assertHome(FarmWorkspace.CUSTOM_FARM, "Custom farm");

        WorkspaceHomeTab cannabis = new WorkspaceHomeTab(FarmWorkspace.CANNABIS_FARM,
                List.of("Inventory"), new RecordingHost());
        JTextArea scope = descendants(onEdt(cannabis::getUIComponent), JTextArea.class)
                .get(0);
        assertThat(scope.getText()).contains("regulated tracking")
                .contains("state reporting");

        WorkspaceHomeTab potato = new WorkspaceHomeTab(FarmWorkspace.POTATO_FARM,
                List.of("Field plans"), new RecordingHost());
        assertThat(descendants(onEdt(potato::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("do not grade potatoes", "packout lots");

        WorkspaceHomeTab apple = new WorkspaceHomeTab(FarmWorkspace.APPLE_ORCHARD,
                List.of("Orchard blocks"), new RecordingHost());
        assertThat(descendants(onEdt(apple::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("cold storage", "packout");

        WorkspaceHomeTab berry = new WorkspaceHomeTab(FarmWorkspace.BERRY_FARM,
                List.of("Berry blocks"), new RecordingHost());
        assertThat(descendants(onEdt(berry::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("harvest lots or stock", "U-pick bookings");

        WorkspaceHomeTab nursery = new WorkspaceHomeTab(FarmWorkspace.NURSERY_FARM,
                List.of("Growing batches"), new RecordingHost());
        assertThat(descendants(onEdt(nursery::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("do not create stock", "nursery licensing");

        WorkspaceHomeTab livestock = new WorkspaceHomeTab(FarmWorkspace.LIVESTOCK_FARM,
                List.of("Herd groups"), new RecordingHost());
        assertThat(descendants(onEdt(livestock::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("individual animals", "traceability");

        WorkspaceHomeTab poultry = new WorkspaceHomeTab(FarmWorkspace.POULTRY_FARM,
                List.of("Egg collections"), new RecordingHost());
        assertThat(descendants(onEdt(poultry::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("do not create stock or orders", "grade eggs");

        WorkspaceHomeTab dairy = new WorkspaceHomeTab(FarmWorkspace.DAIRY_FARM,
                List.of("Milk collections"), new RecordingHost());
        assertThat(descendants(onEdt(dairy::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("processor delivery", "food safety");

        WorkspaceHomeTab vineyard = new WorkspaceHomeTab(FarmWorkspace.VINEYARD_FARM,
                List.of("Vineyard blocks"), new RecordingHost());
        assertThat(descendants(onEdt(vineyard::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("harvest lots", "winery stock", "deliveries");

        WorkspaceHomeTab hay = new WorkspaceHomeTab(FarmWorkspace.HAY_FARM,
                List.of("Hay cuttings"), new RecordingHost());
        assertThat(descendants(onEdt(hay::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("bale stock", "feed rations", "quality tests");

        WorkspaceHomeTab hops = new WorkspaceHomeTab(FarmWorkspace.HOPS_FARM,
                List.of("Hop yards"), new RecordingHost());
        assertThat(descendants(onEdt(hops::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("drying", "pelletizing", "contracts");

        WorkspaceHomeTab grains = new WorkspaceHomeTab(FarmWorkspace.GRAIN_FARM,
                List.of("Grain fields"), new RecordingHost());
        assertThat(descendants(onEdt(grains::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("yields", "grade", "moisture", "storage");

        WorkspaceHomeTab seed = new WorkspaceHomeTab(FarmWorkspace.SEED_FARM,
                List.of("Seed fields"), new RecordingHost());
        assertThat(descendants(onEdt(seed::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("isolation", "certify seed", "seed lots", "stock");

        WorkspaceHomeTab nuts = new WorkspaceHomeTab(FarmWorkspace.NUT_ORCHARD,
                List.of("Nut blocks"), new RecordingHost());
        assertThat(descendants(onEdt(nuts::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("pollinizers", "actual yields", "processing", "stock");

        WorkspaceHomeTab vegetables = new WorkspaceHomeTab(FarmWorkspace.VEGETABLE_FARM,
                List.of("Vegetable plantings"), new RecordingHost());
        assertThat(descendants(onEdt(vegetables::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("entered by the grower", "stock");

        WorkspaceHomeTab aquatic = new WorkspaceHomeTab(FarmWorkspace.AQUACULTURE_FARM,
                List.of("Aquatic cohorts"), new RecordingHost());
        assertThat(descendants(onEdt(aquatic::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("harvest clearance", "licensing", "food safety");

        WorkspaceHomeTab trees = new WorkspaceHomeTab(FarmWorkspace.CHRISTMAS_TREE_FARM,
                List.of("Tree blocks"), new RecordingHost());
        assertThat(descendants(onEdt(trees::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("actual cuts", "bookings", "export clearance");

        WorkspaceHomeTab apiary = new WorkspaceHomeTab(FarmWorkspace.APIARY_FARM,
                List.of("Hive checks"), new RecordingHost());
        assertThat(descendants(onEdt(apiary::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("colony health", "pollination contracts", "honey stock");

        WorkspaceHomeTab mushrooms = new WorkspaceHomeTab(FarmWorkspace.MUSHROOM_FARM,
                List.of("Mushroom batches"), new RecordingHost());
        assertThat(descendants(onEdt(mushrooms::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("fruiting", "actual yield", "stock and sales");

        WorkspaceHomeTab cranberries = new WorkspaceHomeTab(FarmWorkspace.CRANBERRY_FARM,
                List.of("Cranberry beds"), new RecordingHost());
        assertThat(descendants(onEdt(cranberries::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("flooding", "stock and sales");

        WorkspaceHomeTab pumpkins = new WorkspaceHomeTab(FarmWorkspace.PUMPKIN_PATCH,
                List.of("Pumpkin patches"), new RecordingHost());
        assertThat(descendants(onEdt(pumpkins::getUIComponent), JTextArea.class).get(0)
                .getText()).contains("admission", "bookings", "stock and sales");
    }

    @Test
    void emptyWorkspaceHasNoNavigationAction() throws Exception {
        WorkspaceHomeTab tab = new WorkspaceHomeTab(FarmWorkspace.CUSTOM_FARM,
                List.of(), new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(workflowButtons(root)).isEmpty();
        assertThat(descendants(root, JLabel.class))
                .extracting(JLabel::getText)
                .contains("No workflows are available for this workspace yet.");
        assertThatThrownBy(() -> new WorkspaceHomeTab(FarmWorkspace.CUSTOM_FARM,
                java.util.Arrays.asList("Inventory", " "), new RecordingHost()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customWorkspaceShowsSavedFarmFocusWithoutClaimingCropSupport() throws Exception {
        WorkspaceHomeTab tab = new WorkspaceHomeTab(FarmWorkspace.CUSTOM_FARM,
                List.of("Harvest Log", "Inventory"), new RecordingHost(),
                "Potatoes · Honeycrisp apples");

        JTextArea note = descendants(onEdt(tab::getUIComponent), JTextArea.class).stream()
                .filter(area -> "workspace.scopeNote".equals(area.getName()))
                .findFirst().orElseThrow();
        assertThat(note.getText()).contains("Potatoes · Honeycrisp apples")
                .contains("need dedicated workflows");
        onEdt(() -> tab.setCustomFocus("Blueberries"));
        assertThat(note.getText()).contains("Blueberries")
                .doesNotContain("Potatoes");
    }

    @Test
    void combinedHomeShowsTheSelectedTypesAndDirectWorkflowLinks() throws Exception {
        RecordingHost host = new RecordingHost();
        WorkspaceHomeTab tab = new WorkspaceHomeTab(FarmWorkspace.POTATO_FARM,
                List.of("Cultivation", "Field plans", "Orchard blocks", "Inventory"),
                host, new java.util.LinkedHashSet<>(List.of(FarmWorkspace.POTATO_FARM,
                        FarmWorkspace.CANNABIS_FARM, FarmWorkspace.APPLE_ORCHARD)));
        JComponent root = onEdt(tab::getUIComponent);

        assertThat(tab.getDescription()).contains("Your combined farm");
        assertThat(descendants(root, JLabel.class)).extracting(JLabel::getText)
                .contains("Potato farm · Cannabis farm · Apple orchard");
        assertThat(workflowButtons(root)).extracting(JButton::getText)
                .containsExactly("Cultivation", "Field plans", "Orchard blocks",
                        "Inventory", "Field science");
        JTextArea note = descendants(root, JTextArea.class).stream()
                .filter(area -> "workspace.scopeNote".equals(area.getName()))
                .findFirst().orElseThrow();
        assertThat(note.getText()).contains("specialty tools", "shared tools",
                "Cannabis compliance reporting is not included");
    }

    @Test
    void combinedCustomHomeShowsAndRefreshesUserNamedOtherLines() throws Exception {
        WorkspaceHomeTab tab = new WorkspaceHomeTab(FarmWorkspace.POTATO_FARM,
                List.of("Field plans", "Harvest Log", "Inventory",
                        "Customers & Orders"), new RecordingHost(),
                "Lavender · Hemp", new java.util.LinkedHashSet<>(List.of(
                        FarmWorkspace.POTATO_FARM, FarmWorkspace.CUSTOM_FARM)));
        JComponent root = onEdt(tab::getUIComponent);
        JTextArea note = descendants(root, JTextArea.class).stream()
                .filter(area -> "workspace.scopeNote".equals(area.getName()))
                .findFirst().orElseThrow();

        assertThat(note.getText()).contains("Lavender · Hemp",
                "no separate production plan");
        assertThat(workflowButtons(root)).extracting(JButton::getText)
                .contains("Field plans", "Harvest Log", "Inventory");
        onEdt(() -> tab.setCustomFocus("Pears"));
        assertThat(note.getText()).contains("Pears").doesNotContain("Lavender");
    }

    @Test
    void poultryAndLivestockMixDoesNotPromiseAHarvestLog() throws Exception {
        WorkspaceHomeTab tab = new WorkspaceHomeTab(FarmWorkspace.POULTRY_FARM,
                List.of("Egg collections", "Herd groups", "Inventory",
                        "Customers & Orders"), new RecordingHost(),
                java.util.Set.of(FarmWorkspace.POULTRY_FARM,
                        FarmWorkspace.LIVESTOCK_FARM));
        JComponent root = onEdt(tab::getUIComponent);
        JTextArea note = descendants(root, JTextArea.class).stream()
                .filter(area -> "workspace.scopeNote".equals(area.getName()))
                .findFirst().orElseThrow();
        assertThat(note.getText()).contains("collection logs", "Inventory")
                .doesNotContain("Harvest Log", "Record harvests");
    }

    private static void assertHome(FarmWorkspace workspace, String heading) throws Exception {
        WorkspaceHomeTab tab = new WorkspaceHomeTab(workspace, List.of(),
                new RecordingHost());
        JComponent root = onEdt(tab::getUIComponent);
        assertThat(tab.getDescription()).contains(heading);
        assertThat(descendants(root, JLabel.class))
                .filteredOn(label -> "farm.pageTitle".equals(label.getName()))
                .extracting(JLabel::getText)
                .containsExactly(heading);
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                found.addAll(descendants(child, type));
            }
        }
        return found;
    }

    private static List<JButton> workflowButtons(Component root) {
        return descendants(root, JButton.class).stream()
                .filter(button -> button.getName() != null
                        && button.getName().startsWith("workspace.open."))
                .toList();
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> action) throws Exception {
        java.util.concurrent.atomic.AtomicReference<T> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Exception> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception exception) {
                failure.set(exception);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }

    private static void onEdt(Runnable action) throws Exception {
        onEdt(() -> {
            action.run();
            return null;
        });
    }

    private static final class RecordingHost implements TabHost {
        private final List<String> selected = new ArrayList<>();

        @Override public void refreshAll() {
        }

        @Override public void selectTab(String tabTitle) {
            selected.add(tabTitle);
        }

        @Override public void setStatus(String message) {
        }

        @Override public void runTrendAnalysis() {
        }
    }
}
