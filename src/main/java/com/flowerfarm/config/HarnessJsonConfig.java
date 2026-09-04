package com.flowerfarm.config;

import com.flowerfarm.service.FieldAttachmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Keeps the optional REST harness's JSON allowance aligned with attachment uploads. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flowerfarm.harness.enabled", havingValue = "true")
@Profile("!cli & !mcp & !release-smoke")
public class HarnessJsonConfig {

    public static final int MAX_ATTACHMENT_BASE64_CHARACTERS =
            4 * ((FieldAttachmentService.MAX_ATTACHMENT_BYTES + 2) / 3);

    @Bean
    Jackson2ObjectMapperBuilderCustomizer harnessAttachmentJsonLimit() {
        return builder -> builder.postConfigurer(mapper -> {
            var factory = mapper.getFactory();
            var constraints = factory.streamReadConstraints();
            if (constraints.getMaxStringLength() < MAX_ATTACHMENT_BASE64_CHARACTERS) {
                factory.setStreamReadConstraints(constraints.rebuild()
                        .maxStringLength(MAX_ATTACHMENT_BASE64_CHARACTERS).build());
            }
        });
    }
}
