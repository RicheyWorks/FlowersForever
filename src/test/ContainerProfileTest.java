package com.flowerfarm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
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
                "flowerfarm.auth.users=owner:a-long-container-password:OWNER"
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
    }
}
