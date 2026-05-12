package xyz.mcutils.backend.common;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

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
    public void awaitAll(List<Future<?>> futures, String context) {
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                log.warn("{} task failed", context, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
