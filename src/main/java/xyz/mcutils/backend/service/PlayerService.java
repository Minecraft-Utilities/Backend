package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
    static final Duration PLAYER_UPDATE_INTERVAL = PlayerRefreshSchedule.BASE_INTERVAL;
    private static final int MAX_PLAYER_SEARCH_RESULTS = 5;

    public static PlayerService INSTANCE;
    private final MojangService mojangService;
    private final SkinService skinService;
    private final CapeService capeService;
    private final PlayerRepository playerRepository;
    private final UsernameChangeEventRepository usernameChangeEventRepository;
    private final PlayerSkinAdoptionRepository playerSkinAdoptionRepository;
    private final PlayerCapeAdoptionRepository playerCapeAdoptionRepository;
    private final PlayerService self;

    private final CoalescingLoader<String, PlayerRow> playerLoader = new CoalescingLoader<>(Runnable::run);

    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService,
                         PlayerRepository playerRepository, UsernameChangeEventRepository usernameChangeEventRepository,
                         PlayerSkinAdoptionRepository playerSkinAdoptionRepository, PlayerCapeAdoptionRepository playerCapeAdoptionRepository,
                         @Lazy PlayerService self) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRepository = playerRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        this.playerSkinAdoptionRepository = playerSkinAdoptionRepository;
        this.playerCapeAdoptionRepository = playerCapeAdoptionRepository;
        this.self = self;
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
            if (playerRow.getNextRefreshAt().isBefore(Instant.now())) {
                Main.EXECUTOR.execute(() -> {
                    try {
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
        Map<String, CompletableFuture<SkinRow>> skinFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().left())
                .collect(Collectors.toMap(
                        SkinTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() -> {
                            UUID firstPlayerId = UUIDUtils.parseUuid(tokens.stream()
                                    .filter(tok -> Objects.equals(tok.getSkinAndCape().left().getTextureId(), t.getTextureId()))
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

    public void updatePlayers(List<PlayerUpdate> playerUpdates) {
        List<PlayerUpdate> sortedUpdates = playerUpdates.stream()
                .sorted(Comparator.comparing(u -> u.playerRow().getId()))
                .toList();

        List<UsernameChangeEventRow> usernameChangeEvents = new ArrayList<>();
        for (PlayerUpdate playerUpdate : sortedUpdates) {
            try {
                PreparedPlayerUpdate prepared = preparePlayerUpdate(playerUpdate);
                UsernameChangeEventRow usernameChangeEvent = this.self.persistPlayerUpdate(prepared);
                if (usernameChangeEvent != null) {
                    usernameChangeEvents.add(usernameChangeEvent);
                }
            } catch (Exception e) {
                log.warn("Failed to refresh player {}: {}", playerUpdate.playerRow().getId(), e.toString());
                log.debug("Failed to refresh player {}", playerUpdate.playerRow().getId(), e);
                this.bumpRefreshFailure(playerUpdate.playerRow().getId());
            }
        }

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
        SkinTextureToken skinToken = skinAndCape.left();
        CapeTextureToken capeToken = skinAndCape.right();

        SkinRow newSkin = null;
        if (!snapshot.getSkin().getTextureId().equals(skinToken.getTextureId())) {
            newSkin = this.skinService.getOrCreateSkinCached(skinToken, snapshot.getId());
        }

        String oldCapeTextureId = snapshot.getCape() != null ? snapshot.getCape().getTextureId() : null;
        String newCapeTextureId = capeToken != null ? capeToken.getTextureId() : null;
        boolean capeChanged = !Objects.equals(oldCapeTextureId, newCapeTextureId);
        CapeRow newCape = null;
        if (capeChanged && capeToken != null) {
            newCape = this.capeService.getOrCreateCapeCached(capeToken, snapshot.getId());
        }

        return new PreparedPlayerUpdate(snapshot.getId(), token, newSkin, newCape, capeChanged);
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
            }
        }

        UsernameChangeEventRow usernameChangeEventRow = null;
        String previousUsername = playerRow.getUsername();
        if (!previousUsername.equals(token.getName())) {
            playerRow.setUsername(token.getName());
            usernameChangeEventRow = new UsernameChangeEventRow(playerRow.getId(), token.getName(), previousUsername, now);
            changeCount++;
        }

        boolean hadChanges = changeCount > 0;
        double velocity = PlayerRefreshSchedule.updateVelocity(
                playerRow.getChangeVelocity(), playerRow.getLastUpdated(), now, hadChanges);
        Duration interval = PlayerRefreshSchedule.intervalFor(velocity, playerRow.getMonthlyViews());
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
        if (changeCount > 0) {
            MetricService.getMetric(PlayerChangesDetectedMetric.class).inc(changeCount);
        }
        return usernameChangeEventRow;
    }

    private record PreparedPlayerUpdate(
            UUID playerId,
            MojangProfileToken token,
            SkinRow newSkin,
            CapeRow newCape,
            boolean capeChanged
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

    public List<FullPlayer> searchPlayers(String query) {
        return this.playerRepository.findByUsernameStartingWithIgnoreCase(query, Pageable.ofSize(MAX_PLAYER_SEARCH_RESULTS)).stream()
                .map(playerRow -> FullPlayer.fromRow(playerRow, this)).toList();
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
        Instant now = Instant.now();
        this.playerRepository.bumpRefreshFailure(
                List.of(playerId),
                now,
                now.plus(PlayerRefreshSchedule.FAILURE_BACKOFF)
        );
    }

    public void bumpRefreshFailures(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        this.playerRepository.bumpRefreshFailure(
                playerIds,
                now,
                now.plus(PlayerRefreshSchedule.FAILURE_BACKOFF)
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
}
