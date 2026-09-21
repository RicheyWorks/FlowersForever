package com.flowerfarm.gui.automation;

import com.flowerfarm.automation.HarnessCommand;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.DocumentFilter;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SwingTextEditingTest {
    @Test
    void failsWhenAnEditListenerReplacesTheVisibleDocumentAndReplaysWithoutEditingAgain() throws Exception {
        AtomicInteger replacements = new AtomicInteger();
        JTextArea draft = onEdt(() -> {
            JTextArea area = new JTextArea("retained");
            area.setName("draft");
            area.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) {
                    replacements.incrementAndGet();
                    area.setDocument(new JTextArea("refreshed draft").getDocument());
                }
                @Override public void removeUpdate(DocumentEvent event) { }
                @Override public void changedUpdate(DocumentEvent event) { }
            });
            return area;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(draft, title -> false);
        try {
            SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
            HarnessCommand replaced = new HarnessCommand("replaced-document", "edit-text", Map.of(
                    "selector", "draft", "offset", 0, "length", 8, "value", "changed"));
            var receipt = plugin.execute(replaced);
            onEdt(() -> null);
            assertThat(bridge.actionStatus(replaced.requestId()).output())
                    .containsEntry("status", "FAILED")
                    .containsEntry("failureType", "IllegalStateException");
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("refreshed draft");
            assertThat(plugin.execute(replaced)).isEqualTo(receipt);
            assertThat(replacements).hasValue(1);
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("refreshed draft");

            plugin.execute(new HarnessCommand("edit-current-document", "edit-text", Map.of(
                    "selector", "draft", "offset", 0, "length", 0, "value", "Recovered: ")));
            onEdt(() -> null);
            assertThat(bridge.actionStatus("edit-current-document").output())
                    .containsEntry("status", "COMPLETED");
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText))
                    .isEqualTo("Recovered: refreshed draft");
            assertThat(replacements).hasValue(1);
        } finally {
            bridge.detach();
        }
    }

    @Test
    void buildsAndReadsLongDraftsThroughBoundedSegmentsAndReplaysOnce() throws Exception {
        JTextArea draft = onEdt(() -> new JTextArea("A".repeat(8000)));
        draft.setName("brief");
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(draft, title -> false);
        try {
            SwingHarnessPlugin plugin = new SwingHarnessPlugin(bridge);
            String token = (String) plugin.observe().metadata().get("contextToken");
            HarnessCommand append = new HarnessCommand("append-brief", "edit-text", Map.of(
                    "selector", "brief", "offset", 8000, "length", 0,
                    "value", "B".repeat(8000), "expected-context", token));
            var receipt = plugin.execute(append);
            onEdt(() -> null);
            assertThat(plugin.execute(append)).isEqualTo(receipt);
            assertThat(bridge.actionStatus("append-brief").output()).containsEntry("status", "COMPLETED");
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("A".repeat(8000) + "B".repeat(8000));
            var tail = plugin.execute(new HarnessCommand("read-tail", "read-control",
                    Map.of("selector", "brief", "offset", 8000))).output();
            assertThat(tail).containsEntry("value", "B".repeat(8000)).containsEntry("hasMore", false);
            bridge.editText("brief", 7999, 2, "\uD83C\uDF39");
            assertThat(onEdt(() -> draft.getText().substring(7998, 8002))).isEqualTo("A\uD83C\uDF39B");
            bridge.editText("brief", 8001, 7999, "");
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("A".repeat(7999) + "\uD83C\uDF39");
        } finally {
            bridge.detach();
        }
    }

    @Test
    void rejectsInvalidRangesAndOversizedChunksBeforeChangingTheDocument() throws Exception {
        JTextArea draft = onEdt(() -> new JTextArea("keep this draft"));
        draft.setName("draft");
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(draft, title -> false);
        try {
            for (int[] range : new int[][]{{-1, 0}, {0, -1}, {16, 0}, {3, 100}, {1, Integer.MAX_VALUE}}) {
                assertThatThrownBy(() -> bridge.editText("draft", range[0], range[1], "new"))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("keep this draft");
            }
            assertThatThrownBy(() -> bridge.editText("draft", 0, 0, "x".repeat(8001)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("keep this draft");
        } finally {
            bridge.detach();
        }
    }

    @Test
    void preservesPasswordDisabledHiddenAndReadonlyBoundaries() throws Exception {
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        JPanel root = onEdt(() -> {
            JPanel panel = new JPanel();
            JPasswordField password = new JPasswordField("secret");
            password.setName("password");
            panel.add(password);
            JTextArea readonly = new JTextArea("readonly");
            readonly.setName("readonly");
            readonly.setEditable(false);
            panel.add(readonly);
            JTextArea disabled = new JTextArea("disabled");
            disabled.setName("disabled");
            disabled.setEnabled(false);
            panel.add(disabled);
            JPanel hidden = new JPanel();
            JTextArea hiddenDraft = new JTextArea("hidden");
            hiddenDraft.setName("hidden");
            hidden.add(hiddenDraft);
            hidden.setVisible(false);
            panel.add(hidden);
            return panel;
        });
        bridge.attach(root, title -> false);
        try {
            for (String selector : new String[]{"password", "readonly", "disabled", "hidden"}) {
                assertThatThrownBy(() -> bridge.editText(selector, 0, 1, "changed"))
                        .isInstanceOf(IllegalArgumentException.class);
            }
        } finally {
            bridge.detach();
        }
    }

    @Test
    void reportsDocumentFilterRefusalsInsteadOfClaimingAnEditSucceeded() throws Exception {
        JTextArea draft = onEdt(() -> {
            JTextArea area = new JTextArea("retained");
            area.setName("draft");
            ((AbstractDocument) area.getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void replace(FilterBypass bypass, int offset, int length,
                                    String text, AttributeSet attributes) {
                    // Production validation can refuse a proposed edit without throwing.
                }
            });
            return area;
        });
        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(draft, title -> false);
        try {
            assertThatThrownBy(() -> bridge.editText("draft", 0, 8, "changed"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> bridge.replaceText("draft", "changed"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(SwingTextEditingTest.<String>onEdt(draft::getText)).isEqualTo("retained");
        } finally {
            bridge.detach();
        }
    }

    private static <T> T onEdt(Callable<T> task) throws Exception {
        FutureTask<T> pending = new FutureTask<>(task);
        SwingUtilities.invokeAndWait(pending);
        return pending.get();
    }
}
