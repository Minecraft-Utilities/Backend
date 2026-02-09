package xyz.mcutils.backend.controller;

import io.prometheus.metrics.config.EscapingScheme;
import io.prometheus.metrics.expositionformats.PrometheusTextFormatWriter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.SneakyThrows;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.mcutils.backend.config.AppConfig;
import xyz.mcutils.backend.model.dto.response.HealthResponse;
import xyz.mcutils.backend.model.dto.response.IndexResponse;
import xyz.mcutils.backend.model.dto.response.StatisticsResponse;
import xyz.mcutils.backend.service.MetricService;
import xyz.mcutils.backend.service.StatisticsService;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping(value = "/")
@Tag(name = "Index Controller")
public class IndexController {

    private final BuildProperties buildProperties;
    private final AppConfig appConfig;
    private final StatisticsService statisticsService;

    public IndexController(BuildProperties buildProperties, AppConfig appConfig, StatisticsService statisticsService) {
        this.buildProperties = buildProperties;
        this.appConfig = appConfig;
        this.statisticsService = statisticsService;
    }

    @GetMapping(value = "/")
    public IndexResponse index() {
        return new IndexResponse(
                "Minecraft Utilities API",
                buildProperties == null ? "dev" : buildProperties.getVersion(),
                appConfig.getWebPublicUrl() + "/swagger-ui.html"
        );
    }

    @GetMapping(value = "/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("OK"));
    }

    @GetMapping(value = "/statistics") @SneakyThrows
    public StatisticsResponse getStatistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE) @SneakyThrows
    public String getMetrics() {
        var snapshots = MetricService.REGISTRY.scrape();
        var output = new ByteArrayOutputStream();
        PrometheusTextFormatWriter.create().write(output, snapshots, EscapingScheme.DEFAULT);
        return output.toString(StandardCharsets.UTF_8);
    }
}
