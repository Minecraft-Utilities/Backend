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
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import xyz.mcutils.backend.metric.impl.api.RequestsMetric;
import xyz.mcutils.backend.service.MetricService;

@ControllerAdvice
@Slf4j
public class RequestTracker implements ResponseBodyAdvice<Object> {

    @Override
    public Object beforeBodyWrite(Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NonNull ServerHttpRequest rawRequest,
                                  @NonNull ServerHttpResponse rawResponse) {
        HttpServletRequest request = ((ServletServerHttpRequest) rawRequest).getServletRequest();

        // Ignore metrics and health check requests
        if (!request.getRequestURI().contains("/metrics") && !request.getRequestURI().contains("/health")) {
            MetricService.getMetric(RequestsMetric.class).getValue().inc();
        }
        return body;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
}