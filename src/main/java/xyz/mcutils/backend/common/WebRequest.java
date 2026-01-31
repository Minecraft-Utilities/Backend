package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import xyz.mcutils.backend.exception.impl.RateLimitException;

import java.util.concurrent.TimeUnit;

@UtilityClass
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

    /**
     * Gets a response from the given URL.
     *
     * @param url the url
     * @return the response
     * @param <T> the type of the response
     */
    public static <T> T getAsEntity(String url, Class<T> clazz) throws RateLimitException {
        ResponseEntity<T> responseEntity = CLIENT.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {}) // Don't throw exceptions on error
                .toEntity(clazz);

        if (responseEntity.getStatusCode().isError()) {
            return null;
        }
        if (responseEntity.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            throw new RateLimitException("Rate limit reached");
        }
        return responseEntity.getBody();
    }

    /**
     * Gets a response from the given URL.
     *
     * @param url the url
     * @return the response
     */
    public static ResponseEntity<?> get(String url, Class<?> clazz) {
        return CLIENT.get()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {}) // Don't throw exceptions on error
                .toEntity(clazz);
    }

    /**
     * Gets a response from the given URL.
     *
     * @param url the url
     * @return the response
     */
    public static ResponseEntity<?> head(String url, Class<?> clazz) {
        return CLIENT.head()
                .uri(url)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {}) // Don't throw exceptions on error
                .toEntity(clazz);
    }
}
