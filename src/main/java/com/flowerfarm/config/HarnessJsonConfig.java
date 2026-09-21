package com.flowerfarm.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.service.FieldAttachmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Configures attachment allowance and unambiguous JSON command envelopes for REST. */
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

    @Bean
    WebMvcConfigurer harnessCommandJson(ObjectMapper mapper) {
        ObjectMapper commandMapper = mapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .registerModule(new SimpleModule("harness-command-json")
                        .addDeserializer(Instant.class, new HarnessExpiryDeserializer(mapper)));
        commandMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return new WebMvcConfigurer() {
            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                for (HttpMessageConverter<?> converter : converters) {
                    if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                        jackson.registerObjectMappersForType(HarnessCommand.class, mappings -> {
                            mappings.put(MediaType.APPLICATION_JSON, commandMapper);
                            mappings.put(new MediaType("application", "*+json"), commandMapper);
                        });
                    }
                }
            }
        };
    }

    private static final class HarnessExpiryDeserializer extends JsonDeserializer<Instant> {
        private final ObjectReader reader;

        private HarnessExpiryDeserializer(ObjectMapper mapper) {
            // Retain the application's date-time modules while checking this wire field's type.
            reader = mapper.readerFor(Instant.class)
                    .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        }

        @Override
        public Instant deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return context.reportInputMismatch(Instant.class,
                        "expiresAt must be an ISO-8601 timestamp string");
            }
            try {
                Instant.parse(parser.getText().trim());
            } catch (DateTimeParseException exception) {
                throw JsonMappingException.from(parser,
                        "expiresAt must be an ISO-8601 timestamp string", exception);
            }
            Instant value = reader.readValue(parser);
            if (value == null) {
                return context.reportInputMismatch(Instant.class,
                        "expiresAt must be an ISO-8601 timestamp string");
            }
            return value;
        }
    }
}
