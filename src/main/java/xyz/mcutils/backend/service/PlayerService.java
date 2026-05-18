package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerChangesDetectedMetric;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.FullPlayer;
import xyz.mcutils.backend.model.domain.player.history.RecentUsernameChange;
import xyz.mcutils.backend.model.domain.player.history.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.postgres.PlayerCapeAdoptionRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.PlayerSkinAdoptionRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;
import xyz.mcutils.backend.websocket.WebSocketManager;
import xyz.mcutils.backend.websocket.impl.NameChangeWebSocket;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class PlayerService {
    static final Duration PLAYER_UPDATE_INTERVAL = Duration.ofHours(3);
    private static final int MAX_PLAYER_SEARCH_RESULTS = 5;

    public static PlayerService INSTANCE;
    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerRepository playerRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;
    private final PlayerSkinAdoptionRepository playerSkinAdoptionRepository;
    private final PlayerCapeAdoptionRepository playerCapeAdoptionRepository;
    private final TransactionTemplate transactionTemplate;

    private final CoalescingLoader<String, PlayerRow> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService,
                         PlayerRepository playerRepository, UsernameChangeEventRepository usernameChangeEventRepository,
                         PlayerSkinAdoptionRepository playerSkinAdoptionRepository,
                         PlayerCapeAdoptionRepository playerCapeAdoptionRepository,
                         PlatformTransactionManager transactionManager) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRepository = playerRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        this.playerSkinAdoptionRepository = playerSkinAdoptionRepository;
        this.playerCapeAdoptionRepository = playerCapeAdoptionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    @Transactional
    public PlayerRow getPlayer(String query) {
        return playerLoader.get(query, () -> {
            boolean isUsername = query.length() <= 16;
            Optional<PlayerRow> optionalPlayerRow = isUsername ? this.playerRepository.findByUsernameIgnoreCase(query) : this.playerRepository.findById(UUIDUtils.parseUuid(query));
            if (optionalPlayerRow.isEmpty()) {
                UUID uuid = !isUsername ? UUIDUtils.parseUuid(query) : null;
                if (isUsername) {
                    MojangUsernameToUuidToken mojangUsernameToUuid = this.mojangService.getUuidFromUsername(query);
                    if (mojangUsernameToUuid == null) {
                        throw new NotFoundException("Player with username '%s' was not found".formatted(query));
                    }
                    uuid = UUIDUtils.parseUuid(mojangUsernameToUuid.uuid());
                }
                assert uuid != null;

                MojangProfileToken token = this.mojangService.getProfile(uuid.toString());
                if (token == null) {
                    throw new NotFoundException("Player '%s' was not found".formatted(query));
                }
                return this.createPlayer(token);
            }

            PlayerRow playerRow = optionalPlayerRow.get();
            if (playerRow.getLastUpdated().isBefore(Instant.now().minus(PLAYER_UPDATE_INTERVAL))) {
                MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
                if (token == null) {
                    throw new NotFoundException("Player '%s' was not found".formatted(query));
                }
                playerRow = this.updatePlayer(playerRow, token);
            }
            return playerRow;
        });
    }

    @Transactional
    public PlayerRow createPlayer(MojangProfileToken token) {
        UUID id = UUIDUtils.parseUuid(token.getId());
        Instant now = Instant.now();

        SkinRow skin = this.skinService.getOrCreateSkin(token.getSkinAndCape().left(), id);
        CapeTextureToken capeToken = token.getSkinAndCape().right();
        CapeRow cape = capeToken != null ? this.capeService.getOrCreateCape(capeToken, id) : null;

        PlayerRow playerRow = this.playerRepository.save(new PlayerRow(
                id,
                token.getName(),
                token.getLegacy() != null && token.getLegacy(),
                0,
                0,
                skin,
                cape,
                now,
                now
        ));

        this.playerSkinAdoptionRepository.insertFirstAdoption(id, skin.getId(), now);
        if (cape != null) {
            this.playerCapeAdoptionRepository.insertFirstAdoption(id, cape.getId(), now);
        }

        StatisticsService.addTrackedPlayerCount(1);
        return playerRow;
    }

    public PlayerRow updatePlayer(PlayerRow playerRow, MojangProfileToken token) {
        this.updatePlayers(Collections.singletonList(new PlayerUpdate(playerRow, token)));
        return this.playerRepository.findById(playerRow.getId()).orElseThrow();
    }

    public void savePlayers(List<MojangProfileToken> tokens) {
        Map<String, CompletableFuture<SkinRow>> skinFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().left())
                .collect(Collectors.toMap(
                        SkinTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() -> {
                            UUID firstPlayerId = UUIDUtils.parseUuid(tokens.stream()
                                    .filter(tok -> tok.getSkinAndCape().left().getTextureId().equals(t.getTextureId()))
                                    .findFirst().orElseThrow().getId());
                            return this.skinService.getOrCreateSkinCached(t, firstPlayerId);
                        }, Main.EXECUTOR),
                        (a, b) -> a));
        Map<String, CompletableFuture<CapeRow>> capeFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().right())
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        CapeTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() -> {
                            UUID firstPlayerId = UUIDUtils.parseUuid(tokens.stream()
                                    .filter(tok -> tok.getSkinAndCape().right() != null && tok.getSkinAndCape().right().getTextureId().equals(t.getTextureId()))
                                    .findFirst().orElseThrow().getId());
                            return this.capeService.getOrCreateCapeCached(t, firstPlayerId);
                        }, Main.EXECUTOR),
                        (a, b) -> a));

        CompletableFuture.allOf(Stream.concat(skinFutures.values().stream(), capeFutures.values().stream())
                .toArray(CompletableFuture[]::new)).join();

        Map<String, SkinRow> skinsByTextureId = skinFutures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));
        Map<String, CapeRow> capesByTextureId = capeFutures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));

        List<UUID> ids = tokens.stream().map(t -> UUIDUtils.parseUuid(t.getId())).toList();
        Set<UUID> existingIds = this.playerRepository.findExistingIds(ids);

        List<PlayerRow> playerRows = new ArrayList<>();
        // Pending adoption data collected per-player (not per unique texture).
        record SkinAdoption(UUID playerId, long skinId) {}
        record CapeAdoption(UUID playerId, long capeId) {}
        List<SkinAdoption> skinAdoptions = new ArrayList<>();
        List<CapeAdoption> capeAdoptions = new ArrayList<>();

        Instant now = Instant.now();
        for (MojangProfileToken token : tokens) {
            UUID id = UUIDUtils.parseUuid(token.getId());
            if (existingIds.contains(id)) {
                continue;
            }
            SkinRow skin = skinsByTextureId.get(token.getSkinAndCape().left().getTextureId());
            CapeTextureToken capeToken = token.getSkinAndCape().right();
            CapeRow cape = capeToken != null ? capesByTextureId.get(capeToken.getTextureId()) : null;

            skinAdoptions.add(new SkinAdoption(id, skin.getId()));
            if (cape != null) {
                capeAdoptions.add(new CapeAdoption(id, cape.getId()));
            }
            playerRows.add(new PlayerRow(
                    id,
                    token.getName(),
                    token.getLegacy() != null && token.getLegacy(),
                    0,
                    0,
                    skin,
                    cape,
                    now,
                    now
            ));
        }

        if (playerRows.isEmpty()) {
            return;
        }

        this.playerRepository.saveAll(playerRows);
        StatisticsService.addTrackedPlayerCount(playerRows.size());

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> {
                    for (SkinAdoption a : skinAdoptions) {
                        this.playerSkinAdoptionRepository.insertFirstAdoption(a.playerId(), a.skinId(), now);
                    }
                }, Main.EXECUTOR),
                CompletableFuture.runAsync(() -> {
                    for (CapeAdoption a : capeAdoptions) {
                        this.playerCapeAdoptionRepository.insertFirstAdoption(a.playerId(), a.capeId(), now);
                    }
                }, Main.EXECUTOR)
        ).join();
    }

    public void updatePlayers(List<PlayerUpdate> playerUpdates) {
        this.transactionTemplate.executeWithoutResult(_ -> {
            List<PlayerRow> playerRows = new ArrayList<>();
            List<UsernameChangeEventRow> usernameChangeEvents = new ArrayList<>();
            int skinChangeCount = 0;
            int capeChangeCount = 0;

            List<PlayerUpdate> sortedUpdates = playerUpdates.stream()
                    .sorted(Comparator.comparing(u -> u.playerRow().getId()))
                    .toList();

            List<UpdatePlayerResult> updatePlayerResults = new ArrayList<>();
            for (PlayerUpdate playerUpdate : sortedUpdates) {
                updatePlayerResults.add(this.applyPlayerUpdate(playerUpdate));
            }

            for (UpdatePlayerResult update : updatePlayerResults) {
                playerRows.add(update.playerRow());
                if (update.skinChanged()) {
                    skinChangeCount++;
                }
                if (update.capeChanged()) {
                    capeChangeCount++;
                }
                if (update.usernameChangeEventRow() != null) {
                    usernameChangeEvents.add(update.usernameChangeEventRow());
                }
            }

            this.playerRepository.saveAll(playerRows);
            this.usernameChangeEventRepository.saveAll(usernameChangeEvents);
            MetricService.getMetric(AccountsUpdatedMetric.class).inc(playerRows.size());
            MetricService.getMetric(PlayerChangesDetectedMetric.class).inc(skinChangeCount + capeChangeCount + usernameChangeEvents.size());
            StatisticsService.addNameChangesCount(usernameChangeEvents.size());

            for (UsernameChangeEventRow usernameChangeEvent : usernameChangeEvents) {
                WebSocketManager.getWebsocket(NameChangeWebSocket.class).sendMessageToAll(new RecentUsernameChange(
                        usernameChangeEvent.getPlayerId(),
                        usernameChangeEvent.getNewUsername(),
                        usernameChangeEvent.getPreviousUsername(),
                        usernameChangeEvent.getTimestamp()
                ));
            }
        });
    }

    private UpdatePlayerResult applyPlayerUpdate(PlayerUpdate playerUpdate) {
        PlayerRow playerRow = this.playerRepository.findByIdForUpdate(playerUpdate.playerRow().getId()).orElseThrow(() ->
                new NotFoundException("Player '%s' was not found".formatted(playerUpdate.playerRow().getId())));
        MojangProfileToken token = playerUpdate.token();

        boolean skinChanged = false;
        boolean capeChanged = false;
        UsernameChangeEventRow usernameChangeEventRow = null;

        if (playerRow.isLegacyAccount() != token.isLegacy()) {
            playerRow.setLegacyAccount(token.isLegacy());
        }

        Instant now = Instant.now();

        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        SkinTextureToken skinToken = skinAndCape.left();
        CapeTextureToken capeToken = skinAndCape.right();

        if (!playerRow.getSkin().getTextureId().equals(skinToken.getTextureId())) {
            SkinRow newSkin = this.skinService.getOrCreateSkinCached(skinToken, playerRow.getId());
            playerRow.setSkin(newSkin);
            this.playerSkinAdoptionRepository.recordEquip(playerRow.getId(), newSkin.getId(), now);
            skinChanged = true;
        }

        String oldCapeTextureId = playerRow.getCape() != null ? playerRow.getCape().getTextureId() : null;
        String newCapeTextureId = capeToken != null ? capeToken.getTextureId() : null;
        if (!Objects.equals(oldCapeTextureId, newCapeTextureId)) {
            CapeRow newCape = capeToken != null ? this.capeService.getOrCreateCapeCached(capeToken, playerRow.getId()) : null;
            playerRow.setCape(newCape);
            if (newCape != null) {
                this.playerCapeAdoptionRepository.recordEquip(playerRow.getId(), newCape.getId(), now);
            }
            capeChanged = true;
        }

        String previousUsername = playerRow.getUsername();
        if (!previousUsername.equals(token.getName())) {
            playerRow.setUsername(token.getName());
            usernameChangeEventRow = new UsernameChangeEventRow(playerRow.getId(), token.getName(), previousUsername, now);
        }

        playerRow.setLastUpdated(now);
        return new UpdatePlayerResult(playerRow, skinChanged, capeChanged, usernameChangeEventRow);
    }

    public Set<UsernameHistory> getUsernameHistory(PlayerRow player) {
        List<UsernameChangeEventRow> events = this.usernameChangeEventRepository
                .findByPlayerIdOrderByTimestampDesc(player.getId())
                .stream()
                .filter(row -> row.getPreviousUsername() != null)
                .toList();

        Set<UsernameHistory> history = events.stream()
                .map(row -> new UsernameHistory(row.getNewUsername(), row.getPreviousUsername(), row.getTimestamp()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String initialUsername = events.isEmpty()
                ? player.getUsername()
                : events.getLast().getPreviousUsername();
        history.add(new UsernameHistory(initialUsername, null, player.getFirstSeen()));

        return history;
    }

    public List<RecentUsernameChange> getRecentNameChanges() {
        return this.usernameChangeEventRepository.findRecentNameChanges(PageRequest.of(0, 50)).stream()
                .map(row -> new RecentUsernameChange(row.getPlayerId(), row.getNewUsername(), row.getPreviousUsername(), row.getTimestamp()))
                .toList();
    }

    public Set<Skin> getSkinHistory(PlayerRow player) {
        List<PlayerSkinAdoptionRow> adoptions = this.playerSkinAdoptionRepository.findByPlayerIdOrderByFirstSeenAsc(player.getId());
        return adoptions.stream().map(adoption -> {
            Skin skin = Skin.fromRow(adoption.getSkin());
            skin.setFirstSeen(adoption.getFirstSeen());
            return skin;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<VanillaCape> getCapeHistory(PlayerRow player) {
        List<PlayerCapeAdoptionRow> adoptions = this.playerCapeAdoptionRepository.findByPlayerIdOrderByFirstSeenAsc(player.getId());
        return adoptions.stream().map(adoption -> {
            VanillaCape cape = VanillaCape.fromRow(adoption.getCape());
            cape.setFirstSeen(adoption.getFirstSeen());
            return cape;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<FullPlayer> searchPlayers(String query) {
        return this.playerRepository.findByUsernameStartingWithIgnoreCase(query, Pageable.ofSize(MAX_PLAYER_SEARCH_RESULTS)).stream()
                .map(playerRow -> FullPlayer.fromRow(playerRow, this)).toList();
    }

    public List<PlayerRow> getTopSubmittedPlayers(int amount) {
        return this.playerRepository.findTopByOrderBySubmittedUuidsDesc(PageRequest.of(0, amount)).stream().toList();
    }

    public Set<UUID> getExistingPlayerIds(List<UUID> ids) {
        return this.playerRepository.findExistingIds(ids);
    }

    public void incrementSubmittedUuids(UUID playerId, long count) {
        this.playerRepository.incrementSubmittedUuids(playerId, count);
    }

    public record PlayerUpdate(PlayerRow playerRow, MojangProfileToken token) {}

    public record UpdatePlayerResult(PlayerRow playerRow, boolean skinChanged, boolean capeChanged,
                                     UsernameChangeEventRow usernameChangeEventRow) {}
}
