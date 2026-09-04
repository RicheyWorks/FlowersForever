package com.flowerfarm.automation;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Fail-closed runtime policy shared by REST, MCP, or future harness transports. */
@Component
@ConfigurationProperties(prefix = "flowerfarm.harness")
public class HarnessProperties implements InitializingBean {

    private boolean enabled;
    private String token = "";
    private boolean allowSensitiveRead;
    private boolean allowNavigation = true;
    private boolean allowDrafts;
    private boolean allowMutations;
    private boolean allowDestructive;
    private boolean allowPhysicalActions;

    @Override
    public void afterPropertiesSet() {
        validateConfiguration();
    }

    public void validateConfiguration() {
        if (!enabled) {
            return;
        }
        if (token == null || token.length() < 24) {
            throw new IllegalStateException(
                    "Enabled automation harness requires FLOWERFARM_HARNESS_TOKEN "
                            + "with at least 24 characters.");
        }
        if (allowDestructive && !allowMutations) {
            throw new IllegalStateException(
                    "Destructive harness actions require allow-mutations=true.");
        }
        if (allowPhysicalActions && (!allowMutations || !allowDestructive)) {
            throw new IllegalStateException("Physical harness actions require both "
                    + "allow-mutations=true and allow-destructive=true.");
        }
    }

    public void authenticate(String suppliedToken) {
        if (!enabled) {
            throw new HarnessAuthenticationException("Automation harness is disabled");
        }
        byte[] expected = token == null
                ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = suppliedToken == null
                ? new byte[0] : suppliedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new HarnessAuthenticationException("Invalid automation harness token");
        }
    }

    public void authorize(HarnessRisk risk) {
        if (!allows(risk)) {
            throw new HarnessPolicyException(
                    "Harness action risk " + risk + " is disabled by operator policy");
        }
    }

    public boolean allows(HarnessRisk risk) {
        return switch (risk) {
            case READ -> true;
            case SENSITIVE_READ -> allowSensitiveRead;
            case NAVIGATE -> allowNavigation;
            case DRAFT -> allowDrafts;
            case MUTATE -> allowMutations;
            case DESTRUCTIVE -> allowMutations && allowDestructive;
            case PHYSICAL -> allowMutations && allowDestructive && allowPhysicalActions;
        };
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public boolean isAllowNavigation() {
        return allowNavigation;
    }

    public boolean isAllowSensitiveRead() {
        return allowSensitiveRead;
    }

    public void setAllowSensitiveRead(boolean allowSensitiveRead) {
        this.allowSensitiveRead = allowSensitiveRead;
    }

    public void setAllowNavigation(boolean allowNavigation) {
        this.allowNavigation = allowNavigation;
    }

    public boolean isAllowDrafts() {
        return allowDrafts;
    }

    public void setAllowDrafts(boolean allowDrafts) {
        this.allowDrafts = allowDrafts;
    }

    public boolean isAllowMutations() {
        return allowMutations;
    }

    public void setAllowMutations(boolean allowMutations) {
        this.allowMutations = allowMutations;
    }

    public boolean isAllowDestructive() {
        return allowDestructive;
    }

    public void setAllowDestructive(boolean allowDestructive) {
        this.allowDestructive = allowDestructive;
    }

    public boolean isAllowPhysicalActions() {
        return allowPhysicalActions;
    }

    public void setAllowPhysicalActions(boolean allowPhysicalActions) {
        this.allowPhysicalActions = allowPhysicalActions;
    }
}
