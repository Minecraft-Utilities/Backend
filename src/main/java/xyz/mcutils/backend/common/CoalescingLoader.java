package xyz.mcutils.backend.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Ensures at most one load runs per key at a time; concurrent callers for the same key
 * share the same result (request coalescing). Useful to avoid thundering herd when many
 * requests need the same resource (e.g. same skin texture for different image parts).
 * <p>
 * When a load completes, the key is removed so later requests perform a fresh load;
 * combine with your own cache (e.g. storage) if you want to avoid repeated work.
 */
public final class CoalescingLoader<K, V> {

    private final ConcurrentHashMap<K, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    private final Executor executor;

    public CoalescingLoader(Executor executor) {
        this.executor = executor;
    }

    /**
     * Returns the value for the key, loading it via {@code loader} if necessary.
     * Concurrent calls for the same key share a single load.
     *
     * @param key    coalescing key (e.g. texture id, or "id|options")
     * @param loader supplies the value; runs at most once per key while in-flight
     * @return the loaded value
     * @throws RuntimeException if the loader throws (or any failure), unwrapped from {@link CompletionException}
     */
    public V get(K key, Supplier<V> loader) {
        CompletableFuture<V> future = inFlight.computeIfAbsent(key, k ->
                CompletableFuture.supplyAsync(loader, executor)
                        .whenComplete((v, ex) -> executor.execute(() -> inFlight.remove(key))));
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(cause != null ? cause : e);
        }
    }
}
