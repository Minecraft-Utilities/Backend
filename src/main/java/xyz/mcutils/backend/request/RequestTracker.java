package xyz.mcutils.backend.request;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import xyz.mcutils.backend.filter.RequestTimingFilter;
import xyz.mcutils.backend.metric.impl.api.RequestsMetric;
import xyz.mcutils.backend.service.MetricService;

@ControllerAdvice
@Slf4j
public class RequestTracker implements ResponseBodyAdvice<Object> {

    @Override
    public Object beforeBodyWrite(Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType, @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NonNull ServerHttpRequest rawRequest, @NonNull ServerHttpResponse rawResponse) {
        HttpServletRequest request = ((ServletServerHttpRequest) rawRequest).getServletRequest();
        String uri = request.getRequestURI();

        // Ignore metrics and health check requests
        if (uri.contains("/metrics") || uri.contains("/health")) {
            return body;
        }

        String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String endpoint = pattern != null ? pattern : uri;
        String method = rawRequest.getMethod().name();

        int status = 200;
        if (rawResponse instanceof ServletServerHttpResponse servletResponse) {
            status = servletResponse.getServletResponse().getStatus();
        }

        long durationMs = 0;
        Object startTime = request.getAttribute(RequestTimingFilter.START_TIME_ATTR);
        if (startTime instanceof Long start) {
            durationMs = System.currentTimeMillis() - start;
        }

        MetricService.getMetric(RequestsMetric.class).record(endpoint, method, status, durationMs);
        return body;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
}
