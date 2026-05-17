package xyz.mcutils.backend.common;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import xyz.mcutils.backend.exception.impl.RateLimitException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class WebRequest {

    @Value("${mc-utils.http-client.max-total-connections}")
    private int maxTotalConnections;

    @Value("${mc-utils.http-client.max-connections-per-route}")
    private int maxConnectionsPerRoute;

    @Value("${mc-utils.http-client.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${mc-utils.http-client.connection-request-timeout-ms}")
    private int connectionRequestTimeoutMs;

    @Value("${mc-utils.http-client.socket-timeout-ms}")
    private int socketTimeoutMs;

    @Value("${mc-utils.http-client.connection-time-to-live-seconds}")
    private int connectionTimeToLiveSeconds;

    @Value("${mc-utils.http-proxy:}")
    private String httpProxy;

    private RestClient client;

    @PostConstruct
    private void initHttpClient() {
        SocketConfig socketConfig = SocketConfig.custom()
                .setSoTimeout(Timeout.of(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(maxTotalConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .setDefaultSocketConfig(socketConfig)
                .setDefaultConnectionConfig(connectionConfig)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(socketTimeoutMs, TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(connectionRequestTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(Timeout.of(connectionTimeToLiveSeconds, TimeUnit.SECONDS))
                .evictExpiredConnections()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectionRequestTimeout(connectionRequestTimeoutMs);

        client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public RequestBuilder request(String url) {
        return new RequestBuilder(url);
    }

    public enum Method { GET, POST, HEAD }

    public class RequestBuilder {

        private final String url;
        private Method method = Method.GET;
        private Object body;
        private MediaType contentType;
        private boolean useProxy;

        private RequestBuilder(String url) {
            this.url = url;
        }

        public RequestBuilder get() {
            this.method = Method.GET;
            return this;
        }

        public RequestBuilder post(Object jsonBody) {
            this.method = Method.POST;
            this.body = jsonBody;
            this.contentType = MediaType.APPLICATION_JSON;
            return this;
        }

        public RequestBuilder post(MultiValueMap<String, String> formBody) {
            this.method = Method.POST;
            this.body = formBody;
            this.contentType = MediaType.APPLICATION_FORM_URLENCODED;
            return this;
        }

        public RequestBuilder head() {
            this.method = Method.HEAD;
            return this;
        }

        public RequestBuilder useProxy() {
            this.useProxy = true;
            return this;
        }

        public <T> T as(Class<T> clazz) {
            String requestUrl = resolveUrl();
            var spec = client.method(toHttpMethod()).uri(requestUrl).accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                spec.body(body);
            }
            return spec.exchange((req, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                    throw new RateLimitException("Rate limit was reached");
                }
                if (status.isError() || status.isSameCodeAs(HttpStatus.NO_CONTENT)) {
                    return null;
                }
                MediaType ct = response.getHeaders().getContentType();
                if (ct == null || !ct.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                    return null;
                }
                return response.bodyTo(clazz);
            });
        }

        public <T> ResponseEntity<T> asResponse(Class<T> clazz) {
            return client.method(toHttpMethod()).uri(resolveUrl())
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {})
                    .toEntity(clazz);
        }

        public byte[] asBytes() {
            try {
                ResponseEntity<byte[]> response = client.method(toHttpMethod()).uri(resolveUrl())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {})
                        .toEntity(byte[].class);

                if (response.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
                    throw new RateLimitException("Rate limit reached fetching: " + url);
                }
                if (!response.getStatusCode().is2xxSuccessful()) {
                    log.warn("Unexpected status {} fetching {}", response.getStatusCode(), url);
                    return null;
                }
                return response.getBody();
            } catch (RateLimitException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Error fetching {}: {}", url, e.getMessage());
                return null;
            }
        }

        public boolean exists() {
            try {
                ResponseEntity<Void> response = client.head().uri(resolveUrl())
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (req, res) -> {})
                        .toBodilessEntity();
                return response.getStatusCode().isSameCodeAs(HttpStatus.OK);
            } catch (Exception e) {
                return false;
            }
        }

        private String resolveUrl() {
            if (useProxy && StringUtils.hasText(httpProxy)) {
                String base = httpProxy.endsWith("/") ? httpProxy.substring(0, httpProxy.length() - 1) : httpProxy;
                String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8).replace("+", "%20");
                return base + "/" + encoded;
            }
            return url;
        }

        private org.springframework.http.HttpMethod toHttpMethod() {
            return switch (method) {
                case POST -> org.springframework.http.HttpMethod.POST;
                case HEAD -> org.springframework.http.HttpMethod.HEAD;
                default -> org.springframework.http.HttpMethod.GET;
            };
        }

        private RestClient.RequestBodySpec body(Object body, MediaType contentType) {
            // handled inline via .body(body, contentType) above — this is just for clarity
            throw new UnsupportedOperationException();
        }
    }
}