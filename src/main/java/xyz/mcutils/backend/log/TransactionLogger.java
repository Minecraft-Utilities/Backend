package xyz.mcutils.backend.log;

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
import xyz.mcutils.backend.common.IPUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

@ControllerAdvice
@Slf4j(topic = "Req Transaction")
public class TransactionLogger implements ResponseBodyAdvice<Object> {

    private static final String START_TIME_ATTRIBUTE = "requestStartTime";

    @Override
    public Object beforeBodyWrite(Object body, @NonNull MethodParameter returnType, @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NonNull ServerHttpRequest rawRequest,
                                  @NonNull ServerHttpResponse rawResponse) {
        HttpServletRequest request = ((ServletServerHttpRequest) rawRequest).getServletRequest();

        // Get the request ip ip
        String ip = IPUtils.getRealIp(request);

        // Getting params
        Map<String, String> params = new HashMap<>();
        for (Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            params.put(entry.getKey(), Arrays.toString(entry.getValue()));
        }

        // Calculate processing time
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        long processingTime = startTime != null ? System.currentTimeMillis() - startTime : -1;

        // Logging the request
        log.info(String.format("[Req] %s | %s | '%s' | %dms | params=%s",
                request.getMethod(),
                ip,
                request.getRequestURI(),
                processingTime,
                params
        ));

        return body;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType, @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }
}