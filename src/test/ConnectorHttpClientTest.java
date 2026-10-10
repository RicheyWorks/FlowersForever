package com.flowerfarm.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Connector HTTP client")
class ConnectorHttpClientTest {

    @Test
    @DisplayName("sets bounded connect and read timeouts")
    void boundedTimeouts() {
        var requestFactory = ConnectorHttpClient.create().getRequestFactory();
        assertThat(requestFactory).isInstanceOf(SimpleClientHttpRequestFactory.class);
        assertThat(ReflectionTestUtils.getField(requestFactory, "connectTimeout"))
                .isEqualTo(ConnectorHttpClient.CONNECT_TIMEOUT_MILLIS);
        assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
                .isEqualTo(ConnectorHttpClient.READ_TIMEOUT_MILLIS);
    }
}
