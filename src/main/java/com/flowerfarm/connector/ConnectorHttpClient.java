package com.flowerfarm.connector;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Creates connector HTTP clients with bounded network waits. */
public final class ConnectorHttpClient {

    public static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    public static final int READ_TIMEOUT_MILLIS = 30_000;

    private ConnectorHttpClient() {
    }

    public static RestTemplate create() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        factory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return new RestTemplate(factory);
    }
}
