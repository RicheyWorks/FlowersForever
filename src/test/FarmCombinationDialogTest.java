package com.flowerfarm.gui;

import com.flowerfarm.auth.FarmRole;
import com.flowerfarm.gui.harness.SwingDialogHarness;
import com.flowerfarm.model.FarmWorkspace;
import com.formdev.flatlaf.FlatLightLaf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.TransferHandler;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Combined farm type picker")
class FarmCombinationDialogTest {

    @Test
    void ownerCanEditMixAndPrimaryWithoutMutatingTheSavedSelection() {
        Set<FarmWorkspace> saved = new LinkedHashSet<>(Set.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD));
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, saved, true);

        JCheckBox potato = named(form, "farmCombination.include.POTATO_FARM",
                JCheckBox.class);
        JCheckBox cannabis = named(form, "farmCombination.include.CANNABIS_FARM",
                JCheckBox.class);
        JCheckBox apples = named(form, "farmCombination.include.APPLE_ORCHARD",
                JCheckBox.class);
        JCheckBox berries = named(form, "farmCombination.include.BERRY_FARM",
                JCheckBox.class);
        JComboBox<?> primary = named(form, "farmCombination.primary", JComboBox.class);
        JTextArea preview = named(form, "farmCombination.preview", JTextArea.class);

        assertThat(potato.isSelected()).isTrue();
        assertThat(cannabis.isSelected()).isTrue();
        assertThat(apples.isSelected()).isTrue();
        assertThat(berries.isSelected()).isFalse();
        assertThat(preview.getText()).contains("Field plans", "Cultivation",
                "Orchard blocks", "Cannabis reporting");

        berries.doClick();
        potato.doClick();
        primary.setSelectedItem(FarmWorkspace.CANNABIS_FARM);

        FarmCombinationDialog.Selection draft = form.selection();
        assertThat(draft.primary()).isEqualTo(FarmWorkspace.CANNABIS_FARM);
        assertThat(draft.included()).containsExactly(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.APPLE_ORCHARD,
                FarmWorkspace.BERRY_FARM);
        assertThat(preview.getText()).contains("Berry blocks")
                .doesNotContain("Field plans");
        assertThat(saved).containsExactlyInAnyOrder(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM,
                FarmWorkspace.APPLE_ORCHARD);
        assertThatThrownBy(() -> draft.included().add(FarmWorkspace.NURSERY_FARM))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void removingTypesCanReturnToOnePrimaryTypeButCannotSaveNone() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM,
                Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD), true);
        named(form, "farmCombination.include.POTATO_FARM", JCheckBox.class).doClick();

        FarmCombinationDialog.Selection single = form.selection();
        assertThat(single.primary()).isEqualTo(FarmWorkspace.APPLE_ORCHARD);
        assertThat(single.included()).containsExactly(FarmWorkspace.APPLE_ORCHARD);

        named(form, "farmCombination.include.APPLE_ORCHARD", JCheckBox.class).doClick();
        assertThatThrownBy(form::selection)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one farm type");
    }

    @Test
    void startingANewCombinationRequiresTwoFarmTypes() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, Set.of(FarmWorkspace.POTATO_FARM), true);
        assertThatThrownBy(form::selection)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two farm types");

        named(form, "farmCombination.include.APPLE_ORCHARD", JCheckBox.class)
                .doClick();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void customFarmCanJoinANamedMixAndBecomeMainWithoutClaimingAnotherTool() {
        Set<FarmWorkspace> saved = Set.of(FarmWorkspace.POTATO_FARM);
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, saved, true);
        JTextField search = named(form, "farmCombination.search", JTextField.class);
        JPanel cards = named(form, "farmCombination.cards", JPanel.class);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JPanel main = named(form, "farmCombination.mainDrop", JPanel.class);
        JPanel remove = named(form, "farmCombination.removeDrop", JPanel.class);
        JTextArea preview = named(form, "farmCombination.preview", JTextArea.class);

        search.setText("custom");
        assertThat(cards.getComponentCount()).isEqualTo(1);
        assertThat(cards.getComponent(0).getName())
                .isEqualTo("farmCombination.card.CUSTOM_FARM");
        JLabel custom = named(cards, "farmCombination.drag.CUSTOM_FARM", JLabel.class);
        Transferable card = dragPayload(custom);
        assertThat(drop(mix, card)).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CUSTOM_FARM);
        assertThat(preview.getText()).contains("Field plans", "shared records",
                "no crop-specific planning tool");
        assertThat(preview.getText()).doesNotContain("Selected tools: none");

        assertThat(drop(main, card)).isTrue();
        assertThat(form.selection().primary()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(drop(remove, card)).isTrue();
        assertThatThrownBy(form::selection).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two farm types");
        assertThat(saved).containsExactly(FarmWorkspace.POTATO_FARM);
    }

    @Test
    void standaloneCustomFarmCanStartAMixWithANamedType() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.CUSTOM_FARM, Set.of(FarmWorkspace.CUSTOM_FARM), true);

        assertThatThrownBy(form::selection).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two farm types");
        named(form, "farmCombination.include.APPLE_ORCHARD", JCheckBox.class).doClick();

        FarmCombinationDialog.Selection selection = form.selection();
        assertThat(selection.primary()).isEqualTo(FarmWorkspace.CUSTOM_FARM);
        assertThat(selection.included()).containsExactly(
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.CUSTOM_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Orchard blocks", "unlisted business lines");
    }

    @Test
    void searchFindsFarmCardsWithoutChangingTheDraftMixOrMainType() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM,
                Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD), true);
        JTextField search = named(form, "farmCombination.search", JTextField.class);
        JPanel cards = named(form, "farmCombination.cards", JPanel.class);
        JLabel count = named(form, "farmCombination.matchCount", JLabel.class);
        JButton clear = named(form, "farmCombination.clearSearch", JButton.class);
        JPanel empty = named(form, "farmCombination.empty", JPanel.class);
        JComboBox<?> primary = named(form, "farmCombination.primary", JComboBox.class);

        assertThat(search.getAccessibleContext().getAccessibleName())
                .isEqualTo("Find a farm type");
        assertThat(count.getText()).isEqualTo("25 farm types");
        assertThat(clear.isEnabled()).isFalse();

        search.setText("marijuana");
        assertThat(cards.getComponentCount()).isEqualTo(1);
        assertThat(cards.getComponent(0).getName())
                .isEqualTo("farmCombination.card.CANNABIS_FARM");
        assertThat(count.getText()).isEqualTo("1 farm type");
        assertThat(clear.isEnabled()).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(primary.getSelectedItem()).isEqualTo(FarmWorkspace.POTATO_FARM);

        JLabel cannabis = named(cards, "farmCombination.drag.CANNABIS_FARM", JLabel.class);
        assertThat(drop(named(form, "farmCombination.mixDrop", JPanel.class),
                dragPayload(cannabis))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.APPLE_ORCHARD);

        search.setText("nothing-grows-here");
        assertThat(cards.getComponentCount()).isZero();
        assertThat(empty.isVisible()).isTrue();
        assertThat(count.getText()).isEqualTo("0 farm types");
        assertThat(form.selection().primary()).isEqualTo(FarmWorkspace.POTATO_FARM);
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.POTATO_FARM,
                FarmWorkspace.APPLE_ORCHARD);

        clear.doClick();
        assertThat(search.getText()).isEmpty();
        assertThat(cards.getComponentCount())
                .isEqualTo(FarmWorkspace.values().length);
        assertThat(count.getText()).isEqualTo("25 farm types");
        assertThat(clear.isEnabled()).isFalse();
        assertThat(named(form, "farmCombination.include.CANNABIS_FARM",
                JCheckBox.class).isSelected()).isTrue();
        assertThat(form.selection().primary()).isEqualTo(FarmWorkspace.POTATO_FARM);
    }

    @Test
    void ownerSaveReturnsImmutableSelectionAndCancelReturnsNothing() {
        Set<FarmWorkspace> saved = Set.of(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM);
        SwingDialogHarness save = new SwingDialogHarness().chooseForm(
                "Combine farm types", "Save combination", form -> form
                        .select("Main farm type", "Cannabis farm")
                        .expectText("Your combined workspace")
                        .expectText("Farm setup"));

        FarmCombinationDialog.Selection result = new FarmCombinationDialog(save)
                .show(null, FarmWorkspace.POTATO_FARM, saved, FarmRole.OWNER);

        assertThat(result.primary()).isEqualTo(FarmWorkspace.CANNABIS_FARM);
        assertThat(result.included()).containsExactly(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.POTATO_FARM);
        save.assertComplete();

        SwingDialogHarness cancel = new SwingDialogHarness()
                .choose("Combine farm types", "Cancel");
        assertThat(new FarmCombinationDialog(cancel).show(null,
                FarmWorkspace.POTATO_FARM, saved, FarmRole.OWNER)).isNull();
        cancel.assertComplete();
    }

    @Test
    void nonOwnerCanReviewButCannotEditOrSave() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM,
                Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD), false);
        for (FarmWorkspace workspace : FarmWorkspace.values()) {
            assertThat(named(form, "farmCombination.include." + workspace.name(),
                    JCheckBox.class).isEnabled()).isFalse();
        }
        assertThat(named(form, "farmCombination.primary", JComboBox.class)
                .isEnabled()).isFalse();
        assertThat(named(form, "farmCombination.drag.APPLE_ORCHARD", JLabel.class)
                .getTransferHandler()).isNull();
        assertThat(named(form, "farmCombination.drag.CUSTOM_FARM", JLabel.class)
                .getTransferHandler()).isNull();
        assertThat(named(form, "farmCombination.mixDrop", JPanel.class)
                .getTransferHandler()).isNull();
        SwingDialogHarness close = new SwingDialogHarness()
                .choose("Combine farm types", "Close");
        assertThat(new FarmCombinationDialog(close).show(null,
                FarmWorkspace.POTATO_FARM,
                Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD),
                FarmRole.VIEWER)).isNull();
        close.assertComplete();
    }

    @Test
    void draggedCardsCanBeAddedMadeMainAndRemovedInTheDraftOnly() {
        Set<FarmWorkspace> saved = new LinkedHashSet<>(Set.of(FarmWorkspace.POTATO_FARM));
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, saved, true);
        JLabel cannabis = named(form, "farmCombination.drag.CANNABIS_FARM", JLabel.class);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JPanel main = named(form, "farmCombination.mainDrop", JPanel.class);
        JPanel remove = named(form, "farmCombination.removeDrop", JPanel.class);
        JLabel feedback = named(form, "farmCombination.dragFeedback", JLabel.class);

        Transferable cannabisCard = dragPayload(cannabis);
        assertThat(mix.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(mix, cannabisCard))).isTrue();
        assertThat(drop(mix, cannabisCard)).isTrue();
        assertThat(named(form, "farmCombination.include.CANNABIS_FARM", JCheckBox.class)
                .isSelected()).isTrue();
        assertThat(feedback.getText()).contains("Added Cannabis farm", "draft mix");

        Transferable appleCard = dragPayload(named(form,
                "farmCombination.drag.APPLE_ORCHARD", JLabel.class));
        assertThat(drop(main, appleCard)).isTrue();
        assertThat(named(form, "farmCombination.primary", JComboBox.class)
                .getSelectedItem()).isEqualTo(FarmWorkspace.APPLE_ORCHARD);
        assertThat(named(form, "farmCombination.mixSummary", JTextArea.class).getText())
                .contains("Potato farm", "Cannabis farm", "Apple orchard");

        assertThat(drop(remove, dragPayload(named(form,
                "farmCombination.drag.POTATO_FARM", JLabel.class)))).isTrue();
        FarmCombinationDialog.Selection draft = form.selection();
        assertThat(draft.primary()).isEqualTo(FarmWorkspace.APPLE_ORCHARD);
        assertThat(draft.included()).containsExactly(
                FarmWorkspace.CANNABIS_FARM, FarmWorkspace.APPLE_ORCHARD);
        assertThat(feedback.getText()).contains("Removed Potato farm", "records remain");
        assertThat(saved).containsExactly(FarmWorkspace.POTATO_FARM);
    }

    @Test
    void dropTargetsRejectPlainTextAndCheckboxesRemainAvailable() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, Set.of(FarmWorkspace.POTATO_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        StringSelection unrelatedText = new StringSelection("APPLE_ORCHARD");

        assertThat(mix.getTransferHandler().canImport(
                new TransferHandler.TransferSupport(mix, unrelatedText))).isFalse();
        assertThat(drop(mix, unrelatedText)).isFalse();
        assertThat(named(form, "farmCombination.include.APPLE_ORCHARD", JCheckBox.class)
                .isSelected()).isFalse();

        named(form, "farmCombination.include.APPLE_ORCHARD", JCheckBox.class).doClick();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.APPLE_ORCHARD);
    }

    @Test
    void poultryCardCanBeDraggedIntoAPotatoFarmMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, Set.of(FarmWorkspace.POTATO_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel poultry = named(form, "farmCombination.drag.POULTRY_FARM", JLabel.class);

        assertThat(drop(mix, dragPayload(poultry))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.POULTRY_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Egg collections", "Inventory", "Customers & Orders");
    }

    @Test
    void dairyCardCanBeDraggedIntoALivestockMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.LIVESTOCK_FARM,
                Set.of(FarmWorkspace.LIVESTOCK_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel dairy = named(form, "farmCombination.drag.DAIRY_FARM", JLabel.class);

        assertThat(drop(mix, dragPayload(dairy))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.LIVESTOCK_FARM, FarmWorkspace.DAIRY_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Herd groups", "Milk collections", "Inventory");
    }

    @Test
    void seedCardAddsItsOwnToolToAGrainMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.GRAIN_FARM, Set.of(FarmWorkspace.GRAIN_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel seed = named(form, "farmCombination.drag.SEED_FARM", JLabel.class);

        assertThat(drop(mix, dragPayload(seed))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.GRAIN_FARM, FarmWorkspace.SEED_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Grain fields", "Seed fields", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void nutCardAddsItsOwnToolToAnAppleMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.APPLE_ORCHARD, Set.of(FarmWorkspace.APPLE_ORCHARD), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel nut = named(form, "farmCombination.drag.NUT_ORCHARD", JLabel.class);

        assertThat(drop(mix, dragPayload(nut))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.NUT_ORCHARD);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Orchard blocks", "Nut blocks", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void vegetableCardAddsItsOwnToolToAPotatoMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, Set.of(FarmWorkspace.POTATO_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel vegetable = named(form, "farmCombination.drag.VEGETABLE_FARM",
                JLabel.class);

        assertThat(drop(mix, dragPayload(vegetable))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.VEGETABLE_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Field plans", "Vegetable plantings", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void aquacultureCardAddsItsOwnToolToAPotatoMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, Set.of(FarmWorkspace.POTATO_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel aquaculture = named(form, "farmCombination.drag.AQUACULTURE_FARM",
                JLabel.class);

        assertThat(drop(mix, dragPayload(aquaculture))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.AQUACULTURE_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Field plans", "Aquatic cohorts", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void christmasTreeCardAddsItsOwnToolToANurseryMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.NURSERY_FARM, Set.of(FarmWorkspace.NURSERY_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel trees = named(form, "farmCombination.drag.CHRISTMAS_TREE_FARM",
                JLabel.class);

        assertThat(drop(mix, dragPayload(trees))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.NURSERY_FARM, FarmWorkspace.CHRISTMAS_TREE_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Growing batches", "Tree blocks", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void apiaryCardAddsHiveChecksToAnAppleMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.APPLE_ORCHARD, Set.of(FarmWorkspace.APPLE_ORCHARD), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel apiary = named(form, "farmCombination.drag.APIARY_FARM", JLabel.class);

        assertThat(drop(mix, dragPayload(apiary))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.APPLE_ORCHARD, FarmWorkspace.APIARY_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Orchard blocks", "Hive checks", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void mushroomCardAddsBatchPlanningToAPotatoMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.POTATO_FARM, Set.of(FarmWorkspace.POTATO_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel mushrooms = named(form, "farmCombination.drag.MUSHROOM_FARM", JLabel.class);

        assertThat(drop(mix, dragPayload(mushrooms))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.POTATO_FARM, FarmWorkspace.MUSHROOM_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Field plans", "Mushroom batches", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void cranberryCardAddsBedPlanningToAMushroomMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.MUSHROOM_FARM, Set.of(FarmWorkspace.MUSHROOM_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel cranberries = named(form, "farmCombination.drag.CRANBERRY_FARM", JLabel.class);

        assertThat(drop(mix, dragPayload(cranberries))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.MUSHROOM_FARM, FarmWorkspace.CRANBERRY_FARM);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Mushroom batches", "Cranberry beds", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void pumpkinCardAddsPatchPlanningToACranberryMix() {
        FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                FarmWorkspace.CRANBERRY_FARM, Set.of(FarmWorkspace.CRANBERRY_FARM), true);
        JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
        JLabel pumpkins = named(form, "farmCombination.drag.PUMPKIN_PATCH", JLabel.class);

        assertThat(drop(mix, dragPayload(pumpkins))).isTrue();
        assertThat(form.selection().included()).containsExactly(
                FarmWorkspace.CRANBERRY_FARM, FarmWorkspace.PUMPKIN_PATCH);
        assertThat(named(form, "farmCombination.preview", JTextArea.class).getText())
                .contains("Cranberry beds", "Pumpkin patches", "Inventory",
                        "Customers & Orders");
    }

    @Test
    void dialogViewportKeepsDropBoardAndFirstDragHandleVisible() throws Exception {
        Path preview = Path.of("target", "ui-previews", "combined-farm-drag.png");
        Path filteredPreview = Path.of("target", "ui-previews",
                "combined-farm-search.png");
        Files.createDirectories(preview.getParent());
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
            FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                    FarmWorkspace.POTATO_FARM,
                    Set.of(FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM),
                    true);
            JScrollPane scroll = form.dialogScroll(null);
            scroll.setSize(scroll.getPreferredSize());
            layoutTree(scroll);
            Rectangle visible = new Rectangle(0, 0,
                    scroll.getViewport().getWidth(),
                    scroll.getViewport().getHeight());
            for (String target : Set.of("mixDrop", "mainDrop", "removeDrop")) {
                JPanel zone = named(form, "farmCombination." + target, JPanel.class);
                Rectangle bounds = SwingUtilities.convertRectangle(zone.getParent(),
                        zone.getBounds(), scroll.getViewport());
                assertThat(bounds.width).as(target).isGreaterThan(150);
                assertThat(bounds.height).as(target).isGreaterThan(35);
                assertThat(visible.contains(bounds)).as(target).isTrue();
            }
            JLabel handle = named(form, "farmCombination.drag.CANNABIS_FARM",
                    JLabel.class);
            Rectangle handleBounds = SwingUtilities.convertRectangle(handle.getParent(),
                    handle.getBounds(), scroll.getViewport());
            assertThat(visible.contains(handleBounds))
                    .as("first handle %s in %s", handleBounds, visible).isTrue();
            JTextField search = named(form, "farmCombination.search", JTextField.class);
            Rectangle searchBounds = SwingUtilities.convertRectangle(search.getParent(),
                    search.getBounds(), scroll.getViewport());
            assertThat(visible.contains(searchBounds))
                    .as("search field %s in %s", searchBounds, visible).isTrue();
            BufferedImage image = new BufferedImage(scroll.getWidth(),
                    scroll.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            scroll.printAll(graphics);
            graphics.dispose();
            try {
                javax.imageio.ImageIO.write(image, "png", preview.toFile());
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }

            search.setText("marijuana");
            layoutTree(scroll);
            JPanel cards = named(form, "farmCombination.cards", JPanel.class);
            assertThat(cards.getComponentCount()).isEqualTo(1);
            assertThat(cards.getComponent(0).getName())
                    .isEqualTo("farmCombination.card.CANNABIS_FARM");
            Rectangle filteredHandle = SwingUtilities.convertRectangle(handle.getParent(),
                    handle.getBounds(), scroll.getViewport());
            assertThat(visible.contains(filteredHandle))
                    .as("filtered handle %s in %s", filteredHandle, visible).isTrue();
            BufferedImage filteredImage = new BufferedImage(scroll.getWidth(),
                    scroll.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D filteredGraphics = filteredImage.createGraphics();
            scroll.printAll(filteredGraphics);
            filteredGraphics.dispose();
            try {
                javax.imageio.ImageIO.write(filteredImage, "png", filteredPreview.toFile());
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void compactDialogKeepsDropBoardVisibleWhileLastFarmCardScrollsIntoView()
            throws Exception {
        Path preview = Path.of("target", "ui-previews", "combined-farm-compact-sticky.png");
        Files.createDirectories(preview.getParent());
        SwingUtilities.invokeAndWait(() -> {
            FlatLightLaf.setup();
            FarmUi.installThemeDefaults();
            JPanel owner = new JPanel();
            owner.setSize(1100, 700);
            Set<FarmWorkspace> saved = Set.of(FarmWorkspace.POTATO_FARM,
                    FarmWorkspace.CANNABIS_FARM);
            FarmCombinationDialog.Form form = new FarmCombinationDialog.Form(
                    FarmWorkspace.POTATO_FARM, saved, true);
            JScrollPane scroll = form.dialogScroll(owner);
            scroll.setSize(scroll.getPreferredSize());
            layoutTree(scroll);

            JScrollPane cardScroll = named(form, "farmCombination.cardScroll",
                    JScrollPane.class);
            JPanel cards = named(form, "farmCombination.cards", JPanel.class);
            assertThat(cards.getWidth()).isGreaterThan(750);
            JLabel pumpkins = named(form, "farmCombination.drag.PUMPKIN_PATCH",
                    JLabel.class);
            Rectangle pumpkinBefore = SwingUtilities.convertRectangle(
                    pumpkins.getParent(), pumpkins.getBounds(), cardScroll.getViewport());
            int scrollY = Math.max(0, pumpkinBefore.y + pumpkinBefore.height + 12
                    - cardScroll.getViewport().getHeight());
            cardScroll.getViewport().setViewPosition(new Point(0, scrollY));
            layoutTree(scroll);

            Rectangle cardViewport = new Rectangle(0, 0,
                    cardScroll.getViewport().getWidth(),
                    cardScroll.getViewport().getHeight());
            Rectangle pumpkinVisible = SwingUtilities.convertRectangle(
                    pumpkins.getParent(), pumpkins.getBounds(), cardScroll.getViewport());
            assertThat(cardViewport.contains(pumpkinVisible))
                    .as("Pumpkin %s in %s after scroll to %s", pumpkinVisible,
                            cardViewport, scrollY).isTrue();
            assertThat(scroll.getVerticalScrollBar().isVisible()).isFalse();
            Rectangle modalViewport = new Rectangle(0, 0,
                    scroll.getViewport().getWidth(), scroll.getViewport().getHeight());
            for (String target : Set.of("mixDrop", "mainDrop", "removeDrop")) {
                JPanel zone = named(form, "farmCombination." + target, JPanel.class);
                Rectangle bounds = SwingUtilities.convertRectangle(zone.getParent(),
                        zone.getBounds(), scroll.getViewport());
                assertThat(bounds.width).as(target).isGreaterThan(150);
                assertThat(bounds.height).as(target).isGreaterThan(35);
                assertThat(modalViewport.contains(bounds)).as(target).isTrue();
            }

            Transferable pumpkinCard = dragPayload(pumpkins);
            JPanel mix = named(form, "farmCombination.mixDrop", JPanel.class);
            JPanel main = named(form, "farmCombination.mainDrop", JPanel.class);
            JPanel remove = named(form, "farmCombination.removeDrop", JPanel.class);
            assertThat(drop(mix, pumpkinCard)).isTrue();
            assertThat(drop(main, pumpkinCard)).isTrue();
            assertThat(form.selection().primary()).isEqualTo(FarmWorkspace.PUMPKIN_PATCH);
            assertThat(drop(remove, pumpkinCard)).isTrue();
            assertThat(form.selection().included()).containsExactlyInAnyOrderElementsOf(saved);
            assertThat(saved).containsExactlyInAnyOrder(
                    FarmWorkspace.POTATO_FARM, FarmWorkspace.CANNABIS_FARM);

            BufferedImage image = new BufferedImage(scroll.getWidth(),
                    scroll.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            scroll.printAll(graphics);
            graphics.dispose();
            try {
                javax.imageio.ImageIO.write(image, "png", preview.toFile());
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static Transferable dragPayload(JComponent source) {
        Clipboard clipboard = new Clipboard("farm-card-test");
        assertThat(source.getTransferHandler().getSourceActions(source))
                .isEqualTo(TransferHandler.COPY);
        source.getTransferHandler().exportToClipboard(
                source, clipboard, TransferHandler.COPY);
        return clipboard.getContents(null);
    }

    private static boolean drop(JComponent target, Transferable payload) {
        return target.getTransferHandler().importData(
                new TransferHandler.TransferSupport(target, payload));
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container container) {
                layoutTree(container);
            }
        }
    }

    private static <T extends Component> T named(
            Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = namedOrNull(child, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        throw new AssertionError("Missing " + name);
    }

    private static <T extends Component> T namedOrNull(
            Component root, String name, Class<T> type) {
        if (type.isInstance(root) && name.equals(root.getName())) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = namedOrNull(child, name, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
