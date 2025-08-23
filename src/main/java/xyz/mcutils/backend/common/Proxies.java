package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Gets a proxy in a round-robin fashion.
 */
@UtilityClass
public class Proxies {
    private static final List<String> proxies = List.of(
            "https://proxy.fascinated.cc",
            "https://proxy-2.fascinated.cc"
    );
    private static int currentProxyIndex = 0;

    /**
     * Gets the next proxy in the list,
     * rolls over to the first proxy when
     * the end of the list is reached.
     *
     * @return the next proxy.
     */
    public static String getNextProxy() {
        String proxy = proxies.get(currentProxyIndex);
        currentProxyIndex = (currentProxyIndex + 1) % proxies.size();
        return proxy;
    }

    /**
     * Gets the amount of proxies in the list.
     *
     * @return the amount of proxies.
     */
    public static int getTotalProxies() {
        return proxies.size();
    }
}
