package com.flowerfarm.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Runtime profile configuration")
class ProfileConfigurationTest {

    @Test
    @DisplayName("CLI disables the web server without changing the default database")
    void cliProfile() {
        withProfiles(new String[]{"cli"}, environment -> {
            assertThat(environment.getProperty("spring.main.web-application-type"))
                    .isEqualTo("none");
            assertThat(environment.getProperty("spring.datasource.url"))
                    .startsWith("jdbc:h2:file:");
        });
    }

    @Test
    @DisplayName("SQLite replaces only persistence settings")
    void sqliteProfile() {
        withProfiles(new String[]{"sqlite"}, environment -> {
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:sqlite:./data/flowerfarm.sqlite");
            assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size",
                    Integer.class)).isEqualTo(1);
            assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
        });
    }

    @Test
    @DisplayName("Auth composes with SQLite and enables external secured access")
    void authAndSqliteProfilesCompose() {
        withProfiles(new String[]{"auth", "sqlite"}, environment -> {
            assertThat(environment.getProperty("flowerfarm.auth.enabled", Boolean.class)).isTrue();
            assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
            assertThat(environment.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:sqlite:./data/flowerfarm.sqlite");
        });
    }

    private static void withProfiles(String[] profiles, EnvironmentAssertion assertion) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            new ConfigDataApplicationContextInitializer().initialize(context);
            context.refresh();
            assertion.accept(context.getEnvironment());
        }
    }

    @FunctionalInterface
    private interface EnvironmentAssertion {
        void accept(ConfigurableEnvironment environment);
    }
}
