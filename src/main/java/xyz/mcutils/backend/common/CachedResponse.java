package xyz.mcutils.backend.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor @NoArgsConstructor
@Getter
public class CachedResponse {
    /**
     * Whether this request is cached.
     */
    private boolean cached;

    /**
     * The unix timestamp of when this was cached.
     */
    private long cachedTime;

    /**
     * Sets if this request is cached.
     *
     * @param cached the new value of if this request is cached
     */
    public void setCached(boolean cached) {
        this.cached = cached;
        if (!cached) {
            cachedTime = -1;
        }
    }
}
