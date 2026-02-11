package xyz.mcutils.backend.common;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import xyz.mcutils.backend.exception.impl.RateLimitException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
public class WebRequest {
    /**
     * Default connection pool settings
     */
    private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 1000;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 100;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2500;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 10000;
    private static final int DEFAULT_CONNECTION_TIME_TO_LIVE_SECONDS = 60;

    /**
     * The web client with connection pooling.
     */
    private static final RestClient CLIENT;
    private static final CloseableHttpClient HTTP_CLIENT;

    static {
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(DEFAULT_SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .build();

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(DEFAULT_MAX_TOTAL_CONNECTIONS)
                .setMaxConnPerRoute(DEFAULT_MAX_CONNECTIONS_PER_ROUTE)
                .setDefaultSocketConfig(socketConfig)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(DEFAULT_SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .build();

        HTTP_CLIENT = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(Timeout.of(DEFAULT_CONNECTION_TIME_TO_LIVE_SECONDS, TimeUnit.SECONDS))
                .evictExpiredConnections()
                .build();

        // Create request factory with pooled client
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(HTTP_CLIENT);
        requestFactory.setConnectionRequestTimeout(DEFAULT_CONNECT_TIMEOUT_MS);

        CLIENT = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * URL proxy base (e.g. http://213.255.246.119:8080). When set and useProxy is true,
     * the request URL becomes proxyBase + "/" + targetUrl (e.g. proxyBase/https://example.com/path).
     */
    @Value("${mc-utils.http-proxy:}")
    private String httpProxy;

    /**
     * Converts the given URL to a request URL, optionally via the URL proxy (proxy base + "/" + url).
     *
     * @param url the URL
     * @param useProxy when true and httpProxy is set, the request goes to httpProxy/url
     * @return the request URL
     */
    private String toRequestUrl(String url, boolean useProxy) {
        if (useProxy && StringUtils.hasText(httpProxy)) {
            String base = httpProxy.endsWith("/") ? httpProxy.substring(0, httpProxy.length() - 1) : httpProxy;
            String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8).replace("+", "%20");
            return base + "/" + encoded;
        }
        return url;
    }

    /**
     * Gets a response from the given URL.
     *
     * @param url the url
     * @return the response
     * @param <T> the type of the response
     */
    public <T> T getAsEntity(String url, Class<T> clazz) throws RateLimitException {
        return getAsEntity(url, clazz, false);
    }

    /**
     * Gets a response from the given URL, optionally via the URL proxy (proxy base + "/" + url).
     *
     * @param url the url
     * @param useProxy when true and httpProxy is set, the request goes to httpProxy/url
     * @return the response
     * @param <T> the type of the response
     */
    public <T> T getAsEntity(String url, Class<T> clazz, boolean useProxy) throws RateLimitException {
        String requestUrl = toRequestUrl(url, useProxy);
        ResponseEntity<T> responseEntity = CLIENT.get()
                .uri(requestUrl)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {}) // Don't throw exceptions on error
                .toEntity(clazz);

        if (responseEntity.getStatusCode().isError()) {
            return null;
        }
        if (responseEntity.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            throw new RateLimitException("Rate limit was reached");
        }
        return responseEntity.getBody();
    }

    /**
     * Gets a response from the given URL.
     *
     * @param url the url
     * @return the response
     */
    public ResponseEntity<?> get(String url, Class<?> clazz) {
        return get(url, clazz, false);
    }

    /**
     * Gets a response from the given URL, optionally via the URL proxy (proxy base + "/" + url).
     *
     * @param url the url
     * @param useProxy when true and httpProxy is set, the request goes to httpProxy/url
     * @return the response
     */
    public ResponseEntity<?> get(String url, Class<?> clazz, boolean useProxy) {
        return CLIENT.get()
                .uri(toRequestUrl(url, useProxy))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (_, _) -> {}) // Don't throw exceptions on error
                .toEntity(clazz);
    }

    /**
     * Checks whether a resource exists at the given URL (HEAD request, 200 = exists).
     *
     * @param url the URL
     * @return true if the resource exists (status 200), false otherwise or on error
     */
    public boolean checkExists(String url) {
        return checkExists(url, false);
    }

    /**
     * Checks whether a resource exists at the given URL, optionally via the URL proxy (proxy base + "/" + url).
     *
     * @param url the URL
     * @param useProxy when true and httpProxy is set, the request goes to httpProxy/url
     * @return true if the resource exists (status 200), false otherwise or on error
     */
    public boolean checkExists(String url, boolean useProxy) {
        try {
            ResponseEntity<Void> response = CLIENT.head()
                    .uri(toRequestUrl(url, useProxy))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (_, _) -> {})
                    .toBodilessEntity();
            return response.getStatusCode().isSameCodeAs(HttpStatus.OK);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Performs a GET request and returns the response body as a byte array.
     *
     * @param url the URL
     * @return the response body, or null if status is not 2xx or on error
     */
    public byte[] getAsByteArray(String url) {
        return getAsByteArray(url, false);
    }

    /**
     * Performs a GET request and returns the response body as a byte array, optionally via the URL proxy (proxy base + "/" + url).
     *
     * @param url the URL
     * @param useProxy when true and httpProxy is set, the request goes to httpProxy/url
     * @return the response body, or null if status is not 2xx or on error
     */
    public byte[] getAsByteArray(String url, boolean useProxy) {
        try {
            ResponseEntity<byte[]> response = CLIENT.get()
                    .uri(toRequestUrl(url, useProxy))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (_, _) -> {})
                    .toEntity(byte[].class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            return response.getBody();
        } catch (Exception e) {
            return null;
        }
    }
}
