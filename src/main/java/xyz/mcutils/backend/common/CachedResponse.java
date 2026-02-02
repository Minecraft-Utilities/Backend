package xyz.mcutils.backend.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter @Setter
public class CachedResponse {
    /**
     * Whether this request is cached.
     */
    private boolean cached;

    /**
     * The unix timestamp of when this was cached.
     */
    private long cachedTime;

    public CachedResponse(boolean cached, long cachedTime) {
        this.cached = cached;
        this.cachedTime = cachedTime;
    }

    /**
     * Gets the cached time.
     *
     * @return the cached time
     */
    public long getCachedTime() {
        return cached ? cachedTime : -1;
    }
}
