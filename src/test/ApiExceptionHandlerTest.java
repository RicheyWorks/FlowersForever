package com.flowerfarm.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.flowerfarm.model.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API exception contract")
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("client errors retain the compatible error field and sanitize line breaks")
    void clientErrorContract() throws Exception {
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", not(containsString("\n"))))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/invalid"));
    }

    @Test
    @DisplayName("malformed JSON does not expose parser or constructor details")
    void malformedBodyContract() throws Exception {
        mockMvc.perform(post("/test/item")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rose\",\"price\":NaN}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_BODY"))
                .andExpect(jsonPath("$.error").value(
                        "Request body is malformed or contains invalid values."));
    }

    @Test
    @DisplayName("unexpected failures hide internal exception messages")
    void unexpectedFailureIsOpaque() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/test/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.incidentId").isNotEmpty())
                    .andExpect(jsonPath("$.error", not(containsString("database-password"))))
                    .andExpect(jsonPath("$.error", not(containsString("secret"))));

            String logged = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + " " + right);
            assertThat(logged)
                    .contains("password=[REDACTED]")
                    .doesNotContain("password=secret");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("persistence conflicts tell clients to refresh")
    void persistenceConflict() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.error", containsString("Refresh")));
    }

    @RestController
    static class FailureController {
        @GetMapping("/test/invalid")
        void invalid() {
            throw new IllegalArgumentException("bad value\r\nsecond line");
        }

        @PostMapping("/test/item")
        Item item(@RequestBody Item item) {
            return item;
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("database-password=secret");
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new DataIntegrityViolationException("constraint details");
        }
    }
}
