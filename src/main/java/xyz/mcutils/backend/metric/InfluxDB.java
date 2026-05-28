package xyz.mcutils.backend.metric;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.mcutils.backend.common.WebRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Writes metrics via the InfluxDB line protocol API (VictoriaMetrics-compatible).
 */
@Component
@Slf4j(topic = "InfluxDB")
public final class InfluxDB {
    private final WebRequest webRequest;

    @Value("${mc-utils.influxdb.enabled:false}")
    private boolean enabled;

    @Value("${mc-utils.influxdb.host:http://127.0.0.1:8428}")
    private String host;

    @Value("${mc-utils.influxdb.username:}")
    private String username;

    @Value("${mc-utils.influxdb.password:}")
    private String password;

    private URI writeUri;
    private URI healthUri;
    private String authorizationHeader;
    private boolean connected;

    public InfluxDB(WebRequest webRequest) {
        this.webRequest = webRequest;
    }

    @PostConstruct
    public void connect() {
        if (!this.enabled) {
            return;
        }
        if (this.connected) {
            log.warn("Already connected to InfluxDB");
            return;
        }
        String normalizedHost = this.host.endsWith("/") ? this.host.substring(0, this.host.length() - 1) : this.host;
        URI baseUri = URI.create(normalizedHost);
        this.writeUri = baseUri.resolve("/api/v2/write");
        this.healthUri = baseUri.resolve("/api/v1/status/tsdb");
        if (StringUtils.hasText(this.username)) {
            String credentials = this.username + ':' + (this.password == null ? "" : this.password);
            this.authorizationHeader = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        if (this.ping() < 0L) {
            this.housekeeping();
            log.error("Failed to connect to InfluxDB at {}", normalizedHost);
            return;
        }
        this.connected = true;
        log.info("Successfully connected!");
    }

    @PreDestroy
    public void disconnect() {
        this.housekeeping();
    }

    public boolean isConnected() {
        return this.connected;
    }

    public void housekeeping() {
        this.writeUri = null;
        this.healthUri = null;
        this.authorizationHeader = null;
        this.connected = false;
    }

    public void writePoints(List<MetricPoint> points) {
        if (!this.connected || points.isEmpty()) {
            return;
        }
        WebRequest.RawResponse response = this.authorizedRequest(this.writeUri.toString())
                .postRaw(InfluxLineProtocol.format(points), "text/plain; charset=utf-8")
                .asRaw();
        if (!response.isSuccess()) {
            log.error("Failed to write metrics (HTTP {}): {}", response.statusCode(), response.body());
        }
    }

    private long ping() {
        return this.authorizedRequest(this.healthUri.toString()).pingMillis();
    }

    private WebRequest.RequestBuilder authorizedRequest(String url) {
        WebRequest.RequestBuilder builder = this.webRequest.request(url);
        if (this.authorizationHeader != null) {
            builder.header("Authorization", this.authorizationHeader);
        }
        return builder;
    }
}
