package com.flowerfarm.gui.automation;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class SwingCaptureConsistencyTest {
    @Test
    void rejectsAnAttachmentReplacedWhileItsPixelsAreBeingCaptured() {
        assertCaptureRejectsReplacement(true);
    }

    @Test
    void rejectsAnActiveRootReplacedWhileItsPixelsAreBeingCaptured() {
        assertCaptureRejectsReplacement(false);
    }

    private static void assertCaptureRejectsReplacement(boolean replaceAttachment) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            CountDownLatch painting = new CountDownLatch(1);
            CountDownLatch releasePaint = new CountDownLatch(1);
            AtomicReference<JComponent> active = new AtomicReference<>();
            AtomicReference<JPanel> replacement = new AtomicReference<>();
            SwingHarnessBridge bridge = replaceAttachment ? new SwingHarnessBridge()
                    : new SwingHarnessBridge(ignored -> active.get());
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JPanel original = new JPanel() {
                        @Override protected void paintComponent(Graphics graphics) {
                            super.paintComponent(graphics);
                            painting.countDown();
                            try {
                                if (!releasePaint.await(3, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Test did not release the paint callback");
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }
                        }
                    };
                    original.setBackground(Color.RED);
                    original.setSize(8, 8);
                    JPanel next = new JPanel();
                    next.setBackground(Color.BLUE);
                    next.setSize(8, 8);
                    replacement.set(next);
                    active.set(original);
                    bridge.attach(original, title -> false);
                });
                // Start directly on the EDT so this probe isolates attachment/root
                // consistency rather than depending on the one-second read timeout.
                FutureTask<SwingHarnessBridge.ScreenCapture> capture = new FutureTask<>(bridge::captureScreen);
                SwingUtilities.invokeLater(capture);
                assertThat(painting.await(2, TimeUnit.SECONDS)).isTrue();
                if (replaceAttachment) {
                    bridge.attach(replacement.get(), title -> false);
                } else {
                    active.set(replacement.get());
                }
                releasePaint.countDown();
                assertThatThrownBy(() -> capture.get(2, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(DesktopContextChangedException.class);

                SwingHarnessBridge.ScreenCapture recovered = bridge.captureScreen();
                var pixels = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(recovered.data())));
                assertThat(pixels.getRGB(1, 1)).isEqualTo(Color.BLUE.getRGB());
            } finally {
                releasePaint.countDown();
                bridge.detach();
                SwingUtilities.invokeAndWait(() -> { });
            }
        });
    }
}
