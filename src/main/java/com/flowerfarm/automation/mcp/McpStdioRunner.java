package com.flowerfarm.automation.mcp;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Runs the bundled MCP adapter when the executable is launched with --mcp. */
@Component
@Profile("mcp & !mcp-doctor")
public class McpStdioRunner implements ApplicationRunner {

    private final McpStdioServer server;

    public McpStdioRunner(McpStdioServer server) {
        this.server = server;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        server.serve(System.in, System.out, System.err);
    }
}
