package com.flowerfarm.controller;

import com.flowerfarm.automation.HarnessAuthenticationException;
import com.flowerfarm.automation.HarnessCommand;
import com.flowerfarm.automation.HarnessGateway;
import com.flowerfarm.automation.HarnessManifest;
import com.flowerfarm.automation.HarnessPluginDescriptor;
import com.flowerfarm.automation.HarnessPolicyException;
import com.flowerfarm.automation.HarnessResponse;
import com.flowerfarm.automation.HarnessSnapshot;
import com.flowerfarm.automation.farm.FarmDataImportConflictException;
import com.flowerfarm.automation.farm.FarmDataExportConflictException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Disabled-by-default loopback transport for AI and external harness clients. */
@RestController
@RequestMapping("/api/harness")
@ConditionalOnProperty(name = "flowerfarm.harness.enabled", havingValue = "true")
@Profile("!cli & !mcp & !release-smoke")
public class HarnessController {

    public static final String TOKEN_HEADER = "X-FlowersForever-Harness-Token";

    private final HarnessGateway gateway;

    public HarnessController(HarnessGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    public List<HarnessPluginDescriptor> discover(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return gateway.discover(token);
    }

    @GetMapping("/manifest")
    public HarnessManifest manifest(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return gateway.manifest(token);
    }

    @GetMapping("/{pluginId}/observe")
    public HarnessSnapshot observe(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable String pluginId) {
        return gateway.observe(token, pluginId);
    }

    @PostMapping("/{pluginId}/commands")
    public HarnessResponse execute(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @PathVariable String pluginId,
            @RequestBody HarnessCommand command) {
        return gateway.execute(token, pluginId, command);
    }

    @ExceptionHandler(HarnessAuthenticationException.class)
    public ResponseEntity<Map<String, Object>> authenticationFailure(
            HarnessAuthenticationException exception) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(HarnessPolicyException.class)
    public ResponseEntity<Map<String, Object>> policyFailure(
            HarnessPolicyException exception) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler({FarmDataImportConflictException.class,
            FarmDataExportConflictException.class})
    public ResponseEntity<Map<String, Object>> importConflict(
            RuntimeException exception) {
        return error(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> commandFailure(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message == null ? "Harness request failed" : message));
    }
}
