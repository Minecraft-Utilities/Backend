package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Utility methods for working with {@link Future}s.
 */
@UtilityClass
@Slf4j
public class FutureUtils {
    /**
     * Awaits all futures, logging execution failures with the given context label.
     * Restores the interrupt flag and stops waiting early if the thread is interrupted.
     *
     * @param futures the futures to await
     * @param context a short label used in warning log messages (e.g. "player refresh")
     */
    public <T> List<T> awaitAll(List<Future<T>> futures, String context) {
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            try {
                T result = f.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (ExecutionException e) {
                log.warn("{} task failed", context, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return results;
    }
}
