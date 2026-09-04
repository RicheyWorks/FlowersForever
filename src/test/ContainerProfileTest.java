package com.flowerfarm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.headless=true",
                "spring.datasource.url=jdbc:h2:mem:container-profile;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "flowerfarm.inventory.seed-csv=target/no-container-profile-seed.csv",
                "flowerfarm.irrigation.live-weather=false",
                "flowerfarm.auth.users=owner:a-long-container-password:OWNER,"
                        + "hand:a-long-worker-password:HAND,"
                        + "viewer:a-long-viewer-password:VIEWER",
                "flowerfarm.harness.enabled=true",
                "flowerfarm.harness.token=container-harness-token-123456"
        })
@ActiveProfiles("container")
@DisplayName("Container profile")
class ContainerProfileTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    Environment environment;

    @Test
    @DisplayName("binds externally but requires configured HTTP Basic credentials")
    void externalBindIsAuthenticated() {
        assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
        assertThat(environment.getProperty("flowerfarm.auth.enabled", Boolean.class)).isTrue();

        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/api/inventory", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.withBasicAuth("owner", "a-long-container-password")
                .getForEntity("/api/inventory", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.withBasicAuth("owner", "a-long-container-password")
                .getForEntity("/api/harness/manifest", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("owner", "a-long-container-password");
        headers.set("X-FlowersForever-Harness-Token", "container-harness-token-123456");
        ResponseEntity<String> manifest = restTemplate.exchange(
                "/api/harness/manifest", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(manifest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manifest.getBody()).contains("farm-data__upsert-inventory");

        assertThat(restTemplate.getForEntity("/worker/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.withBasicAuth("viewer", "a-long-viewer-password")
                .getForEntity("/worker/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        HttpHeaders viewerHeaders = new HttpHeaders();
        viewerHeaders.setBasicAuth("viewer", "a-long-viewer-password");
        viewerHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> viewerWrite = restTemplate.exchange(
                "/api/worker/harvests", HttpMethod.POST,
                new HttpEntity<>("{}", viewerHeaders), String.class);
        assertThat(viewerWrite.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> viewerCount = restTemplate.exchange(
                "/api/worker/inventory/1/count", HttpMethod.POST,
                new HttpEntity<>("{}", viewerHeaders), String.class);
        assertThat(viewerCount.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> viewerHandoff = restTemplate.exchange(
                "/api/worker/orders/1/complete", HttpMethod.POST,
                new HttpEntity<>("{}", viewerHeaders), String.class);
        assertThat(viewerHandoff.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<String> viewerAttachment = restTemplate.exchange(
                "/api/worker/attachments", HttpMethod.POST,
                new HttpEntity<>("{}", viewerHeaders), String.class);
        assertThat(viewerAttachment.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        HttpHeaders handHeaders = new HttpHeaders();
        handHeaders.setBasicAuth("hand", "a-long-worker-password");
        handHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        ResponseEntity<String> handCount = restTemplate.exchange(
                "/api/worker/inventory/1/count", HttpMethod.POST,
                new HttpEntity<>("{}", handHeaders), String.class);
        assertThat(handCount.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<String> handHandoff = restTemplate.exchange(
                "/api/worker/orders/1/complete", HttpMethod.POST,
                new HttpEntity<>("{}", handHeaders), String.class);
        assertThat(handHandoff.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        HttpHeaders handMultipartHeaders = new HttpHeaders();
        handMultipartHeaders.setBasicAuth("hand", "a-long-worker-password");
        handMultipartHeaders.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> handAttachment = restTemplate.exchange(
                "/api/worker/attachments", HttpMethod.POST,
                new HttpEntity<>(new org.springframework.util.LinkedMultiValueMap<>(),
                        handMultipartHeaders), String.class);
        assertThat(handAttachment.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
