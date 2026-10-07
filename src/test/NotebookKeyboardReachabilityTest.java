package com.flowerfarm.gui.tabs;

import com.flowerfarm.gui.FarmDialogs;
import com.flowerfarm.gui.FarmUi;
import com.flowerfarm.model.FarmWorkspace;
import com.formdev.flatlaf.FlatLightLaf;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

/** Focus must reveal form controls below the list without moving passive drafts. */
class NotebookKeyboardReachabilityTest {

    static Stream<Class<? extends FlowerFarmTab>> notebooks() {
        return Stream.of(AquaticCohortTab.class, BerryBlockPlanTab.class,
                CannabisCultivationTab.class, ChristmasTreeBlockTab.class,
                CranberryBedTab.class, CropAreaPlanTab.class, EggCollectionTab.class,
                GrainFieldPlanTab.class, HayCuttingTab.class, HerdGroupTab.class,
                HiveCheckTab.class, HopYardPlanTab.class, MilkCollectionTab.class,
                MushroomBatchTab.class, NurseryGrowingBatchTab.class, NutBlockPlanTab.class,
                PumpkinPatchTab.class, SeedFieldPlanTab.class, VegetablePlantingTab.class,
                VineyardBlockPlanTab.class);
    }

    @ParameterizedTest(name = "{0}: compact keyboard entry and save remain visible")
    @MethodSource("notebooks")
    void focusedInputsAndSaveBecomeVisible(Class<? extends FlowerFarmTab> type) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture(type);
            List<JComponent> targets = new ArrayList<>();
            targets.add((JComponent) FarmUi.focusFirstInteractive(fixture.root()));
            targets.addAll(descendants(fixture.root(), JComboBox.class));
            JButton save = descendants(fixture.root(), JButton.class).stream()
                    .filter(button -> button.getName() != null && button.getName().endsWith(".save"))
                    .findFirst().orElseThrow();
            targets.add(save);
            SoftAssertions softly = new SoftAssertions();
            int hiddenTargets = 0;
            for (JComponent target : targets) {
                Rectangle bounds = SwingUtilities.convertRectangle(target.getParent(),
                        target.getBounds(), fixture.scroll().getViewport().getView());
                int bottom = Math.max(0, fixture.scroll().getViewport().getViewSize().height
                        - fixture.scroll().getViewport().getExtentSize().height);
                fixture.scroll().getViewport().setViewPosition(new Point(0,
                        bounds.y < fixture.scroll().getViewport().getExtentSize().height
                                ? bottom : 0));
                if (!fixture.scroll().getViewport().getViewRect().contains(bounds)) {
                    hiddenTargets++;
                }
                FocusEvent event = new FocusEvent(target, FocusEvent.FOCUS_GAINED);
                for (FocusListener listener : target.getFocusListeners()) {
                    listener.focusGained(event);
                }
                softly.assertThat(fixture.scroll().getViewport().getViewRect().contains(bounds))
                        .as("%s focus reveals %s at %s inside %s", type.getSimpleName(),
                                target.getAccessibleContext().getAccessibleName(), bounds,
                                fixture.scroll().getViewport().getViewRect())
                        .isTrue();
            }
            savePreview(fixture.root(), type.getSimpleName());
            softly.assertThat(hiddenTargets).as("Exercise real offscreen controls").isPositive();
            softly.assertAll();
        });
    }

    @ParameterizedTest(name = "{0}: a passive refresh preserves draft and scroll position")
    @MethodSource("notebooks")
    void backgroundRefreshDoesNotScrollAwayFromTheCurrentView(Class<? extends FlowerFarmTab> type)
            throws Exception {
        AtomicReference<Fixture> holder = new AtomicReference<>();
        AtomicReference<Point> position = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = fixture(type);
            holder.set(fixture);
            JTextArea notes = descendants(fixture.root(), JTextArea.class).get(0);
            notes.setText("Retained planning note while other records refresh.");
            fixture.scroll().getViewport().setViewPosition(new Point(0, 80));
            position.set(fixture.scroll().getViewport().getViewPosition());
            fixture.tab().refreshData();
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            AtomicReference<Boolean> loading = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> loading.set(Boolean.TRUE.equals(
                    ReflectionTestUtils.getField(holder.get().tab(), "loading"))));
            return !loading.get();
        });
        SwingUtilities.invokeAndWait(() -> {
            Fixture fixture = holder.get();
            layoutTree(fixture.root());
            assertThat(fixture.scroll().getViewport().getViewPosition()).isEqualTo(position.get());
            assertThat(descendants(fixture.root(), JTextArea.class).get(0).getText())
                    .isEqualTo("Retained planning note while other records refresh.");
        });
    }

    private static Fixture fixture(Class<? extends FlowerFarmTab> type) {
        FlatLightLaf.setup();
        FarmUi.installThemeDefaults();
        try {
            Constructor<?> constructor = Arrays.stream(type.getConstructors())
                    .filter(candidate -> Arrays.asList(candidate.getParameterTypes())
                            .contains(FarmDialogs.class)).findFirst().orElseThrow();
            Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                    .map(parameter -> parameter == FarmWorkspace.class
                            ? FarmWorkspace.POTATO_FARM : mock(parameter)).toArray();
            FlowerFarmTab tab = (FlowerFarmTab) constructor.newInstance(arguments);
            JComponent root = tab.getUIComponent();
            root.setSize(850, 600);
            for (int pass = 0; pass < 6; pass++) {
                layoutTree(root);
            }
            JScrollPane scroll = descendants(root, JScrollPane.class).stream()
                    .filter(candidate -> candidate.getName() != null
                            && candidate.getName().endsWith(".scroll"))
                    .findFirst().orElseThrow();
            return new Fixture(tab, root, scroll);
        } catch (ReflectiveOperationException exception) {
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

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(root)) {
            result.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                result.addAll(descendants(child, type));
            }
        }
        return result;
    }

    private static void savePreview(JComponent root, String name) {
        Path output = Path.of("target", "ui-previews", "notebook-keyboard", name + ".png");
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        root.printAll(graphics);
        graphics.dispose();
        try {
            Files.createDirectories(output.getParent());
            ImageIO.write(image, "png", output.toFile());
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private record Fixture(FlowerFarmTab tab, JComponent root, JScrollPane scroll) { }
}
