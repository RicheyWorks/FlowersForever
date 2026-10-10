package com.flowerfarm.connector.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Every HTTP client/connection is a fake. No network requests or real credentials. */
class ShopifyConnectorSecurityTest {
    private static final String ENDPOINT = "https://demo-farm.myshopify.com/admin/api/2024-01/products.json";

    @ParameterizedTest
    @ValueSource(strings = {"https://attacker.example/products.json", "http://demo-farm.myshopify.com/admin/api/2024-01/products.json",
            "https://demo-farm.myshopify.com:444/admin/api/2024-01/products.json",
            "https://demo-farm.myshopify.com.attacker.example/admin/api/2024-01/products.json",
            "https://user@demo-farm.myshopify.com/admin/api/2024-01/products.json",
            "https://127.0.0.1/admin/api/2024-01/products.json", "file:///tmp/products.json",
            "//demo-farm.myshopify.com/admin/api/2024-01/products.json",
            "https://demo-farm.myshopify.com/admin/api/2024-01/orders.json",
            "https://demo-farm.myshopify.com/admin/api/2024-01/products.json#fragment"})
    void importAndSyncNeverSendCredentialsToAnUnadmittedNextPage(String next) {
        RestTemplate http = mock(RestTemplate.class);
        ShopifyConnector connector = connector("demo-farm", "2024-01", http);
        when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(page("<" + next + ">; rel=\"next\""));
        assertThat(connector.importItems().isSuccess()).isFalse();
        verify(http, times(1)).exchange(eq(URI.create(ENDPOINT + "?limit=50")), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class));
        clearInvocations(http);
        assertThat(connector.syncUpdates(List.of()).isSuccess()).isFalse();
        verify(http, times(1)).exchange(eq(URI.create(ENDPOINT + "?limit=50")), eq(HttpMethod.GET),
                any(HttpEntity.class), eq(Map.class));
        verifyNoMoreInteractions(http);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://demo-farm.myshopify.com", "127.0.0.1", "shop.example",
            "demo-farm.myshopify.com.attacker.example", "https://user@demo-farm.myshopify.com",
            "https://demo-farm.myshopify.com:444", "https://demo-farm.myshopify.com/products",
            "https://demo-farm.myshopify.com?query=yes", "https://demo-farm.myshopify.com#fragment",
            "nested.demo-farm.myshopify.com", "-demo-farm", "demo-farm-"})
    void configuredStoreMustBeAnAdmittedShopifyHttpsHost(String shop) {
        RestTemplate http = mock(RestTemplate.class);
        assertThat(connector(shop, "2024-01", http).importItems().isSuccess()).isFalse();
        verifyNoInteractions(http);
    }

    @Test
    void preservesOpaqueCursorBytesAndAdmitsOnlyCanonicalStoreRequests() {
        for (String shop : List.of("demo-farm", "demo-farm.myshopify.com", "https://DEMO-FARM.myshopify.com:443/")) {
            RestTemplate http = mock(RestTemplate.class);
            List<URI> requested = new ArrayList<>();
            String cursor = "page_info=opaque%2Fcursor%3D%2B&limit=50";
            when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                    .thenAnswer(call -> {
                        URI uri = call.getArgument(0);
                        requested.add(uri);
                        HttpEntity<?> entity = call.getArgument(2);
                        assertThat(entity.getHeaders().getFirst("X-Shopify-Access-Token")).isEqualTo("fake-api-token");
                        return requested.size() == 1
                                ? page("<" + ENDPOINT + "?" + cursor + ">; rel=\"next\"") : page(null);
                    });
            assertThat(connector(shop, "2024-01", http).importItems().isSuccess()).isTrue();
            assertThat(requested).containsExactly(URI.create(ENDPOINT + "?limit=50"), URI.create(ENDPOINT + "?" + cursor));
            assertThat(requested.get(1).getRawQuery()).isEqualTo(cursor);
        }
    }

    @Test
    void rejectsMalformedDuplicateOverlongAndCyclicPagination() {
        for (String header : List.of("<" + ENDPOINT + ">", "<" + ENDPOINT + ">; rel=\"next",
                "<" + ENDPOINT + ">; rel=\"next\", <" + ENDPOINT + ">; rel=\"next\"",
                "<" + ENDPOINT + ">; rel=\"next\"; rel=\"previous\"", "<".repeat(16385))) {
            assertThatThrownBy(() -> ShopifyConnector.extractNextLink(header)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ShopifyConnector.extractNextLink("<" + ENDPOINT
                + "?page_info=next>; title=\"a,b\"; rel = \"next\"" )).contains("page_info=next");
        RestTemplate http = mock(RestTemplate.class);
        when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(page("<" + ENDPOINT + "?limit=50>; rel=\"next\""));
        assertThat(connector("demo-farm", "2024-01", http).importItems().isSuccess()).isFalse();
        verify(http, times(1)).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void refusesApiVersionPathInjectionAndRedirectResponses() {
        RestTemplate http = mock(RestTemplate.class);
        assertThat(connector("demo-farm", "2024-01/../orders", http).importItems().isSuccess()).isFalse();
        verifyNoInteractions(http);
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("https://attacker.example"));
        when(http.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("products", List.of()), headers, HttpStatus.FOUND));
        assertThat(connector("demo-farm", "2024-01", http).importItems().isSuccess()).isFalse();
        verify(http, times(1)).exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void nativeTransportDisablesAutomaticRedirectsWithoutOpeningAConnection() throws Exception {
        ShopifyConnector.ShopifyRequestFactory factory = new ShopifyConnector.ShopifyRequestFactory();
        for (String method : List.of("GET", "POST", "PUT")) {
            FakeConnection connection = new FakeConnection(new URL("https://demo-farm.myshopify.com/"));
            factory.prepareConnection(connection, method);
            assertThat(connection.getInstanceFollowRedirects()).isFalse();
            assertThat(connection.getRequestMethod()).isEqualTo(method);
        }
    }

    @Test
    void htmlConversionPreservesTextAndHasBoundedLinearWorkForUnmatchedAngles() {
        assertThat(ShopifyConnector.stripHtml(" <p>Hello</p>\n<br> world\t ")).isEqualTo("Hello world");
        assertThat(ShopifyConnector.stripHtml("<>\ttext <unfinished")).isEqualTo("<> text <unfinished");
        assertThat(ShopifyConnector.stripHtml("<<x>>")).isEqualTo(">");
        assertThat(ShopifyConnector.stripHtml(null)).isEmpty();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            String adversarial = "<".repeat(200_000);
            assertThat(ShopifyConnector.stripHtml(adversarial)).isEqualTo(adversarial);
            assertThatThrownBy(() -> ShopifyConnector.stripHtml("<".repeat(1_000_001)))
                    .hasMessageContaining("supported size");
        });
    }

    private static ShopifyConnector connector(String shop, String version, RestTemplate http) {
        return new ShopifyConnector(shop, "fake-api-token", version, http);
    }

    private static ResponseEntity<Map> page(String link) {
        HttpHeaders headers = new HttpHeaders();
        if (link != null) headers.set(HttpHeaders.LINK, link);
        return new ResponseEntity<>(Map.of("products", List.of()), headers, HttpStatus.OK);
    }

    private static final class FakeConnection extends HttpURLConnection {
        FakeConnection(URL url) { super(url); }
        @Override public void disconnect() { }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { throw new AssertionError("Fake connection must never connect"); }
    }
}
