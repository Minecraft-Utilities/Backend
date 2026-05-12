package xyz.mcutils.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class IPUtils {
    /**
     * The headers that contain the IP.
     */
    private static final String[] IP_HEADERS = new String[]{"CF-Connecting-IP", "X-Forwarded-For"};

    /**
     * Get the real IP from the given request.
     *
     * @param request the request
     * @return the real IP
     */
    public static String getRealIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        for (String headerName : IP_HEADERS) {
            String header = request.getHeader(headerName);
            if (header == null) {
                continue;
            }
            if (!header.contains(",")) { // Handle single IP
                ip = header;
                break;
            }
            // Handle multiple IPs - take the first (leftmost client IP)
            ip = header.split(",")[0].trim();
        }
        return ip;
    }
}