package com.flowerfarm.model;

import com.flowerfarm.config.FarmProfileConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FarmProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FarmProfileConfig.class);

    @Test
    void defaultsAreNeutralAndKeepProductAttributionQuiet() {
        FarmProfile profile = new FarmProfile();

        assertThat(profile.getName()).isEqualTo("Your Farm");
        assertThat(profile.locationLabel()).isEqualTo("LOCAL FARM");
        assertThat(profile.documentBanner("Price List"))
                .isEqualTo("Your Farm  ·  Price List");
        assertThat(profile.contactBlock()).isEqualTo("Your Farm");
        assertThat(profile.softwareFooter()).startsWith("Prepared with FlowersForever");
    }

    @Test
    void customIdentityFlowsIntoBuyerFacingCopy() {
        FarmProfile profile = new FarmProfile();
        profile.setName(" Evergreen & Bloom Farm ");
        profile.setLocation(" Port Orchard, WA ");
        profile.setPhone("360-555-0100");
        profile.setEmail("hello@example.com");
        profile.setWebsite("example.com");
        profile.setTimeZone("America/Los_Angeles");

        assertThat(profile.documentBanner("Wholesale Invoice"))
                .isEqualTo("Evergreen & Bloom Farm  ·  Port Orchard, WA"
                        + "  ·  Wholesale Invoice");
        assertThat(profile.contactBlock()).containsSubsequence(
                "Evergreen & Bloom Farm", "Port Orchard, WA", "360-555-0100",
                "hello@example.com", "example.com");
        assertThat(profile.thankYouLine())
                .isEqualTo("Thank you for supporting Evergreen & Bloom Farm.");
        assertThat(profile.zoneId()).isEqualTo(ZoneId.of("America/Los_Angeles"));
    }

    @Test
    void invalidTimeZoneFallsBackSafely() {
        FarmProfile profile = new FarmProfile();

        profile.setTimeZone("not/a-zone");

        assertThat(profile.zoneId()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void springAdapterBindsInstallationProperties() {
        contextRunner.withPropertyValues(
                        "flowerfarm.profile.name=Sunny Row Flowers",
                        "flowerfarm.profile.location=Tacoma, WA",
                        "flowerfarm.profile.tagline=Flowers for meaningful moments",
                        "flowerfarm.profile.time-zone=America/Los_Angeles")
                .run(context -> {
                    FarmProfile profile = context.getBean(FarmProfile.class);
                    assertThat(profile.getName()).isEqualTo("Sunny Row Flowers");
                    assertThat(profile.getLocation()).isEqualTo("Tacoma, WA");
                    assertThat(profile.getTagline())
                            .isEqualTo("Flowers for meaningful moments");
                    assertThat(profile.zoneId())
                            .isEqualTo(ZoneId.of("America/Los_Angeles"));
                });
    }
}
