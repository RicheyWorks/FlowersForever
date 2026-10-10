package com.flowerfarm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.controller.ApiExceptionHandler;
import com.flowerfarm.controller.HarnessController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HarnessCommandJsonBoundaryTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"requestId\":\"first\",\"requestId\":\"second\",\"action\":\"catalog\"}",
            "{\"requestId\":\"id\",\"action\":\"catalog\"} {}",
            "{\"requestId\":\"id\",\"action\":\"catalog\"} trailing",
            "{\"requestId\":123,\"action\":\"catalog\"}",
            "{\"requestId\":true,\"action\":\"catalog\"}",
            "{\"requestId\":\"id\",\"action\":123}",
            "{\"requestId\":\"id\",\"action\":\"catalog\",\"expiresAt\":42}",
            "{\"requestId\":\"id\",\"action\":\"catalog\",\"expiresAt\":42.5}",
            "{\"requestId\":\"id\",\"action\":\"catalog\",\"expiresAt\":\"42\"}",
            "{\"requestId\":\"id\",\"action\":\"catalog\",\"expiresAt\":\"\"}",
            "{\"requestId\":\"id\",\"action\":\"catalog\",\"expiresAt\":\"tomorrow\"}",
            "{\"requestId\":\"id\",\"action\":\"catalog\",\"argumentz\":{\"limit\":10}}",
            "{\"requestId\":\"id\",\"action\":\"catalog\","
                    + "\"arguments\":{\"limit\":10,\"limit\":20}}"
    })
    void rejectsAmbiguousCommandsBeforeGatewayExecution(String json) throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        mvc(gateway).perform(post("/api/harness/farm-data/commands")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"));
        verifyNoInteractions(gateway);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/vnd.flowersforever+json"})
    void preservesOptionalEnvelopeFieldsAndArgumentTypes(String mediaType) throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        when(gateway.execute(eq("local-token"), eq("farm-data"), any()))
                .thenReturn(new HarnessResponse("1.0", "valid", "farm-data",
                        true, false, "Done", null, Map.of()));
        mvc(gateway).perform(post("/api/harness/farm-data/commands")
                        .header(HarnessController.TOKEN_HEADER, "local-token")
                        .contentType(mediaType).content("""
                                {"requestId":"valid","action":"catalog",
                                 "arguments":{"limit":10,"enabled":true,"label":"Rose"},
                                 "expiresAt":"2026-09-14T12:00:00+05:30"}
                                """))
                .andExpect(status().isOk());
        verify(gateway).execute("local-token", "farm-data", new HarnessCommand(
                "valid", "catalog", Map.of("limit", 10, "enabled", true, "label", "Rose"),
                Instant.parse("2026-09-14T06:30:00Z")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"arguments\":null,\"expiresAt\":null"})
    void preservesAbsentAndNullOptionalFields(String optionalFields) throws Exception {
        HarnessGateway gateway = mock(HarnessGateway.class);
        mvc(gateway).perform(post("/api/harness/farm-data/commands")
                        .header(HarnessController.TOKEN_HEADER, "local-token")
                        .contentType(MediaType.APPLICATION_JSON).content(
                                "{\"requestId\":\"id\",\"action\":\"catalog\"" + optionalFields + "}"))
                .andExpect(status().isOk());
        verify(gateway).execute("local-token", "farm-data",
                new HarnessCommand("id", "catalog", Map.of()));
    }

    @Test
    void doesNotTightenOrdinaryApiJsonBinding() throws Exception {
        mvc(mock(HarnessGateway.class)).perform(post("/test/legacy")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":123,"extra":"ignored"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("123"));
    }

    private MockMvc mvc(HarnessGateway gateway) {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new MappingJackson2HttpMessageConverter(mapper));
        new HarnessJsonConfig().harnessCommandJson(mapper).extendMessageConverters(converters);
        return standaloneSetup(new HarnessController(gateway), new LegacyController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(converters.toArray(HttpMessageConverter<?>[]::new)).build();
    }

    @RestController
    static class LegacyController {
        @PostMapping("/test/legacy")
        LegacyRecord legacy(@RequestBody LegacyRecord body) {
            return body;
        }
    }

    record LegacyRecord(String name) {
    }
}
