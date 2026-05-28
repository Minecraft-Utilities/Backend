package xyz.mcutils.backend.common;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.exception.impl.RateLimitException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WebRequest {

    @Value("${mc-utils.http-client.connect-timeout-ms}")
    private int connectTimeoutMs;

    @Value("${mc-utils.http-client.socket-timeout-ms}")
    private int socketTimeoutMs;

    @Value("${mc-utils.http-proxy:}")
    private String httpProxy;

    private final JsonMapper jsonMapper;
    private HttpClient httpClient;

    public WebRequest(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @PostConstruct
    private void initHttpClient() {
        // HTTP/1.1 avoids HTTP/2 "too many concurrent streams" when many virtual threads
        // hit the same host (e.g. sessionserver.mojang.com) at once.
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(Main.EXECUTOR)
                .build();
    }

    public RequestBuilder request(String url) {
        return new RequestBuilder(url);
    }

    public record RawResponse(int statusCode, String body) {
        public boolean isSuccess() {
            return this.statusCode >= 200 && this.statusCode < 300;
        }
    }

    public enum Method { GET, POST, HEAD }

    public class RequestBuilder {

        private final String url;
        private Method method = Method.GET;
        private String encodedBody;
        private String contentType;
        private boolean useProxy;
        private final Map<String, String> headers = new LinkedHashMap<>();

        private RequestBuilder(String url) {
            this.url = url;
        }

        public RequestBuilder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public RequestBuilder get() {
            this.method = Method.GET;
            return this;
        }

        public RequestBuilder post(Object jsonBody) {
            this.method = Method.POST;
            this.encodedBody = jsonMapper.writeValueAsString(jsonBody);
            this.contentType = "application/json";
            return this;
        }

        public RequestBuilder post(MultiValueMap<String, String> formBody) {
            this.method = Method.POST;
            this.encodedBody = formBody.entrySet().stream()
                    .flatMap(e -> e.getValue().stream().map(v ->
                            URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                            + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)))
                    .collect(Collectors.joining("&"));
            this.contentType = "application/x-www-form-urlencoded";
            return this;
        }

        public RequestBuilder postRaw(String body, String contentType) {
            this.method = Method.POST;
            this.encodedBody = body;
            this.contentType = contentType;
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
            HttpRequest req = buildRequest("application/json");
            try {
                HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 429) {
                    throw new RateLimitException("Rate limit was reached");
                }
                if (status == 204 || status >= 300) {
                    return null;
                }
                String ct = response.headers().firstValue("Content-Type").orElse("");
                if (!ct.contains("application/json")) {
                    return null;
                }
                String body = response.body();
                if (body == null || body.isBlank()) {
                    return null;
                }
                return jsonMapper.readValue(body, clazz);
            } catch (RateLimitException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public <T> ResponseEntity<T> asResponse(Class<T> clazz) {
            HttpRequest req = buildRequest("application/json");
            try {
                HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                T body = null;
                String raw = response.body();
                if (raw != null && !raw.isBlank()) {
                    try {
                        body = jsonMapper.readValue(raw, clazz);
                    } catch (JacksonException ignored) {
                        // Return null body on deserialisation failure
                    }
                }
                return ResponseEntity.status(status).body(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public byte[] asBytes() {
            HttpRequest req = buildRequest(null);
            try {
                HttpResponse<byte[]> response = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (status == 429) {
                    throw new RateLimitException("Rate limit reached fetching: " + url);
                }
                if (status < 200 || status >= 300) {
                    log.warn("Unexpected status {} fetching {}", status, url);
                    return null;
                }
                return response.body();
            } catch (RateLimitException e) {
                throw e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                log.warn("Error fetching {}: {}", url, e.getMessage());
                return null;
            }
        }

        public RawResponse asRaw() {
            HttpRequest req = buildRequest(null);
            try {
                HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                return new RawResponse(response.statusCode(), response.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new RawResponse(-1, null);
            } catch (IOException e) {
                return new RawResponse(-1, null);
            }
        }

        public long pingMillis() {
            HttpRequest req = buildRequest(null);
            try {
                long before = System.currentTimeMillis();
                HttpResponse<Void> response = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    return -1L;
                }
                return System.currentTimeMillis() - before;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1L;
            } catch (IOException e) {
                return -1L;
            }
        }

        public boolean exists() {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl()))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofMillis(socketTimeoutMs))
                    .build();
            try {
                HttpResponse<Void> response = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                return response.statusCode() == 200;
            } catch (Exception e) {
                return false;
            }
        }

        private HttpRequest buildRequest(String accept) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(resolveUrl()))
                    .timeout(Duration.ofMillis(socketTimeoutMs));
            for (Map.Entry<String, String> header : this.headers.entrySet()) {
                builder.header(header.getKey(), header.getValue());
            }
            if (accept != null) {
                builder.header("Accept", accept);
            }
            switch (method) {
                case POST -> {
                    HttpRequest.BodyPublisher publisher = encodedBody != null
                            ? HttpRequest.BodyPublishers.ofString(encodedBody)
                            : HttpRequest.BodyPublishers.noBody();
                    builder.POST(publisher);
                    if (contentType != null) {
                        builder.header("Content-Type", contentType);
                    }
                }
                case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
                default -> builder.GET();
            }
            return builder.build();
        }

        private String resolveUrl() {
            if (useProxy && StringUtils.hasText(httpProxy)) {
                String base = httpProxy.endsWith("/") ? httpProxy.substring(0, httpProxy.length() - 1) : httpProxy;
                String encoded = URLEncoder.encode(url, StandardCharsets.UTF_8).replace("+", "%20");
                return base + "/" + encoded;
            }
            return url;
        }
    }
}
