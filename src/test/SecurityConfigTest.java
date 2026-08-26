package com.flowerfarm.config;

import com.flowerfarm.auth.FarmUserDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("HTTP security configuration")
class SecurityConfigTest {

    private final List<AnnotationConfigWebApplicationContext> contexts = new ArrayList<>();

    @AfterEach
    void closeContexts() {
        contexts.forEach(AnnotationConfigWebApplicationContext::close);
        contexts.clear();
    }

    @Test
    @DisplayName("open mode permits reads and writes without credentials")
    void openModePermitsAnonymousRequests() throws Exception {
        MockMvc mvc = mockMvc(false);

        mvc.perform(get("/api/test")).andExpect(status().isOk());
        mvc.perform(post("/api/test")).andExpect(status().isOk());
        mvc.perform(delete("/api/connectors/history")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("secured mode enforces viewer, hand, and owner boundaries")
    void securedModeEnforcesRoles() throws Exception {
        MockMvc mvc = mockMvc(true);

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/accounts")).andExpect(status().isOk());
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/test").with(httpBasic("viewer", "view")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/test").with(httpBasic("viewer", "view")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/connectors/history").with(httpBasic("hand", "harvest")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/connectors/history").with(httpBasic("farm", "kitsap")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("external credential policy requires long passwords and an owner")
    void externalCredentialPolicyRejectsWeakDirectories() {
        FarmUserDirectory shortPassword = new FarmUserDirectory(
                "owner:too-short:OWNER", "unused", "unused");
        FarmUserDirectory noOwner = new FarmUserDirectory(
                "hand:a-long-hand-password:HAND", "unused", "unused");

        assertThatIllegalStateException()
                .isThrownBy(() -> SecurityConfig.validateCredentialPolicy(shortPassword, false))
                .withMessageContaining("12 characters")
                .withMessageNotContaining("too-short");
        assertThatIllegalStateException()
                .isThrownBy(() -> SecurityConfig.validateCredentialPolicy(noOwner, false))
                .withMessageContaining("OWNER");
    }

    @Test
    @DisplayName("external credential policy accepts a strong owner directory")
    void externalCredentialPolicyAcceptsStrongOwner() {
        FarmUserDirectory directory = new FarmUserDirectory(
                "owner:a-long-owner-password:OWNER", "unused", "unused");

        assertThatCode(() -> SecurityConfig.validateCredentialPolicy(directory, false))
                .doesNotThrowAnyException();
    }

    private MockMvc mockMvc(boolean authEnabled) {
        AnnotationConfigWebApplicationContext context =
                new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "flowerfarm.auth.enabled=" + authEnabled,
                "flowerfarm.auth.allow-demo-credentials=true",
                "flowerfarm.auth.users=farm:kitsap:OWNER,hand:harvest:HAND,viewer:view:VIEWER");
        context.register(TestWebConfig.class);
        context.refresh();
        contexts.add(context);
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, FarmUserDirectory.class, TestEndpoints.class})
    static class TestWebConfig {
    }

    @RestController
    static class TestEndpoints {

        @GetMapping({"/api/test", "/actuator/health", "/api/auth/accounts"})
        Map<String, Boolean> read() {
            return Map.of("ok", true);
        }

        @PostMapping("/api/test")
        Map<String, Boolean> write() {
            return Map.of("ok", true);
        }

        @DeleteMapping("/api/connectors/history")
        Map<String, Boolean> clearHistory() {
            return Map.of("ok", true);
        }
    }
}
