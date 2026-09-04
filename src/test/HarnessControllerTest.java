package com.flowerfarm.controller;

import com.flowerfarm.automation.HarnessAuthenticationException;
import com.flowerfarm.automation.HarnessActionSpec;
import com.flowerfarm.automation.HarnessArgumentSpec;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessRisk;
import com.flowerfarm.automation.HarnessToolDefinition;
import com.flowerfarm.automation.HarnessValueType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("Automation harness JSON transport")
class HarnessControllerTest {

    @Test
    @DisplayName("forwards the separate harness token and publishes plugin discovery")
    void discoversPlugins() throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        HarnessPluginDescriptor plugin = new HarnessPluginDescriptor(
                "swing-desktop", "Desktop", "Swing", "1.0", List.of(
                new HarnessActionSpec("navigate", "Open tab", HarnessRisk.NAVIGATE,
                        List.of(new HarnessArgumentSpec("tab", HarnessValueType.STRING,
                                "Visible tab title.", true)))));
        when(gateway.discover("local-token")).thenReturn(List.of(plugin));
        HarnessManifest manifest = new HarnessManifest("1.0", 256, true,
                Map.of(HarnessRisk.READ, true, HarnessRisk.NAVIGATE, true),
                List.of(plugin), List.of(HarnessToolDefinition.from(
                plugin, plugin.actions().get(0), true)));
        when(gateway.manifest("local-token")).thenReturn(manifest);
        MockMvc mvc = standaloneSetup(new HarnessController(gateway)).build();

        mvc.perform(get("/api/harness")
                        .header(HarnessController.TOKEN_HEADER, "local-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("swing-desktop"))
                .andExpect(jsonPath("$[0].version").value("1.0"))
                .andExpect(jsonPath("$[0].kind").value("APPLICATION"))
                .andExpect(jsonPath("$[0].capabilities[0]").exists())
                .andExpect(jsonPath("$[0].actions[0].arguments[0].name").value("tab"))
                .andExpect(jsonPath("$[0].actions[0].arguments[0].type").value("string"))
                .andExpect(jsonPath("$[0].actions[0].arguments[0].required").value(true));

        mvc.perform(get("/api/harness/manifest")
                        .header(HarnessController.TOKEN_HEADER, "local-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolVersion").value("1.0"))
                .andExpect(jsonPath("$.replayCacheByteLimit").value(8388608))
                .andExpect(jsonPath("$.strictArguments").value(true))
                .andExpect(jsonPath("$.physicalCommandMaxTtlSeconds").value(30))
                .andExpect(jsonPath("$.policy.NAVIGATE").value(true))
                .andExpect(jsonPath("$.tools[0].name").value("swing-desktop__navigate"))
                .andExpect(jsonPath("$.tools[0].annotations.idempotentHint").value(true))
                .andExpect(jsonPath("$.tools[0].annotations.physicalWorldHint")
                        .value(false))
                .andExpect(jsonPath("$.tools[0].inputSchema.type").value("object"))
                .andExpect(jsonPath(
                        "$.tools[0].inputSchema.additionalProperties").value(false));
    }

    @Test
    @DisplayName("maps typed JSON commands to the provider-neutral gateway")
    void executesTypedCommand() throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(gateway.execute(eq("local-token"), eq("swing-desktop"), any()))
                .thenReturn(new HarnessResponse("1.0", "ai-1", "swing-desktop",
                        true, false, "Opened Inventory", null, Map.of("tab", "Inventory")));
        MockMvc mvc = standaloneSetup(new HarnessController(gateway)).build();

        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, "local-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"ai-1","action":"navigate",
                                 "arguments":{"tab":"Inventory"},
                                 "expiresAt":"2026-09-01T18:45:00Z"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protocolVersion").value("1.0"))
                .andExpect(jsonPath("$.requestId").value("ai-1"))
                .andExpect(jsonPath("$.success").value(true));

        verify(gateway).execute(eq("local-token"), eq("swing-desktop"),
                eq(new HarnessCommand("ai-1", "navigate", Map.of("tab", "Inventory"),
                        Instant.parse("2026-09-01T18:45:00Z"))));
    }

    @Test
    @DisplayName("returns explicit HTTP boundaries for token and policy failures")
    void mapsAccessFailures() throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(gateway.discover(null)).thenThrow(
                new HarnessAuthenticationException("Invalid automation harness token"));
        when(gateway.execute(eq("local-token"), eq("swing-desktop"), any()))
                .thenThrow(new HarnessPolicyException("DRAFT disabled"));
        MockMvc mvc = standaloneSetup(new HarnessController(gateway)).build();

        mvc.perform(get("/api/harness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid automation harness token"));
        mvc.perform(post("/api/harness/swing-desktop/commands")
                        .header(HarnessController.TOKEN_HEADER, "local-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"ai-2","action":"replace-text",
                                 "arguments":{"selector":"Crop","value":"Rose"}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("DRAFT disabled"));
    }
}
