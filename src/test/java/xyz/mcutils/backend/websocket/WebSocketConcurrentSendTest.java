package xyz.mcutils.backend.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;
import xyz.mcutils.backend.model.domain.player.history.RecentUsernameChange;
import xyz.mcutils.backend.websocket.impl.NameChangeWebSocket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A fan-out now runs from many threads at once: a session must see one writer at a time and
 * lose no push.
 */
class WebSocketConcurrentSendTest {

    private static final int PUSHERS = 16;
    private static final int PUSHES_PER_PUSHER = 25;

    @Test
    void serializesConcurrentPushesPerSessionAndDropsNothing() throws Exception {
        NameChangeWebSocket webSocket = new NameChangeWebSocket();
        webSocket.setJsonMapper(JsonMapper.builder().build());

        List<String> received = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger writers = new AtomicInteger();
        AtomicInteger overlappingWrites = new AtomicInteger();

        WebSocketSession client = mock(WebSocketSession.class);
        when(client.getId()).thenReturn("client-1");
        when(client.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            if (writers.incrementAndGet() > 1) {
                overlappingWrites.incrementAndGet();
            }
            // Models the duration of a real socket write.
            Thread.sleep(1);
            received.add(((TextMessage) invocation.getArgument(0)).getPayload());
            writers.decrementAndGet();
            return null;
        }).when(client).sendMessage(any());

        webSocket.afterConnectionEstablished(client);

        ExecutorService pool = Executors.newFixedThreadPool(PUSHERS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> pushes = new ArrayList<>();
            for (int pusher = 0; pusher < PUSHERS; pusher++) {
                int index = pusher;
                pushes.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < PUSHES_PER_PUSHER; i++) {
                        webSocket.sendMessageToAll(new RecentUsernameChange(
                                UUID.randomUUID(),
                                "name-" + index + "-" + i,
                                "previous",
                                Instant.now()
                        ));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> push : pushes) {
                push.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(0, overlappingWrites.get(), "a session must never be written by two threads at once");
        assertEquals(PUSHERS * PUSHES_PER_PUSHER, received.size(), "every push must reach the client");
        verify(client, never()).close();
        verify(client, never()).close(any(CloseStatus.class));
    }
}
