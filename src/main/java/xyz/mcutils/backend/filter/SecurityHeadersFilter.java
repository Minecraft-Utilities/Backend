package xyz.mcutils.backend.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Filter that adds security-related response headers to all responses.
 * Aligns with Spring Security best practices (CSP, X-Frame-Options, etc.).
 */
public class SecurityHeadersFilter implements Filter {

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String X_FRAME_OPTIONS_DENY = "DENY";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String NOSNIFF = "nosniff";
    private static final String REFERRER_POLICY = "Referrer-Policy";
    private static final String NO_REFERRER = "no-referrer";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(X_FRAME_OPTIONS, X_FRAME_OPTIONS_DENY);
            httpResponse.setHeader(X_CONTENT_TYPE_OPTIONS, NOSNIFF);
            httpResponse.setHeader(REFERRER_POLICY, NO_REFERRER);
        }
        chain.doFilter(request, response);
    }
}
