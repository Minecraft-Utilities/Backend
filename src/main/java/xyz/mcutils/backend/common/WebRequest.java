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

import jakarta.annotation.PostConstruct;
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
    @Value("${mc-utils.http-client.max-total-connections}")
    private int maxTotalConnections;

    @Value("${mc-utils.http-client.max-connections-per-route}")
    private int maxConnectionsPerRoute;

    @Value("${mc-utils.http-client.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${mc-utils.http-client.socket-timeout-ms}")
    private int socketTimeoutMs;

    @Value("${mc-utils.http-client.connection-time-to-live-seconds}")
    private int connectionTimeToLiveSeconds;

    private RestClient client;
    private CloseableHttpClient httpClient;

    /**
     * URL proxy base (e.g. http://213.255.246.119:8080). When set and useProxy is true,
     * the request URL becomes proxyBase + "/" + targetUrl (e.g. proxyBase/https://example.com/path).
     */
    @Value("${mc-utils.http-proxy:}")
    private String httpProxy;

    @PostConstruct
    private void initHttpClient() {
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotalConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setDefaultSocketConfig(socketConfig)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(Timeout.of(connectionTimeToLiveSeconds, TimeUnit.SECONDS))
                .evictExpiredConnections()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout(connectTimeoutMs);

        client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

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
        ResponseEntity<T> responseEntity = client.get()
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
        return client.get()
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
            ResponseEntity<Void> response = client.head()
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
            ResponseEntity<byte[]> response = client.get()
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
