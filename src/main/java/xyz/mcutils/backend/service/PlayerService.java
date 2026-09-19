package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.metric.impl.player.AccountsUpdatedMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerChangesDetectedMetric;
import xyz.mcutils.backend.metric.impl.player.PlayerRefreshMetric;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.history.RecentUsernameChange;
import xyz.mcutils.backend.model.domain.player.history.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangNameHistoryToken;
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
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class PlayerService {
    static final Duration PLAYER_UPDATE_INTERVAL = PlayerRefreshSchedule.BASE_INTERVAL;
    private static final int MAX_PLAYER_SEARCH_RESULTS = 5;
    /**
     * Max concurrent per-player refresh transactions (each uses REQUIRES_NEW + row lock).
     * Keep well below {@code spring.datasource.hikari.maximum-pool-size} to leave connections
     * for API traffic, submit queue, and skin/cape resolution during prepare.
     */
    private final Semaphore persistSemaphore;

    public static PlayerService INSTANCE;
    private final MojangService mojangService;
    private final MojangRateLimiter mojangRateLimiter;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerRepository playerRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;
    private final PlayerSkinAdoptionRepository playerSkinAdoptionRepository;
    private final PlayerCapeAdoptionRepository playerCapeAdoptionRepository;
    private final PlayerService self;

    private final CoalescingLoader<String, PlayerRow> playerLoader = new CoalescingLoader<>(Runnable::run);

    public PlayerService(MojangService mojangService, MojangRateLimiter mojangRateLimiter, SkinService skinService, CapeService capeService,
                         PlayerRepository playerRepository, UsernameChangeEventRepository usernameChangeEventRepository,
                         PlayerSkinAdoptionRepository playerSkinAdoptionRepository, PlayerCapeAdoptionRepository playerCapeAdoptionRepository,
                         @Lazy PlayerService self,
                         @Value("${mc-utils.player-refresh.concurrent-persists:64}") int concurrentPersists) {
        this.mojangService = mojangService;
        this.mojangRateLimiter = mojangRateLimiter;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRepository = playerRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        this.playerSkinAdoptionRepository = playerSkinAdoptionRepository;
        this.playerCapeAdoptionRepository = playerCapeAdoptionRepository;
        this.self = self;
        this.persistSemaphore = new Semaphore(concurrentPersists);
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    @Transactional
    public PlayerRow getPlayer(String query) {
        return playerLoader.get(query, () -> {
            boolean isUsername = query.length() <= 16;
            UUID parsedUuid = isUsername ? null : UUIDUtils.parseUuid(query);
            Optional<PlayerRow> optionalPlayerRow = isUsername ? this.playerRepository.findByUsernameIgnoreCase(query) : this.playerRepository.findById(parsedUuid);
            if (optionalPlayerRow.isEmpty()) {
                UUID uuid = parsedUuid;
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
            // Only refresh on-demand for viewed players; the background loop owns the long tail.
            // The shared limiter keeps this path inside the same Mojang budget as the background
            // refresh and submit queue (previously this bypassed all rate limiting).
            if (playerRow.getNextRefreshAt().isBefore(Instant.now()) && playerRow.getMonthlyViews() > 0) {
                Main.EXECUTOR.execute(() -> {
                    try {
                        this.mojangRateLimiter.acquire();
                        MojangProfileToken token = this.mojangService.getProfile(playerRow.getId().toString());
                        if (token == null) {
                            this.bumpRefreshFailure(playerRow.getId());
                            return;
                        }
                        this.self.updatePlayer(playerRow, token);
                    } catch (Exception e) {
                        this.bumpRefreshFailure(playerRow.getId());
                    }
                });
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

        Instant nextRefreshAt = now.plus(PlayerRefreshSchedule.BASE_INTERVAL);

        PlayerRow playerRow = this.playerRepository.save(new PlayerRow(
                id,
                token.getName(),
                token.getLegacy() != null && token.getLegacy(),
                0,
                0,
                skin,
                cape,
                now,
                now,
                0,
                nextRefreshAt
        ));

        this.playerSkinAdoptionRepository.save(new PlayerSkinAdoptionRow(id, skin.getId(), now, null));
        if (cape != null) {
            this.playerCapeAdoptionRepository.save(new PlayerCapeAdoptionRow(id, cape.getId(), now, null));
        }

        StatisticsService.addTrackedPlayerCount(1);
        return playerRow;
    }

    public void updatePlayer(PlayerRow playerRow, MojangProfileToken token) {
        this.updatePlayers(Collections.singletonList(new PlayerUpdate(playerRow, token)));
    }

    @Transactional
    public void createPlayers(List<MojangProfileToken> tokens) {
        // Precompute the first player owning each distinct texture once (O(N)); previously each
        // per-texture future re-scanned the whole token list, making this O(distinct x N).
        Map<String, UUID> firstSkinOwnerByTextureId = new HashMap<>();
        Map<String, UUID> firstCapeOwnerByTextureId = new HashMap<>();
        for (MojangProfileToken token : tokens) {
            SkinTextureToken skin = token.getSkinAndCape().left();
            firstSkinOwnerByTextureId.computeIfAbsent(skin.getTextureId(), _ -> UUIDUtils.parseUuid(token.getId()));
            CapeTextureToken cape = token.getSkinAndCape().right();
            if (cape != null) {
                firstCapeOwnerByTextureId.computeIfAbsent(cape.getTextureId(), _ -> UUIDUtils.parseUuid(token.getId()));
            }
        }

        Map<String, CompletableFuture<SkinRow>> skinFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().left())
                .collect(Collectors.toMap(
                        SkinTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() ->
                                this.skinService.getOrCreateSkinCached(t, firstSkinOwnerByTextureId.get(t.getTextureId())),
                                Main.EXECUTOR),
                        (a, b) -> a));

        Map<String, CompletableFuture<CapeRow>> capeFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().right())
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        CapeTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() ->
                                this.capeService.getOrCreateCapeCached(t, firstCapeOwnerByTextureId.get(t.getTextureId())),
                                Main.EXECUTOR),
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
        List<PlayerSkinAdoptionRow> skinAdoptions = new ArrayList<>();
        List<PlayerCapeAdoptionRow> capeAdoptions = new ArrayList<>();

        Instant now = Instant.now();
        for (MojangProfileToken token : tokens) {
            UUID id = UUIDUtils.parseUuid(token.getId());
            if (existingIds.contains(id)) {
                continue;
            }
            SkinRow skin = skinsByTextureId.get(token.getSkinAndCape().left().getTextureId());
            CapeTextureToken capeToken = token.getSkinAndCape().right();
            CapeRow cape = capeToken != null ? capesByTextureId.get(capeToken.getTextureId()) : null;

            skinAdoptions.add(new PlayerSkinAdoptionRow(id, skin.getId(), now, null));
            if (cape != null) {
                capeAdoptions.add(new PlayerCapeAdoptionRow(id, cape.getId(), now, null));
            }
            playerRows.add(new PlayerRow(id, token.getName(), token.getLegacy() != null && token.getLegacy(),
                    0, 0, skin, cape, now, now, 0, now.plus(PlayerRefreshSchedule.BASE_INTERVAL)));
        }

        if (playerRows.isEmpty()) {
            return;
        }

        this.playerRepository.saveAll(playerRows);
        this.playerSkinAdoptionRepository.saveAll(skinAdoptions);
        if (!capeAdoptions.isEmpty()) {
            this.playerCapeAdoptionRepository.saveAll(capeAdoptions);
        }
        StatisticsService.addTrackedPlayerCount(playerRows.size());
    }

    /**
     * Prepares and persists a single player refresh. Used by the background refresh pipeline.
     */
    public PersistPlayerRefreshResult persistPlayerRefresh(PlayerUpdate playerUpdate) {
        try {
            PreparedPlayerUpdate prepared = preparePlayerUpdate(playerUpdate);
            this.persistSemaphore.acquire();
            try {
                UsernameChangeEventRow usernameChangeEvent = this.self.persistPlayerUpdate(prepared);
                return new PersistPlayerRefreshResult(true, usernameChangeEvent);
            } finally {
                this.persistSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while refreshing player {}", playerUpdate.playerRow().getId());
            this.bumpRefreshFailure(playerUpdate.playerRow().getId());
            return new PersistPlayerRefreshResult(false, null);
        } catch (Exception e) {
            log.warn("Failed to refresh player {}: {}", playerUpdate.playerRow().getId(), e.toString());
            log.debug("Failed to refresh player {}", playerUpdate.playerRow().getId(), e);
            this.bumpRefreshFailure(playerUpdate.playerRow().getId());
            return new PersistPlayerRefreshResult(false, null);
        }
    }

    public void updatePlayers(List<PlayerUpdate> playerUpdates) {
        if (playerUpdates.isEmpty()) {
            return;
        }

        List<PlayerUpdate> sortedUpdates = playerUpdates.stream()
                .sorted(Comparator.comparing(u -> u.playerRow().getId()))
                .toList();

        List<CompletableFuture<PersistPlayerRefreshResult>> futures = sortedUpdates.stream()
                .map(playerUpdate -> CompletableFuture.supplyAsync(
                        () -> this.persistPlayerRefresh(playerUpdate),
                        Main.EXECUTOR))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<UsernameChangeEventRow> usernameChangeEvents = new ArrayList<>();
        for (CompletableFuture<PersistPlayerRefreshResult> future : futures) {
            PersistPlayerRefreshResult result = future.join();
            if (result.success() && result.usernameChangeEvent() != null) {
                usernameChangeEvents.add(result.usernameChangeEvent());
            }
        }
        broadcastUsernameChanges(usernameChangeEvents);
    }

    void broadcastUsernameChanges(List<UsernameChangeEventRow> usernameChangeEvents) {
        for (UsernameChangeEventRow usernameChangeEvent : usernameChangeEvents) {
            WebSocketManager.getWebsocket(NameChangeWebSocket.class).sendMessageToAll(new RecentUsernameChange(
                    usernameChangeEvent.getPlayerId(),
                    usernameChangeEvent.getNewUsername(),
                    usernameChangeEvent.getPreviousUsername(),
                    usernameChangeEvent.getTimestamp()
            ));
        }
    }

    /**
     * Resolves skin/cape rows without holding a player row lock.
     */
    private PreparedPlayerUpdate preparePlayerUpdate(PlayerUpdate playerUpdate) {
        PlayerRow snapshot = playerUpdate.playerRow();
        MojangProfileToken token = playerUpdate.token();
        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        SkinTextureToken skinToken = skinAndCape != null ? skinAndCape.left() : null;
        CapeTextureToken capeToken = skinAndCape != null ? skinAndCape.right() : null;

        SkinRow newSkin = null;
        if (skinToken != null && !snapshot.getSkin().getTextureId().equals(skinToken.getTextureId())) {
            newSkin = this.skinService.getOrCreateSkinCached(skinToken, snapshot.getId());
        }

        // A profile without a textures payload (null skinAndCape) means Mojang returned no skin/cape
        // data at all; keep the stored assets unchanged instead of treating them as removed.
        String oldCapeTextureId = snapshot.getCape() != null ? snapshot.getCape().getTextureId() : null;
        String newCapeTextureId = capeToken != null ? capeToken.getTextureId() : null;
        boolean capeChanged = skinAndCape != null && !Objects.equals(oldCapeTextureId, newCapeTextureId);
        CapeRow newCape = null;
        if (capeChanged && capeToken != null) {
            newCape = this.capeService.getOrCreateCapeCached(capeToken, snapshot.getId());
        }

        // Exact adoption time of a changed username, from Mojang's name history; null when unknown.
        Instant usernameChangedAt = null;
        if (!snapshot.getUsername().equals(token.getName())) {
            usernameChangedAt = fetchNameChangeTimestamp(snapshot.getId().toString(), token.getName());
        }

        return new PreparedPlayerUpdate(snapshot.getId(), token, newSkin, newCape, capeChanged, usernameChangedAt);
    }

    /**
     * Resolves the exact adoption time of the current username from Mojang's name history so
     * name-change events carry the real change timestamp instead of the poll time. Returns
     * {@code null} when the history is unavailable or the current name has no recorded change
     * time (e.g. it is the account's original name); callers then fall back to the refresh time.
     */
    private Instant fetchNameChangeTimestamp(String uuid, String currentName) {
        try {
            this.mojangRateLimiter.acquire();
            List<MojangNameHistoryToken> history = this.mojangService.getNameHistory(uuid);
            for (int i = history.size() - 1; i >= 0; i--) {
                MojangNameHistoryToken entry = history.get(i);
                if (entry.name().equalsIgnoreCase(currentName)) {
                    return entry.changedToAt() != null ? Instant.ofEpochMilli(entry.changedToAt()) : null;
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Name history lookup failed for {}: {}", uuid, e.toString());
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UsernameChangeEventRow persistPlayerUpdate(PreparedPlayerUpdate prepared) {
        Instant now = Instant.now();
        Optional<PlayerRow> locked = this.playerRepository.findByIdForUpdate(prepared.playerId());
        if (locked.isEmpty()) {
            return null;
        }

        PlayerRow playerRow = locked.get();
        MojangProfileToken token = prepared.token();
        int changeCount = 0;
        Long equippedSkinId = null;
        Long equippedCapeId = null;

        if (playerRow.isLegacyAccount() != token.isLegacy()) {
            playerRow.setLegacyAccount(token.isLegacy());
        }

        if (prepared.newSkin() != null && !playerRow.getSkin().getTextureId().equals(prepared.newSkin().getTextureId())) {
            playerRow.setSkin(prepared.newSkin());
            equippedSkinId = prepared.newSkin().getId();
            changeCount++;
            MetricService.getMetric(PlayerChangesDetectedMetric.class).record(PlayerChangesDetectedMetric.SKIN);
        }

        if (prepared.capeChanged()) {
            String currentCapeTextureId = playerRow.getCape() != null ? playerRow.getCape().getTextureId() : null;
            String targetCapeTextureId = prepared.newCape() != null ? prepared.newCape().getTextureId() : null;
            if (!Objects.equals(currentCapeTextureId, targetCapeTextureId)) {
                playerRow.setCape(prepared.newCape());
                if (prepared.newCape() != null) {
                    equippedCapeId = prepared.newCape().getId();
                }
                changeCount++;
                MetricService.getMetric(PlayerChangesDetectedMetric.class).record(PlayerChangesDetectedMetric.CAPE);
            }
        }

        UsernameChangeEventRow usernameChangeEventRow = null;
        String previousUsername = playerRow.getUsername();
        if (!previousUsername.equals(token.getName())) {
            playerRow.setUsername(token.getName());
            Instant eventTime = prepared.usernameChangedAt() != null ? prepared.usernameChangedAt() : now;
            usernameChangeEventRow = new UsernameChangeEventRow(playerRow.getId(), token.getName(), previousUsername, eventTime);
            changeCount++;
            MetricService.getMetric(PlayerChangesDetectedMetric.class).record(PlayerChangesDetectedMetric.USERNAME);
        }

        boolean hadChanges = changeCount > 0;
        double velocity = PlayerRefreshSchedule.updateVelocity(
                playerRow.getChangeVelocity(), playerRow.getLastUpdated(), now, hadChanges);
        Duration interval = PlayerRefreshSchedule.nextRefreshInterval(hadChanges, velocity, playerRow.getMonthlyViews());
        playerRow.setChangeVelocity(velocity);
        playerRow.setNextRefreshAt(now.plus(interval));
        playerRow.setLastUpdated(now);
        this.playerRepository.save(playerRow);
        MetricService.getMetric(PlayerRefreshMetric.class).recordInterval(interval);
        if (equippedSkinId != null) {
            recordSkinEquips(List.of(new PlayerSkinAdoptionRow(playerRow.getId(), equippedSkinId, now, now)), now);
        }
        if (equippedCapeId != null) {
            recordCapeEquips(List.of(new PlayerCapeAdoptionRow(playerRow.getId(), equippedCapeId, now, now)), now);
        }
        if (usernameChangeEventRow != null) {
            this.usernameChangeEventRepository.save(usernameChangeEventRow);
            StatisticsService.addNameChangesCount(1);
        }

        MetricService.getMetric(AccountsUpdatedMetric.class).inc(1);
        return usernameChangeEventRow;
    }

    private record PreparedPlayerUpdate(
            UUID playerId,
            MojangProfileToken token,
            SkinRow newSkin,
            CapeRow newCape,
            boolean capeChanged,
            Instant usernameChangedAt
    ) {}

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

    @Transactional(readOnly = true)
    public Set<Skin> getSkinHistory(PlayerRow player) {
        List<PlayerSkinAdoptionRow> adoptions = this.playerSkinAdoptionRepository.findByPlayerIdOrderByFirstSeenAsc(player.getId());
        return adoptions.stream()
                .filter(adoption -> adoption.getSkin() != null)
                .map(adoption -> {
                    Skin skin = Skin.fromRow(adoption.getSkin());
                    skin.setFirstSeen(adoption.getFirstSeen());
                    return skin;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Set<VanillaCape> getCapeHistory(PlayerRow player) {
        List<PlayerCapeAdoptionRow> adoptions = this.playerCapeAdoptionRepository.findByPlayerIdOrderByFirstSeenAsc(player.getId());
        return adoptions.stream()
                .filter(adoption -> adoption.getCape() != null)
                .map(adoption -> {
                    VanillaCape cape = VanillaCape.fromRow(adoption.getCape());
                    cape.setFirstSeen(adoption.getFirstSeen());
                    return cape;
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<PlayerRow> searchPlayers(String query) {
        return this.playerRepository.findByUsernameStartingWithIgnoreCase(query, Pageable.ofSize(MAX_PLAYER_SEARCH_RESULTS));
    }

    public List<PlayerRow> getTopSubmittedPlayers(int amount) {
        return this.playerRepository.findAllByOrderBySubmittedUuidsDesc(PageRequest.of(0, amount)).stream().toList();
    }

    public Set<UUID> getExistingPlayerIds(List<UUID> ids) {
        return this.playerRepository.findExistingIds(ids);
    }

    public void incrementSubmittedUuids(UUID playerId, long count) {
        this.playerRepository.incrementSubmittedUuids(playerId, count);
    }

    public void bumpRefreshFailure(UUID playerId) {
        this.playerRepository.bumpRefreshFailure(
                List.of(playerId),
                Instant.now().plus(PlayerRefreshSchedule.FAILURE_BACKOFF)
        );
    }

    public void bumpRefreshFailures(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        this.playerRepository.bumpRefreshFailure(
                playerIds,
                Instant.now().plus(PlayerRefreshSchedule.FAILURE_BACKOFF)
        );
    }

    private void recordSkinEquips(List<PlayerSkinAdoptionRow> equips, Instant timestamp) {
        if (equips.isEmpty()) {
            return;
        }
        Set<PlayerSkinAdoptionId> ids = equips.stream()
                .map(row -> new PlayerSkinAdoptionId(row.getPlayerId(), row.getSkinId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<PlayerSkinAdoptionId, PlayerSkinAdoptionRow> existing = this.playerSkinAdoptionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(row -> new PlayerSkinAdoptionId(row.getPlayerId(), row.getSkinId()), row -> row));
        List<PlayerSkinAdoptionRow> toSave = new ArrayList<>();
        for (PlayerSkinAdoptionRow equip : equips) {
            PlayerSkinAdoptionId id = new PlayerSkinAdoptionId(equip.getPlayerId(), equip.getSkinId());
            PlayerSkinAdoptionRow row = existing.get(id);
            if (row == null) {
                row = new PlayerSkinAdoptionRow(equip.getPlayerId(), equip.getSkinId(), timestamp, timestamp);
            } else {
                row.setLastEquippedAt(timestamp);
            }
            toSave.add(row);
        }
        this.playerSkinAdoptionRepository.saveAll(toSave);
    }

    private void recordCapeEquips(List<PlayerCapeAdoptionRow> equips, Instant timestamp) {
        if (equips.isEmpty()) {
            return;
        }
        Set<PlayerCapeAdoptionId> ids = equips.stream()
                .map(row -> new PlayerCapeAdoptionId(row.getPlayerId(), row.getCapeId()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<PlayerCapeAdoptionId, PlayerCapeAdoptionRow> existing = this.playerCapeAdoptionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(row -> new PlayerCapeAdoptionId(row.getPlayerId(), row.getCapeId()), row -> row));
        List<PlayerCapeAdoptionRow> toSave = new ArrayList<>();
        for (PlayerCapeAdoptionRow equip : equips) {
            PlayerCapeAdoptionId id = new PlayerCapeAdoptionId(equip.getPlayerId(), equip.getCapeId());
            PlayerCapeAdoptionRow row = existing.get(id);
            if (row == null) {
                row = new PlayerCapeAdoptionRow(equip.getPlayerId(), equip.getCapeId(), timestamp, timestamp);
            } else {
                row.setLastEquippedAt(timestamp);
            }
            toSave.add(row);
        }
        this.playerCapeAdoptionRepository.saveAll(toSave);
    }

    public record PlayerUpdate(PlayerRow playerRow, MojangProfileToken token) {}

    public record PersistPlayerRefreshResult(boolean success, UsernameChangeEventRow usernameChangeEvent) {}
}
