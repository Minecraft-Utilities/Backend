package xyz.mcutils.backend.common;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
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

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

@Service
public class WebRequest {
    /**
     * Default connection pool settings
     */
    private static final int DEFAULT_MAX_TOTAL_CONNECTIONS = 200;
    private static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 50;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2500;
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 10000;
    private static final int DEFAULT_CONNECTION_TIME_TO_LIVE_SECONDS = 60;

    /**
     * The web client with connection pooling.
     */
    private static final RestClient CLIENT;
    private static final CloseableHttpClient HTTP_CLIENT;

    static {
        // Create connection pool manager
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(DEFAULT_MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_CONNECTIONS_PER_ROUTE);

        // Configure socket settings
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(DEFAULT_SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .build();
        connectionManager.setDefaultSocketConfig(socketConfig);

        // Configure request settings
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(DEFAULT_SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .build();

        // Create HTTP client with connection pooling
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

    @Value("${mc-utils.http-proxy:}")
    private String httpProxy;

    private RestClient proxyClient;

    private RestClient getClient(boolean useProxy) {
        if (useProxy && StringUtils.hasText(httpProxy)) {
            if (proxyClient == null) {
                try {
                    HttpHost proxyHost = HttpHost.create(httpProxy);
                    DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost);
                PoolingHttpClientConnectionManager proxyConnectionManager = new PoolingHttpClientConnectionManager();
                proxyConnectionManager.setMaxTotal(DEFAULT_MAX_TOTAL_CONNECTIONS);
                proxyConnectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
                SocketConfig proxySocketConfig = SocketConfig.custom()
                        .setSoTimeout(Timeout.of(DEFAULT_SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .build();
                proxyConnectionManager.setDefaultSocketConfig(proxySocketConfig);
                RequestConfig proxyRequestConfig = RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(DEFAULT_SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .setConnectionRequestTimeout(Timeout.of(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                        .build();
                CloseableHttpClient proxyHttpClient = HttpClients.custom()
                        .setConnectionManager(proxyConnectionManager)
                        .setRoutePlanner(routePlanner)
                        .setDefaultRequestConfig(proxyRequestConfig)
                        .evictIdleConnections(Timeout.of(DEFAULT_CONNECTION_TIME_TO_LIVE_SECONDS, TimeUnit.SECONDS))
                        .evictExpiredConnections()
                        .build();
                HttpComponentsClientHttpRequestFactory proxyRequestFactory = new HttpComponentsClientHttpRequestFactory(proxyHttpClient);
                proxyRequestFactory.setConnectionRequestTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
                proxyClient = RestClient.builder()
                        .requestFactory(proxyRequestFactory)
                        .build();
                } catch (URISyntaxException e) {
                    return CLIENT;
                }
            }
            return proxyClient;
        }
        return CLIENT;
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
     * Gets a response from the given URL, optionally via the configured HTTP proxy.
     *
     * @param url the url
     * @param useProxy when true and httpProxy is set, the request is sent via the proxy
     * @return the response
     * @param <T> the type of the response
     */
    public <T> T getAsEntity(String url, Class<T> clazz, boolean useProxy) throws RateLimitException {
        RestClient client = getClient(useProxy);
        ResponseEntity<T> responseEntity = client.get()
                .uri(url)
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
     * Gets a response from the given URL, optionally via the configured HTTP proxy.
     *
     * @param url the url
     * @param useProxy when true and httpProxy is set, the request is sent via the proxy
     * @return the response
     */
    public ResponseEntity<?> get(String url, Class<?> clazz, boolean useProxy) {
        return getClient(useProxy).get()
                .uri(url)
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
     * Checks whether a resource exists at the given URL, optionally via the configured HTTP proxy.
     *
     * @param url the URL
     * @param useProxy when true and httpProxy is set, the request is sent via the proxy
     * @return true if the resource exists (status 200), false otherwise or on error
     */
    public boolean checkExists(String url, boolean useProxy) {
        try {
            ResponseEntity<Void> response = getClient(useProxy).head()
                    .uri(url)
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
     * Performs a GET request and returns the response body as a byte array, optionally via the configured HTTP proxy.
     *
     * @param url the URL
     * @param useProxy when true and httpProxy is set, the request is sent via the proxy
     * @return the response body, or null if status is not 2xx or on error
     */
    public byte[] getAsByteArray(String url, boolean useProxy) {
        try {
            ResponseEntity<byte[]> response = getClient(useProxy).get()
                    .uri(url)
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
