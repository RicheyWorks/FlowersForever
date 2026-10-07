package com.flowerfarm.gui;

import com.flowerfarm.model.FarmEnterprise;
import com.flowerfarm.model.FarmOperationSettings;
import com.flowerfarm.model.FarmState;
import com.flowerfarm.model.FarmWorkspace;
import com.flowerfarm.model.FlowerOffering;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FarmTypeLauncherTest {

    @Test
    void ownerCanEnterEveryFarmTypeAndCurrentChoiceIsProminent() throws Exception {
        List<FarmWorkspace> entered = new ArrayList<>();
        FarmTypeLauncher[] root = new FarmTypeLauncher[1];
        SwingUtilities.invokeAndWait(() -> {
            root[0] = new FarmTypeLauncher(FarmWorkspace.WILDFLOWER_FARM,
                    true, entered::add, () -> { });
            assertThat(root[0].getAccessibleContext().getAccessibleName())
                    .isEqualTo("Choose your farm type");
            for (FarmWorkspace choice : FarmWorkspace.values()) {
                JButton button = named(root[0], "farmLauncher.enter." + choice.name(),
                        JButton.class);
                assertThat(button.isEnabled()).isTrue();
                assertThat(button.getAccessibleContext().getAccessibleName())
                        .contains(FarmSetupDialog.workspaceLabel(choice));
                button.doClick();
            }
            assertThat(root[0].currentEnterButton().getText()).isEqualTo("Enter farm");
            assertThat(named(root[0], "farmLauncher.description.CUSTOM_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("Other farms", "shared records", "specialized tools");
            assertThat(named(root[0], "farmLauncher.description.POTATO_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("potato fields", "no grading or packout");
            assertThat(named(root[0], "farmLauncher.description.APPLE_ORCHARD",
                    javax.swing.JTextArea.class).getText())
                    .contains("orchard blocks", "no bin or cold-storage ledger");
            assertThat(named(root[0], "farmLauncher.description.BERRY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("berry blocks", "picking windows", "stock stays separate");
            assertThat(named(root[0], "farmLauncher.description.NURSERY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("live plant batches", "propagation", "shared tools");
            assertThat(named(root[0], "farmLauncher.description.LIVESTOCK_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("cattle", "headcount", "No individual animal");
            assertThat(named(root[0], "farmLauncher.description.POULTRY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("egg collections", "Usable count", "orders stay separate");
            assertThat(named(root[0], "farmLauncher.description.DAIRY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("milking sessions", "measured unit", "stock and orders");
            assertThat(named(root[0], "farmLauncher.description.VINEYARD_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("grape blocks", "harvest windows", "destinations");
            assertThat(named(root[0], "farmLauncher.description.HAY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("hay cutting", "forage crop", "stock and orders");
            assertThat(named(root[0], "farmLauncher.description.HOPS_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("hop yards", "stringing", "harvest window");
            assertThat(named(root[0], "farmLauncher.description.GRAIN_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("grain fields", "rotation", "seeding");
            assertThat(named(root[0], "farmLauncher.description.SEED_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("seed fields", "flowering windows", "isolation notes");
            assertThat(named(root[0], "farmLauncher.description.NUT_ORCHARD",
                    javax.swing.JTextArea.class).getText())
                    .contains("nut orchard blocks", "pollinizers", "floor prep");
            assertThat(named(root[0], "farmLauncher.description.VEGETABLE_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("vegetable plantings", "Repeat successions",
                            "stock and sales stay separate");
            assertThat(named(root[0], "farmLauncher.description.AQUACULTURE_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("aquatic cohorts", "growing site", "harvest clearance");
            assertThat(named(root[0], "farmLauncher.description.CHRISTMAS_TREE_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("Christmas tree blocks", "shearing", "bookings");
            assertThat(named(root[0], "farmLauncher.description.APIARY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("hive checks", "next visits", "pollination contracts");
            assertThat(named(root[0], "farmLauncher.description.MUSHROOM_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("mushroom batches", "substrate", "fruiting window");
            assertThat(named(root[0], "farmLauncher.description.CRANBERRY_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("cranberry beds", "wet or dry", "target window");
            assertThat(named(root[0], "farmLauncher.description.PUMPKIN_PATCH",
                    javax.swing.JTextArea.class).getText())
                    .contains("pumpkin patches", "pick-your-own", "farm stand");
            assertThat(named(root[0], "farmLauncher.description.CANNABIS_FARM",
                    javax.swing.JTextArea.class).getText())
                    .contains("separately", "No compliance reporting");
            assertThat(named(root[0], "farmLauncher.enter.CANNABIS_FARM", JButton.class)
                    .getAccessibleContext().getAccessibleDescription())
                    .contains("No compliance reporting");
        });
        assertThat(entered).containsExactly(FarmWorkspace.values());
    }

    @Test
    void nonOwnerCanEnterCurrentTypeButCannotSelectAnother() throws Exception {
        List<FarmWorkspace> entered = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            FarmTypeLauncher root = new FarmTypeLauncher(FarmWorkspace.FLOWER_SHOP,
                    false, entered::add, () -> { });
            for (FarmWorkspace choice : FarmWorkspace.values()) {
                JButton button = named(root, "farmLauncher.enter." + choice.name(),
                        JButton.class);
                assertThat(button.isEnabled()).isEqualTo(choice == FarmWorkspace.FLOWER_SHOP);
                button.doClick();
            }
        });
        assertThat(entered).containsExactly(FarmWorkspace.FLOWER_SHOP);
    }

    @Test
    void searchFindsFarmTypesByCropOrCommonNameAndCanRecoverFromNoMatches()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FarmTypeLauncher launcher = new FarmTypeLauncher(
                    FarmWorkspace.ROSE_AND_EVENTS,
                    Set.of(FarmWorkspace.ROSE_AND_EVENTS), true,
                    choice -> { }, () -> { }, () -> { });
            JTextField search = named(launcher, "farmLauncher.search", JTextField.class);
            JPanel cards = named(launcher, "farmLauncher.cards", JPanel.class);
            JPanel empty = named(launcher, "farmLauncher.empty", JPanel.class);
            JLabel count = named(launcher, "farmLauncher.matchCount", JLabel.class);
            assertThat(search.getAccessibleContext().getAccessibleName())
                    .isEqualTo("Find a farm type");
            assertThat(cardsShown(launcher)).isEqualTo(FarmWorkspace.values().length);

            search.setText("marijuana");
            assertThat(cards.getComponentCount()).isEqualTo(3);
            assertThat(named(cards, "farmLauncher.enter.CANNABIS_FARM", JButton.class))
                    .isNotNull();
            assertThat(count.getText()).isEqualTo("1 farm type");

            search.setText("subcool super soil");
            assertThat(named(cards, "farmLauncher.enter.CANNABIS_FARM", JButton.class))
                    .isNotNull();

            search.setText("BLUEBERRY");
            assertThat(cards.getComponentCount()).isEqualTo(3);
            assertThat(named(cards, "farmLauncher.enter.BERRY_FARM", JButton.class))
                    .isNotNull();

            search.setText("plant propagation");
            assertThat(cards.getComponentCount()).isEqualTo(3);
            assertThat(named(cards, "farmLauncher.enter.NURSERY_FARM", JButton.class))
                    .isNotNull();

            search.setText("goats");
            assertThat(named(cards, "farmLauncher.enter.LIVESTOCK_FARM", JButton.class))
                    .isNotNull();

            search.setText("chickens");
            assertThat(named(cards, "farmLauncher.enter.POULTRY_FARM", JButton.class))
                    .isNotNull();

            search.setText("milking");
            assertThat(named(cards, "farmLauncher.enter.DAIRY_FARM", JButton.class))
                    .isNotNull();

            search.setText("wine grapes");
            assertThat(named(cards, "farmLauncher.enter.VINEYARD_FARM", JButton.class))
                    .isNotNull();

            search.setText("alfalfa");
            assertThat(named(cards, "farmLauncher.enter.HAY_FARM", JButton.class))
                    .isNotNull();

            search.setText("bines");
            assertThat(named(cards, "farmLauncher.enter.HOPS_FARM", JButton.class))
                    .isNotNull();

            search.setText("barley");
            assertThat(named(cards, "farmLauncher.enter.GRAIN_FARM", JButton.class))
                    .isNotNull();

            search.setText("grass seed");
            assertThat(named(cards, "farmLauncher.enter.SEED_FARM", JButton.class))
                    .isNotNull();

            search.setText("hazelnuts");
            assertThat(named(cards, "farmLauncher.enter.NUT_ORCHARD", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("vegetables");
            assertThat(named(cards, "farmLauncher.enter.VEGETABLE_FARM", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("oysters");
            assertThat(named(cards, "farmLauncher.enter.AQUACULTURE_FARM", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("christmas");
            assertThat(named(cards, "farmLauncher.enter.CHRISTMAS_TREE_FARM", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("beekeeping");
            assertThat(named(cards, "farmLauncher.enter.APIARY_FARM", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("shiitake");
            assertThat(named(cards, "farmLauncher.enter.MUSHROOM_FARM", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("cranberries");
            assertThat(named(cards, "farmLauncher.enter.CRANBERRY_FARM", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("pumpkins");
            assertThat(named(cards, "farmLauncher.enter.PUMPKIN_PATCH", JButton.class))
                    .isNotNull();
            assertThat(cardsShown(launcher)).isEqualTo(1);

            search.setText("no such crop");
            assertThat(cards.getComponentCount()).isZero();
            assertThat(empty.isVisible()).isTrue();
            assertThat(count.getText()).isEqualTo("0 farm types");
            named(launcher, "farmLauncher.clearSearch", JButton.class).doClick();
            assertThat(search.getText()).isEmpty();
            assertThat(cardsShown(launcher)).isEqualTo(FarmWorkspace.values().length);
            assertThat(empty.isVisible()).isFalse();
        });
    }

    @Test
    void changingCardsKeepsAllFarmDetailsAndPreviousBusinessLines() {
        FarmOperationSettings rose = new FarmOperationSettings();
        rose.setState(FarmState.OR);
        rose.setEnterprises(Set.of(FarmEnterprise.CUT_FLOWERS, FarmEnterprise.ORCHARDS));
        rose.setSpecialtyCrops(Set.of("Dahlias", "Honeycrisp apples"));
        rose.setFlowerOfferings(Set.of(FlowerOffering.STEMS, FlowerOffering.WEDDINGS));

        FarmOperationSettings cannabis = FarmTypeLauncher.settingsFor(
                rose, FarmWorkspace.CANNABIS_FARM);
        assertThat(cannabis.getWorkspace()).isEqualTo(FarmWorkspace.CANNABIS_FARM);
        assertThat(cannabis.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CANNABIS);
        assertThat(cannabis.getEnterprises()).containsExactly(
                FarmEnterprise.CUT_FLOWERS, FarmEnterprise.ORCHARDS, FarmEnterprise.CANNABIS);
        assertThat(cannabis.getState()).isEqualTo(FarmState.OR);
        assertThat(cannabis.getSpecialtyCrops()).containsExactly("Dahlias", "Honeycrisp apples");
        assertThat(cannabis.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.WEDDINGS);

        FarmOperationSettings custom = FarmTypeLauncher.settingsFor(
                cannabis, FarmWorkspace.CUSTOM_FARM);
        assertThat(custom.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CANNABIS);
        assertThat(custom.getEnterprises()).isEqualTo(cannabis.getEnterprises());
        assertThat(custom.getFlowerOfferings()).isEqualTo(rose.getFlowerOfferings());
        assertThat(rose.getWorkspace()).isEqualTo(FarmWorkspace.ROSE_AND_EVENTS);

        FarmOperationSettings potato = FarmTypeLauncher.settingsFor(
                custom, FarmWorkspace.POTATO_FARM);
        assertThat(potato.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.ROW_CROPS);
        assertThat(potato.getEnterprises()).contains(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.ORCHARDS, FarmEnterprise.CANNABIS);

        FarmOperationSettings apple = FarmTypeLauncher.settingsFor(
                potato, FarmWorkspace.APPLE_ORCHARD);
        assertThat(apple.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.ORCHARDS);
        assertThat(apple.getSpecialtyCrops()).containsExactly(
                "Dahlias", "Honeycrisp apples");

        FarmOperationSettings berry = FarmTypeLauncher.settingsFor(
                apple, FarmWorkspace.BERRY_FARM);
        assertThat(berry.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.BERRIES);
        assertThat(berry.getEnterprises()).contains(FarmEnterprise.BERRIES,
                FarmEnterprise.ROW_CROPS, FarmEnterprise.ORCHARDS);

        FarmOperationSettings nursery = FarmTypeLauncher.settingsFor(
                berry, FarmWorkspace.NURSERY_FARM);
        assertThat(nursery.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.NURSERY);
        assertThat(nursery.getEnterprises()).contains(FarmEnterprise.NURSERY,
                FarmEnterprise.BERRIES, FarmEnterprise.ORCHARDS,
                FarmEnterprise.CUT_FLOWERS);
        assertThat(nursery.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.WEDDINGS);
    }

    @Test
    void customFarmCanJoinAndLeadAMixedOperation() {
        FarmOperationSettings starter = new FarmOperationSettings();
        LinkedHashSet<FarmWorkspace> farmTypes = new LinkedHashSet<>(List.of(
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.APPLE_ORCHARD));

        FarmOperationSettings mixed = FarmTypeLauncher.settingsForCombination(
                starter, FarmWorkspace.CUSTOM_FARM, farmTypes);

        assertThat(mixed.getWorkspace()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(mixed.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.CUSTOM_FARM, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.APPLE_ORCHARD);
        assertThat(mixed.getEnterprises()).contains(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.ORCHARDS);
        assertThat(mixed.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.OTHER);
        assertThat(mixed.getEnterprises()).doesNotContain(FarmEnterprise.CUT_FLOWERS);
        assertThat(mixed.getFlowerOfferings()).isEmpty();
        mixed.validate();

        FarmOperationSettings namedPrimary = FarmTypeLauncher.settingsForCombination(
                mixed, FarmWorkspace.POTATO_FARM, farmTypes);
        assertThat(namedPrimary.getWorkspace()).isEqualTo(FarmWorkspace.POTATO_FARM);
        assertThat(namedPrimary.getEnabledWorkspaces()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CUSTOM_FARM,
                FarmWorkspace.APPLE_ORCHARD);
        namedPrimary.validate();
    }

    @Test
    void firstNonflowerCardReplacesTheStarterFlowerLineAndOfferings() {
        FarmOperationSettings starter = new FarmOperationSettings();
        for (FarmWorkspace choice : List.of(FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD,
                FarmWorkspace.BERRY_FARM, FarmWorkspace.NURSERY_FARM,
                FarmWorkspace.LIVESTOCK_FARM, FarmWorkspace.POULTRY_FARM,
                FarmWorkspace.DAIRY_FARM, FarmWorkspace.VINEYARD_FARM,
                FarmWorkspace.HAY_FARM, FarmWorkspace.HOPS_FARM,
                FarmWorkspace.GRAIN_FARM, FarmWorkspace.SEED_FARM,
                FarmWorkspace.NUT_ORCHARD, FarmWorkspace.VEGETABLE_FARM,
                FarmWorkspace.AQUACULTURE_FARM,
                FarmWorkspace.CHRISTMAS_TREE_FARM,
                FarmWorkspace.APIARY_FARM,
                FarmWorkspace.MUSHROOM_FARM,
                FarmWorkspace.CRANBERRY_FARM,
                FarmWorkspace.PUMPKIN_PATCH)) {
            FarmOperationSettings selected = FarmTypeLauncher.settingsFor(starter, choice);
            assertThat(selected.getWorkspace()).isEqualTo(choice);
            assertThat(selected.getPrimaryEnterprise())
                    .isEqualTo(choice.requiredEnterprise().orElseThrow());
            assertThat(selected.getEnterprises()).containsExactly(
                    choice.requiredEnterprise().orElseThrow());
            assertThat(selected.getFlowerOfferings()).isEmpty();
        }
        assertThat(starter.getEnterprises()).containsExactly(FarmEnterprise.CUT_FLOWERS);
        assertThat(starter.getFlowerOfferings()).contains(FlowerOffering.STEMS);

        FarmOperationSettings custom = FarmTypeLauncher.settingsFor(
                starter, FarmWorkspace.CUSTOM_FARM);
        assertThat(custom.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.OTHER);
        assertThat(custom.getEnterprises()).containsExactly(FarmEnterprise.OTHER);
        assertThat(custom.getFlowerOfferings()).isEmpty();

        FarmOperationSettings configuredFlowers = new FarmOperationSettings();
        configuredFlowers.setSpecialtyCrops(Set.of("Roses"));
        FarmOperationSettings configuredCustom = FarmTypeLauncher.settingsFor(
                configuredFlowers, FarmWorkspace.CUSTOM_FARM);
        assertThat(configuredCustom.getPrimaryEnterprise())
                .isEqualTo(FarmEnterprise.CUT_FLOWERS);
        assertThat(configuredCustom.getFlowerOfferings())
                .contains(FlowerOffering.STEMS);

        FarmOperationSettings shop = FarmTypeLauncher.settingsFor(
                starter, FarmWorkspace.FLOWER_SHOP);
        assertThat(shop.getEnterprises()).containsExactly(FarmEnterprise.FLORAL_RETAIL);
        assertThat(shop.getFlowerOfferings()).containsExactly(
                FlowerOffering.STEMS, FlowerOffering.BOUQUETS, FlowerOffering.WEDDINGS);
    }

    @Test
    void ownerCanOpenAndReopenAVisibleCombinationEditor() throws Exception {
        List<String> opened = new ArrayList<>();
        Set<FarmWorkspace> mix = new java.util.LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD));
        SwingUtilities.invokeAndWait(() -> {
            FarmTypeLauncher launcher = new FarmTypeLauncher(FarmWorkspace.POTATO_FARM,
                    mix, true, choice -> { }, () -> { }, () -> opened.add("combine"));
            JButton combine = named(launcher, "farmLauncher.combine", JButton.class);
            assertThat(combine.getText()).isEqualTo("Edit combined farm…");
            assertThat(named(launcher, "farmLauncher.mixSummary", JLabel.class).getText())
                    .contains("Potato farm", "Cannabis farm", "Apple orchard");
            assertThat(launcher.currentEnterButton().getText())
                    .isEqualTo("Enter combined farm");
            assertThat(named(launcher, "farmLauncher.enter.APPLE_ORCHARD", JButton.class)
                    .getText()).isEqualTo("Open in mix");
            JButton addBerry = named(launcher, "farmLauncher.enter.BERRY_FARM",
                    JButton.class);
            assertThat(addBerry.getText()).isEqualTo("Add to mix…");
            assertThat(addBerry.getToolTipText()).contains("Save to keep the change");
            assertThat(addBerry.getAccessibleContext().getAccessibleName())
                    .isEqualTo("Add to mix Berry farm");
            assertThat(named(launcher, "farmLauncher.enter.CUSTOM_FARM", JButton.class)
                    .getText()).isEqualTo("Add to mix…");
            combine.doClick();
        });
        assertThat(opened).containsExactly("combine");
    }

    @Test
    void combinedLauncherKeepsAddToMixCardReadableAtDesktopWidth() throws Exception {
        Path output = Path.of("target", "ui-previews", "combined-launcher-add.png");
        Files.createDirectories(output.getParent());
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
            FarmTypeLauncher launcher = new FarmTypeLauncher(FarmWorkspace.POTATO_FARM,
                    Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                            FarmWorkspace.APPLE_ORCHARD), true,
                    choice -> { }, () -> { }, () -> { });
            JPanel window = new JPanel(new BorderLayout());
            window.add(launcher, BorderLayout.CENTER);
            window.setSize(1100, 700);
            layoutTree(window);
            JButton addRose = named(launcher, "farmLauncher.enter.ROSE_AND_EVENTS",
                    JButton.class);
            JPanel roseCard = named(launcher, "farmLauncher.card.ROSE_AND_EVENTS",
                    JPanel.class);
            Rectangle buttonBounds = SwingUtilities.convertRectangle(addRose.getParent(),
                    addRose.getBounds(), roseCard);
            assertThat(addRose.getText()).isEqualTo("Add to mix…");
            assertThat(new Rectangle(0, 0, roseCard.getWidth(), roseCard.getHeight())
                    .contains(buttonBounds)).isTrue();
            savePreview(window, output);
        });
    }

    @Test
    void compactDesktopUsesTwoColumnsWithoutHorizontalCardScrolling() throws Exception {
        Path output = Path.of("target", "ui-previews", "farm-type-launcher-compact.png");
        Files.createDirectories(output.getParent());
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
            FarmTypeLauncher launcher = new FarmTypeLauncher(
                    FarmWorkspace.ROSE_AND_EVENTS,
                    Set.of(FarmWorkspace.ROSE_AND_EVENTS), true,
                    choice -> { }, () -> { }, () -> { });
            JPanel window = new JPanel(new BorderLayout());
            window.add(launcher, BorderLayout.CENTER);
            window.setSize(850, 700);
            layoutTree(window);
            // Refresh after layout so the card grid uses the actual viewport width.
            JTextField search = named(launcher, "farmLauncher.search", JTextField.class);
            search.setText("rose");
            search.setText("");
            layoutTree(window);

            JScrollPane scroll = named(launcher, "farmLauncher.cardScroll",
                    JScrollPane.class);
            JPanel cards = named(launcher, "farmLauncher.cards", JPanel.class);
            assertThat(((GridLayout) cards.getLayout()).getColumns()).isEqualTo(2);
            assertThat(scroll.getHorizontalScrollBarPolicy())
                    .isEqualTo(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            assertThat(cards.getWidth()).isLessThanOrEqualTo(scroll.getViewport().getWidth());
            JButton rose = named(launcher, "farmLauncher.enter.ROSE_AND_EVENTS",
                    JButton.class);
            assertThat(rose.getWidth()).isGreaterThan(75);
            savePreview(window, output);

            scroll.getVerticalScrollBar().setValue(
                    scroll.getVerticalScrollBar().getMaximum()
                            - scroll.getVerticalScrollBar().getVisibleAmount());
            Rectangle viewport = SwingUtilities.convertRectangle(scroll.getViewport(),
                    new Rectangle(0, 0, scroll.getViewport().getWidth(),
                            scroll.getViewport().getHeight()), window);
            JButton custom = named(launcher, "farmLauncher.enter.CUSTOM_FARM",
                    JButton.class);
            Rectangle customBounds = SwingUtilities.convertRectangle(custom.getParent(),
                    custom.getBounds(), window);
            assertThat(viewport.contains(customBounds)).isTrue();
        });
    }

    @Test
    void viewerCanOpenAnyIncludedTypeWithoutChangingTheMix() throws Exception {
        List<FarmWorkspace> entered = new ArrayList<>();
        Set<FarmWorkspace> mix = new java.util.LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD));
        SwingUtilities.invokeAndWait(() -> {
            FarmTypeLauncher launcher = new FarmTypeLauncher(FarmWorkspace.POTATO_FARM,
                    mix, false, entered::add, () -> { }, () -> { });
            JButton included = named(launcher, "farmLauncher.enter.APPLE_ORCHARD",
                    JButton.class);
            assertThat(included.isEnabled()).isTrue();
            assertThat(included.getText()).isEqualTo("Open in mix");
            assertThat(named(launcher, "farmLauncher.enter.CANNABIS_FARM",
                    JButton.class).isEnabled()).isFalse();
            included.doClick();
        });
        assertThat(entered).containsExactly(FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void combinedSelectionKeepsAllChosenToolsAndCanReturnToOneType() {
        FarmOperationSettings starter = new FarmOperationSettings();
        Set<FarmWorkspace> mix = new java.util.LinkedHashSet<>(List.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD));

        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(starter,
                FarmWorkspace.POTATO_FARM, mix);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyElementsOf(mix);
        assertThat(combined.getEnterprises()).containsExactly(
                FarmEnterprise.ROW_CROPS, FarmEnterprise.ORCHARDS,
                FarmEnterprise.CANNABIS);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.ROW_CROPS);
        assertThat(starter.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.ROSE_AND_EVENTS);

        FarmOperationSettings solo = FarmTypeLauncher.settingsFor(
                combined, FarmWorkspace.APPLE_ORCHARD);
        assertThat(solo.getEnabledWorkspaces())
                .containsExactly(FarmWorkspace.APPLE_ORCHARD);
        assertThat(solo.getEnterprises()).contains(FarmEnterprise.ROW_CROPS,
                FarmEnterprise.ORCHARDS, FarmEnterprise.CANNABIS);
    }

    @Test
    void potatoAndPoultryCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.POULTRY_FARM,
                Set.of(FarmWorkspace.POULTRY_FARM, FarmWorkspace.POTATO_FARM));
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.POULTRY_FARM, FarmWorkspace.POTATO_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.POULTRY_AND_EGGS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.POULTRY_AND_EGGS, FarmEnterprise.ROW_CROPS);
    }

    @Test
    void dairyAndLivestockCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.DAIRY_FARM,
                Set.of(FarmWorkspace.DAIRY_FARM, FarmWorkspace.LIVESTOCK_FARM));
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.DAIRY_FARM, FarmWorkspace.LIVESTOCK_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.DAIRY);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.DAIRY, FarmEnterprise.LIVESTOCK);
    }

    @Test
    void vineyardAndAppleCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.VINEYARD_FARM,
                Set.of(FarmWorkspace.VINEYARD_FARM, FarmWorkspace.APPLE_ORCHARD));
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.VINEYARD_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.VINEYARDS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.VINEYARDS, FarmEnterprise.ORCHARDS);
    }

    @Test
    void hayAndDairyCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.HAY_FARM,
                Set.of(FarmWorkspace.HAY_FARM, FarmWorkspace.DAIRY_FARM));
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.HAY_FARM, FarmWorkspace.DAIRY_FARM);
        assertThat(combined.getPrimaryEnterprise())
                .isEqualTo(FarmEnterprise.HAY_AND_FORAGE);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.HAY_AND_FORAGE, FarmEnterprise.DAIRY);
    }

    @Test
    void hopsAndPotatoCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.HOPS_FARM,
                Set.of(FarmWorkspace.HOPS_FARM, FarmWorkspace.POTATO_FARM));
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.HOPS_FARM, FarmWorkspace.POTATO_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.HOPS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.HOPS, FarmEnterprise.ROW_CROPS);
    }

    @Test
    void grainsAndPotatoCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.GRAIN_FARM,
                Set.of(FarmWorkspace.GRAIN_FARM, FarmWorkspace.POTATO_FARM));
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.GRAIN_FARM, FarmWorkspace.POTATO_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.GRAINS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.GRAINS, FarmEnterprise.ROW_CROPS);
    }

    @Test
    void seedAndGrainsCombinationKeepsBothBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.SEED_FARM,
                Set.of(FarmWorkspace.SEED_FARM, FarmWorkspace.GRAIN_FARM));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.SEED_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.SEED_FARM, FarmWorkspace.GRAIN_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.SEED_CROPS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.SEED_CROPS, FarmEnterprise.GRAINS);
    }

    @Test
    void treeNutAndAppleCombinationKeepsDistinctOrchardBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.NUT_ORCHARD,
                Set.of(FarmWorkspace.NUT_ORCHARD, FarmWorkspace.APPLE_ORCHARD));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.NUT_ORCHARD);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.NUT_ORCHARD, FarmWorkspace.APPLE_ORCHARD);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.TREE_NUTS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.TREE_NUTS, FarmEnterprise.ORCHARDS);
    }

    @Test
    void vegetableAndPotatoCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.VEGETABLE_FARM,
                Set.of(FarmWorkspace.VEGETABLE_FARM, FarmWorkspace.POTATO_FARM));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.VEGETABLE_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.VEGETABLE_FARM, FarmWorkspace.POTATO_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.VEGETABLES);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.VEGETABLES, FarmEnterprise.ROW_CROPS);
    }

    @Test
    void aquacultureAndPotatoCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.AQUACULTURE_FARM,
                Set.of(FarmWorkspace.AQUACULTURE_FARM, FarmWorkspace.POTATO_FARM));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.AQUACULTURE_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.AQUACULTURE_FARM, FarmWorkspace.POTATO_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.AQUACULTURE);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.AQUACULTURE, FarmEnterprise.ROW_CROPS);
    }

    @Test
    void christmasTreeAndNurseryCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.CHRISTMAS_TREE_FARM,
                Set.of(FarmWorkspace.CHRISTMAS_TREE_FARM, FarmWorkspace.NURSERY_FARM));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.CHRISTMAS_TREE_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.CHRISTMAS_TREE_FARM, FarmWorkspace.NURSERY_FARM);
        assertThat(combined.getPrimaryEnterprise())
                .isEqualTo(FarmEnterprise.CHRISTMAS_TREES);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.CHRISTMAS_TREES, FarmEnterprise.NURSERY);
    }

    @Test
    void apiaryAndAppleCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.APIARY_FARM,
                Set.of(FarmWorkspace.APIARY_FARM, FarmWorkspace.APPLE_ORCHARD));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.APIARY_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.APIARY_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.APIARY);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.APIARY, FarmEnterprise.ORCHARDS);
    }

    @Test
    void mushroomAndAppleCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.MUSHROOM_FARM,
                Set.of(FarmWorkspace.MUSHROOM_FARM, FarmWorkspace.APPLE_ORCHARD));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.MUSHROOM_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.MUSHROOM_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.MUSHROOMS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.MUSHROOMS, FarmEnterprise.ORCHARDS);
    }

    @Test
    void cranberryAndMushroomCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.CRANBERRY_FARM,
                Set.of(FarmWorkspace.CRANBERRY_FARM, FarmWorkspace.MUSHROOM_FARM));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.CRANBERRY_FARM);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.CRANBERRY_FARM, FarmWorkspace.MUSHROOM_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.CRANBERRIES);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.CRANBERRIES, FarmEnterprise.MUSHROOMS);
    }

    @Test
    void pumpkinAndCranberryCombinationKeepsDistinctBusinessLines() {
        FarmOperationSettings combined = FarmTypeLauncher.settingsForCombination(
                new FarmOperationSettings(), FarmWorkspace.PUMPKIN_PATCH,
                Set.of(FarmWorkspace.PUMPKIN_PATCH, FarmWorkspace.CRANBERRY_FARM));
        assertThat(combined.getWorkspace()).isEqualTo(FarmWorkspace.PUMPKIN_PATCH);
        assertThat(combined.getEnabledWorkspaces()).containsExactlyInAnyOrder(
                FarmWorkspace.PUMPKIN_PATCH, FarmWorkspace.CRANBERRY_FARM);
        assertThat(combined.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.PUMPKINS);
        assertThat(combined.getEnterprises()).containsExactlyInAnyOrder(
                FarmEnterprise.PUMPKINS, FarmEnterprise.CRANBERRIES);
    }

    @Test
    void configuredSingleRoseOperationKeepsItsBusinessLineWhenAddingNursery() {
        FarmOperationSettings roses = new FarmOperationSettings();
        roses.setState(FarmState.OR);
        roses.setSpecialtyCrops(Set.of("Nootka roses"));

        FarmOperationSettings nursery = FarmTypeLauncher.settingsFor(
                roses, FarmWorkspace.NURSERY_FARM);

        assertThat(nursery.getPrimaryEnterprise()).isEqualTo(FarmEnterprise.NURSERY);
        assertThat(nursery.getEnterprises()).containsExactly(
                FarmEnterprise.CUT_FLOWERS, FarmEnterprise.NURSERY);
        assertThat(nursery.getFlowerOfferings()).isEqualTo(roses.getFlowerOfferings());
        assertThat(nursery.getSpecialtyCrops()).containsExactly("Nootka roses");
    }

    @Test
    void minimumDesktopSizeKeepsEveryChoiceReachable() throws Exception {
        Path output = Path.of("target", "ui-previews", "farm-type-launcher.png");
        Files.createDirectories(output.getParent());
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
            JPanel window = new JPanel(new BorderLayout());
            window.add(FarmUi.brandHeader("Sample Farm", "Farm operations",
                    "Washington", "Wednesday · September 30", "Rose & events"),
                    BorderLayout.NORTH);
            FarmTypeLauncher launcher = new FarmTypeLauncher(
                    FarmWorkspace.ROSE_AND_EVENTS,
                    Set.of(FarmWorkspace.ROSE_AND_EVENTS), true,
                    choice -> { }, () -> { }, () -> { });
            window.add(launcher, BorderLayout.CENTER);
            window.add(new JLabel("Ready · local mode (auth off)"), BorderLayout.SOUTH);
            window.setSize(1100, 700);
            layoutTree(window);

            JScrollPane scroll = named(launcher, "farmLauncher.cardScroll", JScrollPane.class);
            Rectangle viewport = SwingUtilities.convertRectangle(scroll.getViewport(),
                    new Rectangle(0, 0, scroll.getViewport().getWidth(),
                            scroll.getViewport().getHeight()), window);
            for (FarmWorkspace choice : FarmWorkspace.values()) {
                JButton button = named(launcher, "farmLauncher.enter." + choice.name(),
                        JButton.class);
                Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(),
                        button.getBounds(), window);
                assertThat(button.getWidth()).isGreaterThan(75);
                assertThat(button.getHeight()).isGreaterThan(25);
                if (choice == FarmWorkspace.ROSE_AND_EVENTS) {
                    assertThat(viewport.contains(bounds)).as(choice.name()).isTrue();
                }
            }
            savePreview(window, output);

            int lastPosition = scroll.getVerticalScrollBar().getMaximum()
                    - scroll.getVerticalScrollBar().getVisibleAmount();
            scroll.getVerticalScrollBar().setValue(lastPosition);
            JButton custom = named(launcher, "farmLauncher.enter.CUSTOM_FARM", JButton.class);
            Rectangle customBounds = SwingUtilities.convertRectangle(custom.getParent(),
                    custom.getBounds(), window);
            assertThat(viewport.contains(customBounds)).isTrue();

            scroll.getVerticalScrollBar().setValue(0);
            window.setSize(1100, 2350);
            layoutTree(window);
            Rectangle tallViewport = SwingUtilities.convertRectangle(scroll.getViewport(),
                    new Rectangle(0, 0, scroll.getViewport().getWidth(),
                            scroll.getViewport().getHeight()), window);
            Rectangle tallCustomBounds = SwingUtilities.convertRectangle(custom.getParent(),
                    custom.getBounds(), window);
            assertThat(tallViewport.contains(tallCustomBounds)).isTrue();
            savePreview(window, output.resolveSibling("farm-type-launcher-all.png"));

            JTextField search = named(launcher, "farmLauncher.search", JTextField.class);
            search.setText("nursery");
            layoutTree(window);
            assertThat(cardsShown(launcher)).isEqualTo(1);
            savePreview(window, output.resolveSibling("farm-type-launcher-search.png"));
        });
    }

    private static int cardsShown(FarmTypeLauncher launcher) {
        JPanel cards = named(launcher, "farmLauncher.cards", JPanel.class);
        return (int) java.util.Arrays.stream(cards.getComponents())
                .filter(component -> component.getName() != null
                        && component.getName().startsWith("farmLauncher.card."))
                .count();
    }

    private static void savePreview(Container window, Path output) {
        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        window.printAll(graphics);
        graphics.dispose();
        try {
            ImageIO.write(image, "png", output.toFile());
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) {
                layoutTree(container);
            }
        }
    }

    private static <T extends Component> T named(Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = named(child, name, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
