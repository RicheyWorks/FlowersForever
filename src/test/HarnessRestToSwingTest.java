package com.flowerfarm.controller;

import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessPlugin;
import com.flowerfarm.automation.HarnessProperties;
import com.flowerfarm.automation.HarnessRegistry;
import com.flowerfarm.gui.automation.SwingHarnessBridge;
import com.flowerfarm.gui.automation.SwingHarnessPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("JSON-to-live-Swing harness wire")
class HarnessRestToSwingTest {

    private static final String TOKEN = "end-to-end-harness-token-123456";

    @Test
    @DisplayName("generates tools, edits a draft, and replays one real button click")
    void drivesLiveSwingThroughTheCompleteRestGateway() throws Exception {
        JTextField draft = new JTextField("private-before-value");
        draft.getAccessibleContext().setAccessibleName("Crop draft");
        AtomicInteger submits = new AtomicInteger();
        draft.addActionListener(event -> submits.incrementAndGet());
        AtomicInteger clicks = new AtomicInteger();
        JButton apply = new JButton("Apply draft");
        apply.addActionListener(event -> clicks.incrementAndGet());
        JPanel content = new JPanel();
        content.add(draft);
        content.add(apply);
        JTable table = new JTable(new DefaultTableModel(
                new Object[][]{{"Camas"}, {"Nootka Rose"}}, new Object[]{"Plant"}));
        table.setName("plant.table");
        AtomicInteger doubleClicks = new AtomicInteger();
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    doubleClicks.incrementAndGet();
                }
            }
        });
        content.add(new JScrollPane(table));
        JTabbedPane tabs = new JTabbedPane();
        tabs.getAccessibleContext().setAccessibleName("Workspaces");
        tabs.addTab("Reference", content);
        JPanel contentRoot = new JPanel(new BorderLayout());
        contentRoot.add(tabs);
        AtomicInteger menus = new AtomicInteger();
        JMenuItem refresh = new JMenuItem("Refresh farm");
        refresh.addActionListener(event -> menus.incrementAndGet());
        JMenu view = new JMenu("View");
        view.add(refresh);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(view);
        JRootPane root = new JRootPane();
        root.setContentPane(contentRoot);
        root.setJMenuBar(menuBar);
        root.setSize(640, 480);
        root.doLayout();
        AtomicInteger shortcuts = new AtomicInteger();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "refresh-farm");
        root.getActionMap().put("refresh-farm", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                shortcuts.incrementAndGet();
            }
        });

        SwingHarnessBridge bridge = new SwingHarnessBridge();
        bridge.attach(root, title -> "Reference".equals(title));
        HarnessPlugin plugin = new SwingHarnessPlugin(bridge);
        HarnessProperties properties = enabledProperties();
        HarnessGateway gateway = new HarnessGateway(
                new HarnessRegistry(List.of(plugin)), properties);
        MockMvc mvc = standaloneSetup(new HarnessController(gateway)).build();

        mvc.perform(get("/api/harness/manifest").header(
                        HarnessController.TOKEN_HEADER, TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.DRAFT").value(true))
                .andExpect(jsonPath("$.policy.SENSITIVE_READ").value(true))
                .andExpect(jsonPath("$.policy.DESTRUCTIVE").value(true))
                .andExpect(jsonPath("$.replayCacheByteLimit").value(8388608))
                .andExpect(jsonPath("$.tools.length()").value(21))
                .andExpect(jsonPath("$.tools[1].name")
                        .value("swing-desktop__replace-text"))
                .andExpect(jsonPath("$.tools[1].inputSchema.properties.value.type")
                        .value("string"))
                .andExpect(jsonPath("$.tools[1].inputSchema.required.length()").value(2))
                .andExpect(jsonPath("$.tools[5].inputSchema.properties.rows.items.type")
                        .value("integer"))
                .andExpect(jsonPath("$.tools[13].inputSchema.properties.path.items.type")
                        .value("string"))
                .andExpect(jsonPath("$.tools[17].name")
                        .value("swing-desktop__navigate-route"))
                .andExpect(jsonPath("$.tools[17].inputSchema.properties.path.items.type")
                        .value("string"))
                .andExpect(jsonPath("$.tools[18].name")
                        .value("swing-desktop__action-status"));

        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-route","action":"navigate-route",
                                 "arguments":{"path":["Reference"]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.route[0]").value("Reference"))
                .andExpect(jsonPath("$.snapshot.metadata.routeCount").value(1));

        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-draft","action":"replace-text",
                                 "arguments":{"selector":"Crop draft","value":"Nootka Rose"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.snapshot.metadata.fieldValuesIncluded").value(false))
                .andExpect(content().string(not(containsString("private-before-value"))));
        assertThat(draft.getText()).isEqualTo("Nootka Rose");

        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-read","action":"read-control",
                                 "arguments":{"selector":"Crop draft"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output.kind").value("text"))
                .andExpect(jsonPath("$.output.value").value("Nootka Rose"));
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-screen","action":"capture-screen",
                                 "arguments":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.output.mediaType").value("image/png"))
                .andExpect(jsonPath("$.output.encoding").value("base64"))
                .andExpect(jsonPath("$.output.width").value(640))
                .andExpect(jsonPath("$.output.height").value(480))
                .andExpect(jsonPath("$.output.sha256").isString());

        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-submit","action":"submit-field",
                                 "arguments":{"selector":"Crop draft"}}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-double","action":"double-click-row",
                                 "arguments":{"table":"plant.table","row":1}}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-menu","action":"activate-menu",
                                 "arguments":{"path":["View","Refresh farm"]}}
                                """))
                .andExpect(status().isOk());
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"wire-shortcut","action":"invoke-shortcut",
                                 "arguments":{"shortcut":"F5"}}
                                """))
                .andExpect(status().isOk());
        assertThat(submits).hasValue(1);
        assertThat(doubleClicks).hasValue(1);
        assertThat(menus).hasValue(1);
        assertThat(shortcuts).hasValue(1);

        String clickCommand = """
                {"requestId":"wire-button","action":"activate-button",
                 "arguments":{"selector":"Apply draft"}}
                """;
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clickCommand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false));
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clickCommand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));
        assertThat(clicks).hasValue(1);
    }

    private static HarnessProperties enabledProperties() {
        HarnessProperties properties = new HarnessProperties();
        properties.setEnabled(true);
        properties.setToken(TOKEN);
        properties.setAllowSensitiveRead(true);
        properties.setAllowDrafts(true);
        properties.setAllowMutations(true);
        properties.setAllowDestructive(true);
        properties.validateConfiguration();
        return properties;
    }
}
