package xyz.mcutils.backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Stores the request start time as an attribute so downstream components
 * (e.g. RequestTracker) can compute request duration.
 */
@Component
public class RequestTimingFilter implements Filter {
    public static final String START_TIME_ATTR = "__request_start_ms";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            request.setAttribute(START_TIME_ATTR, System.currentTimeMillis());
        }
        chain.doFilter(request, response);
    }
}
