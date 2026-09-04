package com.flowerfarm.automation.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Prints one machine-readable readiness result for --mcp-doctor. */
@Component
@Profile("mcp-doctor")
public class McpDoctorRunner implements ApplicationRunner, ExitCodeGenerator {

    private final McpDoctor doctor;
    private final ObjectMapper mapper;
    private final Environment environment;
    private int exitCode = 3;

    public McpDoctorRunner(
            McpDoctor doctor, ObjectMapper mapper, Environment environment) {
        this.doctor = doctor;
        this.mapper = mapper;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws JsonProcessingException {
        boolean tokenConfigured = environment.getProperty(
                McpDoctorBootstrap.TOKEN_VALID_PROPERTY, Boolean.class, false);
        Map<String, Object> report = doctor.inspect(tokenConfigured);
        exitCode = Boolean.TRUE.equals(report.get("ready")) ? 0 : 2;
        System.out.println(mapper.writeValueAsString(report));
        System.out.flush();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
