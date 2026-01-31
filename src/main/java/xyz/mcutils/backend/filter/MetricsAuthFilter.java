package xyz.mcutils.backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that enforces bearer token authentication on the /metrics endpoint.
 * If no token is configured, the endpoint is left open.
 */
public class MetricsAuthFilter implements Filter {

    private final Optional<String> requiredToken;

    public MetricsAuthFilter(String configuredToken) {
        this.requiredToken = Optional.ofNullable(configuredToken)
                .filter(t -> !t.isBlank());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (requiredToken.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (token.equals(requiredToken.get())) {
                chain.doFilter(request, response);
                return;
            }
        }

        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setContentType("text/plain");
        httpResponse.getWriter().write("Unauthorized");
    }
}
