package xyz.mcutils.backend.log;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

public class RequestTimingFilter implements Filter {

    private static final String START_TIME_ATTRIBUTE = "requestStartTime";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest) {
            request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        }
        
        chain.doFilter(request, response);
    }
}
