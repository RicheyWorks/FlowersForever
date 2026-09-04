package com.flowerfarm.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class HarnessJsonConfigTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(HarnessJsonConfig.class);

    @Test
    void enabledRestHarnessMapperAcceptsTheEntireAdvertisedAttachment() {
        context.withPropertyValues("flowerfarm.harness.enabled=true").run(application -> {
            ObjectMapper mapper = application.getBean(ObjectMapper.class);
            var constraints = mapper.getFactory().streamReadConstraints();
            assertThat(constraints.getMaxStringLength())
                    .isGreaterThanOrEqualTo(HarnessJsonConfig.MAX_ATTACHMENT_BASE64_CHARACTERS);
            assertThatNoException().isThrownBy(() -> constraints.validateStringLength(
                    HarnessJsonConfig.MAX_ATTACHMENT_BASE64_CHARACTERS));
        });
    }

    @Test
    void expandsSmallerCapsAndPreservesLargerConfiguredCaps() {
        for (int initialLimit : new int[]{8, 40 * 1024 * 1024}) {
            JsonFactory factory = JsonFactory.builder().streamReadConstraints(
                    StreamReadConstraints.builder().maxStringLength(initialLimit)
                            .maxNestingDepth(75).build()).build();
            Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder().factory(factory);
            new HarnessJsonConfig().harnessAttachmentJsonLimit().customize(builder);
            ObjectMapper mapper = builder.build();

            assertThat(mapper.getFactory().streamReadConstraints().getMaxStringLength())
                    .isEqualTo(Math.max(initialLimit, HarnessJsonConfig.MAX_ATTACHMENT_BASE64_CHARACTERS));
            assertThat(mapper.getFactory().streamReadConstraints().getMaxNestingDepth()).isEqualTo(75);
        }
    }

    @Test
    void leavesTheDefaultMapperAloneWhenTheHarnessIsDisabled() {
        context.withPropertyValues("flowerfarm.harness.enabled=false").run(application -> {
            assertThat(application).doesNotHaveBean(HarnessJsonConfig.class);
            assertThat(application.getBean(ObjectMapper.class).getFactory()
                    .streamReadConstraints().getMaxStringLength())
                    .isEqualTo(StreamReadConstraints.defaults().getMaxStringLength());
        });
    }
}
