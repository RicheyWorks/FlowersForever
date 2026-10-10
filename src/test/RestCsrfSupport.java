package com.flowerfarm.testsupport;

import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** Obtains the same masked token and cookie that a native HTTP client must retain. */
public final class RestCsrfSupport {
    private RestCsrfSupport() {
    }

    public static HttpHeaders headers(TestRestTemplate client, String endpoint) {
        ResponseEntity<Map<String, String>> response = client.exchange(
                endpoint, HttpMethod.GET, HttpEntity.EMPTY,
                new ParameterizedTypeReference<Map<String, String>>() { });
        Map<String, String> body = response.getBody();
        if (response.getStatusCode() != HttpStatus.OK || body == null
                || !"X-XSRF-TOKEN".equals(body.get("headerName"))
                || body.get("token") == null || body.get("token").isBlank()) {
            throw new AssertionError("The CSRF handshake must return its masked token and header name; status="
                    + response.getStatusCode());
        }
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        List<HttpCookie> cookies = (setCookies == null ? List.<String>of() : setCookies).stream()
                .flatMap(value -> HttpCookie.parse(value).stream())
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .toList();
        if (cookies.size() != 1 || cookies.get(0).getValue().isBlank()) {
            throw new AssertionError("The CSRF handshake must set exactly one XSRF-TOKEN cookie");
        }
        HttpCookie cookie = cookies.get(0);
        List<String> csrfHeaders = (setCookies == null ? List.<String>of() : setCookies).stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .toList();
        boolean strictSameSite = csrfHeaders.size() == 1 && List.of(csrfHeaders.get(0).split(";")).stream()
                .map(String::trim).anyMatch("SameSite=Strict"::equalsIgnoreCase);
        URI endpointUri = URI.create(endpoint);
        URI requestUri = endpointUri.isAbsolute() ? endpointUri : URI.create(client.getRootUri()).resolve(endpointUri);
        boolean secureRequest = "https".equalsIgnoreCase(requestUri.getScheme());
        if (!cookie.isHttpOnly() || !strictSameSite || !"/".equals(cookie.getPath())
                || cookie.getDomain() != null || cookie.getSecure() != secureRequest) {
            throw new AssertionError("The native CSRF cookie must be HttpOnly, SameSite=Strict, host-only, "
                    + "Path=/, and Secure exactly for HTTPS requests");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(body.get("headerName"), body.get("token"));
        headers.set(HttpHeaders.COOKIE, cookie.getName() + "=" + cookie.getValue());
        return headers;
    }
}
