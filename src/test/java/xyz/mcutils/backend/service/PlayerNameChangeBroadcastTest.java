package xyz.mcutils.backend.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import tools.jackson.databind.json.JsonMapper;
import xyz.mcutils.backend.model.persistence.postgres.PlayerRow;
import xyz.mcutils.backend.model.persistence.postgres.SkinRow;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.NameChangeWebSocket;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A username change is on the wire by the time the refresh call that persisted it returns.
 */
class PlayerNameChangeBroadcastTest {

    private static final Instant LAST_UPDATED = Instant.parse("2026-01-01T00:00:00Z");

    private final List<String> pushedMessages = Collections.synchronizedList(new ArrayList<>());

    private PlayerRepository playerRepository;
    private PlayerService playerService;

    @BeforeAll
    static void registerMetrics() {
        // The persist path records into these metrics; an unregistered metric would NPE inside it.
        new MetricService(
                mock(PlayerSubmitService.class),
                mock(PlayerService.class),
                mock(MojangService.class),
                mock(StatisticsService.class)
        );
        // ... and bumps the name-change counter on the statistics singleton.
        new StatisticsService(null, null, null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        this.playerRepository = mock(PlayerRepository.class);
        UsernameChangeEventRepository usernameChangeEventRepository = mock(UsernameChangeEventRepository.class);
        SkinService skinService = mock(SkinService.class);
        CapeService capeService = mock(CapeService.class);

        this.playerService = new PlayerService(
                null,
                null,
                skinService,
                capeService,
                this.playerRepository,
                usernameChangeEventRepository,
                null,
                null,
                // Stands in for the @Lazy self proxy Spring injects, which owns the REQUIRES_NEW boundary.
                new PlayerService(null, null, skinService, capeService, this.playerRepository, usernameChangeEventRepository, null, null, null, 64),
                64
        );

        this.registerNameChangeClient();
    }

    @Test
    void pushesEachUsernameChangeTheMomentItIsPersisted() {
        UUID playerId = UUID.randomUUID();
        PlayerRow playerRow = playerRow(playerId, "OldName");
        when(this.playerRepository.findByIdForUpdate(playerId)).thenReturn(Optional.of(playerRow));

        assertTrue(this.playerService.persistPlayerRefresh(new PlayerService.PlayerUpdate(playerRow, profile(playerId, "NewName"))));

        assertEquals(1, this.pushedMessages.size());
        assertTrue(this.pushedMessages.getFirst().contains("NewName"), this.pushedMessages.getFirst());
        assertTrue(this.pushedMessages.getFirst().contains("OldName"), this.pushedMessages.getFirst());
        assertEquals("NewName", playerRow.getUsername());
    }

    @Test
    void pushesNothingWhenTheUsernameIsUnchanged() {
        UUID playerId = UUID.randomUUID();
        PlayerRow playerRow = playerRow(playerId, "SameName");
        when(this.playerRepository.findByIdForUpdate(playerId)).thenReturn(Optional.of(playerRow));

        assertTrue(this.playerService.persistPlayerRefresh(new PlayerService.PlayerUpdate(playerRow, profile(playerId, "SameName"))));

        assertTrue(this.pushedMessages.isEmpty());
    }

    /**
     * Connects a recording client to the real {@link NameChangeWebSocket}.
     */
    private void registerNameChangeClient() throws Exception {
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);
        when(registration.setAllowedOrigins(any())).thenReturn(registration);
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        when(registry.addHandler(any(WebSocketHandler.class), any(String[].class))).thenReturn(registration);
        new WebSocketManager(JsonMapper.builder().build()).registerWebSocketHandlers(registry);

        WebSocketSession client = mock(WebSocketSession.class);
        when(client.getId()).thenReturn("client-1");
        when(client.isOpen()).thenReturn(true);
        doAnswer(invocation -> {
            this.pushedMessages.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(client).sendMessage(any());

        ((NameChangeWebSocket) WebSocketManager.getWebsocket(NameChangeWebSocket.class)).afterConnectionEstablished(client);
    }

    private static PlayerRow playerRow(UUID playerId, String username) {
        SkinRow skin = mock(SkinRow.class);
        when(skin.getTextureId()).thenReturn("skin-texture");
        Instant nextRefreshAt = LAST_UPDATED.plus(Duration.ofHours(6));
        return new PlayerRow(playerId, username, false, 0, 0, skin, null, LAST_UPDATED, LAST_UPDATED, 0, nextRefreshAt);
    }

    /**
     * A profile without a textures payload: only the username can differ from the stored row.
     */
    private static MojangProfileToken profile(UUID playerId, String username) {
        return new MojangProfileToken(playerId.toString(), username, false, new MojangProfileToken.ProfileProperty[0]);
    }
}
