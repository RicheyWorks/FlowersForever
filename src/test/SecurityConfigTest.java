package com.flowerfarm.config;

import com.flowerfarm.auth.FarmUserDirectory;
import com.flowerfarm.controller.AuthController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    @DisplayName("open mode permits reads and token-authorized writes without credentials")
    void openModePermitsAnonymousRequests() throws Exception {
        MockMvc mvc = mockMvc(false);

        mvc.perform(get("/api/test")).andExpect(status().isOk());
        WriteToken csrf = writeToken(mvc);
        mvc.perform(csrf.apply(post("/api/test"))).andExpect(status().isOk());
        mvc.perform(csrf.apply(delete("/api/connectors/history"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("secured mode enforces viewer, hand, and owner boundaries")
    void securedModeEnforcesRoles() throws Exception {
        MockMvc mvc = mockMvc(true);

        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/accounts")).andExpect(status().isOk());
        mvc.perform(get("/api/test")).andExpect(status().isUnauthorized());
        WriteToken csrf = writeToken(mvc);

        mvc.perform(get("/api/test").with(httpBasic("viewer", "view")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/knowledge").with(httpBasic("viewer", "view")))
                .andExpect(status().isOk());
        mvc.perform(csrf.apply(post("/api/test").with(httpBasic("viewer", "view"))))
                .andExpect(status().isForbidden());
        mvc.perform(csrf.apply(post("/api/knowledge").with(httpBasic("viewer", "view"))))
                .andExpect(status().isForbidden());

        mvc.perform(csrf.apply(post("/api/test").with(httpBasic("hand", "harvest"))))
                .andExpect(status().isOk());
        mvc.perform(csrf.apply(post("/api/knowledge").with(httpBasic("hand", "harvest"))))
                .andExpect(status().isOk());
        mvc.perform(csrf.apply(delete("/api/connectors/history").with(httpBasic("hand", "harvest"))))
                .andExpect(status().isForbidden());

        mvc.perform(csrf.apply(delete("/api/connectors/history").with(httpBasic("farm", "kitsap"))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("both modes require the returned masked token and its matching cookie")
    void csrfHandshakeProtectsWrites(boolean authEnabled) throws Exception {
        MockMvc mvc = mockMvc(authEnabled);
        WriteToken csrf = writeToken(mvc);
        WriteToken differentBrowser = writeToken(mvc);

        mvc.perform(csrf.apply(post("/api/test")
                        .with(httpBasic("hand", "harvest"))
                        .contentType("application/json").content("{}")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest")).cookie(csrf.cookie()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest"))
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest"))
                        .cookie(csrf.cookie()).header(csrf.headerName(), "invalid-token"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest"))
                        .cookie(differentBrowser.cookie()).header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest"))
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.cookie().getValue()))
                .andExpect(status().isForbidden());

        // A hostile site can submit a form with the browser's cached Basic
        // credentials, but cannot obtain the same-origin masked header token.
        mvc.perform(post("/api/test").with(httpBasic("hand", "harvest"))
                        .cookie(csrf.cookie()).header("Origin", "https://evil.invalid")
                        .contentType("application/x-www-form-urlencoded").content("confirm=true"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("multipart writes use the same handshake and reject cross-site uploads")
    void csrfHandshakeProtectsMultipart(boolean authEnabled) throws Exception {
        MockMvc mvc = mockMvc(authEnabled);
        WriteToken csrf = writeToken(mvc);
        MockMultipartFile photo = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

        mvc.perform(csrf.apply(multipart("/api/test/upload").file(photo)
                        .with(httpBasic("hand", "harvest"))))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/test/upload").file(photo)
                        .with(httpBasic("hand", "harvest"))
                        .cookie(csrf.cookie()).header("Origin", "https://evil.invalid"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("HTTPS token cookies are secure and repeated handshakes retain BREACH masking")
    void httpsHandshakeRetainsCookieAndRefreshesMask(boolean authEnabled) throws Exception {
        MockMvc mvc = mockMvc(authEnabled);
        WriteToken first = writeToken(mvc, true);
        MvcResult refresh = mvc.perform(get("/api/auth/csrf").secure(true).cookie(first.cookie()))
                .andExpect(status().isOk()).andReturn();
        assertThat(refresh.getResponse().getHeader("Cache-Control")).contains("no-store");
        JsonNode body = new ObjectMapper().readTree(refresh.getResponse().getContentAsString());
        String freshMask = body.path("token").asText();
        assertThat(freshMask).isNotBlank().isNotEqualTo(first.token())
                .isNotEqualTo(first.cookie().getValue());
        mvc.perform(post("/api/test").secure(true).with(httpBasic("hand", "harvest"))
                        .cookie(first.cookie()).header(first.headerName(), freshMask))
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

    private WriteToken writeToken(MockMvc mvc) throws Exception {
        return writeToken(mvc, false);
    }

    private WriteToken writeToken(MockMvc mvc, boolean secure) throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf").secure(secure))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        String cookieHeader = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(header -> header.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow();
        assertThat(cookieHeader).contains("HttpOnly", "SameSite=Strict", "Path=/")
                .doesNotContain("Domain=");
        if (secure) {
            assertThat(cookieHeader).contains("Secure");
        }
        String cookieValue = cookieHeader.substring("XSRF-TOKEN=".length(), cookieHeader.indexOf(';'));
        JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        String headerName = body.path("headerName").asText();
        String token = body.path("token").asText();
        assertThat(headerName).isEqualTo("X-XSRF-TOKEN");
        assertThat(token).isNotBlank().isNotEqualTo(cookieValue);
        assertThat(result.getRequest().getSession(false)).isNull();
        Cookie cookie = new Cookie("XSRF-TOKEN", cookieValue);
        cookie.setSecure(secure);
        return new WriteToken(headerName, token, cookie);
    }

    private record WriteToken(String headerName, String token, Cookie cookie) {
        MockHttpServletRequestBuilder apply(MockHttpServletRequestBuilder request) {
            return request.cookie(cookie).header(headerName, token);
        }
    }

    @Configuration
    @EnableWebMvc
    @Import({SecurityConfig.class, FarmUserDirectory.class, AuthController.class, TestEndpoints.class})
    static class TestWebConfig {
    }

    @RestController
    static class TestEndpoints {

        @GetMapping({"/api/test", "/api/knowledge", "/actuator/health"})
        Map<String, Boolean> read() {
            return Map.of("ok", true);
        }

        @PostMapping({"/api/test", "/api/knowledge"})
        Map<String, Boolean> write() {
            return Map.of("ok", true);
        }

        @PostMapping(value = "/api/test/upload", consumes = "multipart/form-data")
        Map<String, Boolean> upload() {
            return Map.of("ok", true);
        }

        @DeleteMapping("/api/connectors/history")
        Map<String, Boolean> clearHistory() {
            return Map.of("ok", true);
        }
    }
}
