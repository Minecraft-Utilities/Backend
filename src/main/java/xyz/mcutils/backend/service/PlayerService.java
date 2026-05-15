package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.mcutils.backend.Main;
import xyz.mcutils.backend.common.CoalescingLoader;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.common.UUIDUtils;
import xyz.mcutils.backend.exception.impl.NotFoundException;
import xyz.mcutils.backend.model.domain.cape.impl.VanillaCape;
import xyz.mcutils.backend.model.domain.player.FullPlayer;
import xyz.mcutils.backend.model.domain.player.history.UsernameHistory;
import xyz.mcutils.backend.model.domain.skin.Skin;
import xyz.mcutils.backend.model.persistence.postgres.*;
import xyz.mcutils.backend.model.token.mojang.CapeTextureToken;
import xyz.mcutils.backend.model.token.mojang.MojangProfileToken;
import xyz.mcutils.backend.model.token.mojang.MojangUsernameToUuidToken;
import xyz.mcutils.backend.model.token.mojang.SkinTextureToken;
import xyz.mcutils.backend.repository.postgres.CapeChangeEventRepository;
import xyz.mcutils.backend.repository.postgres.PlayerRepository;
import xyz.mcutils.backend.repository.postgres.SkinChangeEventRepository;
import xyz.mcutils.backend.repository.postgres.UsernameChangeEventRepository;

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
    private final SkinChangeEventRepository skinChangeEventRepository;
    private final CapeChangeEventRepository capeChangeEventRepository;

    private final CoalescingLoader<String, FullPlayer> playerLoader = new CoalescingLoader<>(Main.EXECUTOR);

    public PlayerService(MojangService mojangService, SkinService skinService, CapeService capeService, PlayerRepository playerRepository,
                         UsernameChangeEventRepository usernameChangeEventRepository, SkinChangeEventRepository skinChangeEventRepository,
                         CapeChangeEventRepository capeChangeEventRepository) {
        this.mojangService = mojangService;
        this.skinService = skinService;
        this.capeService = capeService;
        this.playerRepository = playerRepository;
        this.usernameChangeEventRepository = usernameChangeEventRepository;
        this.skinChangeEventRepository = skinChangeEventRepository;
        this.capeChangeEventRepository = capeChangeEventRepository;
    }

    @PostConstruct
    public void init() {
        INSTANCE = this;
    }

    public FullPlayer getPlayer(String query) {
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
            return FullPlayer.fromRow(playerRow, this);
        });
    }

    @Transactional
    public FullPlayer createPlayer(MojangProfileToken token) {
        UUID id = UUIDUtils.parseUuid(token.getId());
        SkinRow skin = this.skinService.getOrCreateSkinCached(token.getSkinAndCape().left());
        CapeTextureToken capeToken = token.getSkinAndCape().right();
        CapeRow cape = capeToken != null ? this.capeService.getOrCreateCapeCached(capeToken) : null;

        this.skinChangeEventRepository.save(new SkinChangeEventRow(id, skin, Instant.now()));
        if (cape != null) {
            this.capeChangeEventRepository.save(new CapeChangeEventRow(id, cape, Instant.now()));
        }
        this.usernameChangeEventRepository.save(new UsernameChangeEventRow(id, token.getName(), null, Instant.now()));

        FullPlayer fullPlayer = FullPlayer.fromRow(this.playerRepository.save(new PlayerRow(
                id,
                token.getName(),
                token.getLegacy() != null && token.getLegacy(),
                0, skin, cape,
                Instant.now(),
                Instant.now()
        )), this);
        StatisticsService.addTrackedPlayerCount(1);
        return fullPlayer;
    }

    public PlayerRow updatePlayer(PlayerRow playerRow, MojangProfileToken token) {
        PlayerUpdate update = new PlayerUpdate(playerRow, token);
        this.updatePlayers(Collections.singletonList(update));
        return update.playerRow();
    }

    public void savePlayers(List<MojangProfileToken> tokens) {
        Map<String, CompletableFuture<SkinRow>> skinFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().left())
                .collect(Collectors.toMap(
                        SkinTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() -> this.skinService.getOrCreateSkinCached(t), Main.EXECUTOR),
                        (a, b) -> a));
        Map<String, CompletableFuture<CapeRow>> capeFutures = tokens.stream()
                .map(t -> t.getSkinAndCape().right())
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        CapeTextureToken::getTextureId,
                        t -> CompletableFuture.supplyAsync(() -> this.capeService.getOrCreateCapeCached(t), Main.EXECUTOR),
                        (a, b) -> a));

        CompletableFuture.allOf(Stream.concat(skinFutures.values().stream(), capeFutures.values().stream())
                .toArray(CompletableFuture[]::new)).join();

        Map<String, SkinRow> skinsByTextureId = skinFutures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));
        Map<String, CapeRow> capesByTextureId = capeFutures.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().join()));

        List<PlayerRow> playerRows = new ArrayList<>();
        List<SkinChangeEventRow> skinChangeEvents = new ArrayList<>();
        List<CapeChangeEventRow> capeChangeEvents = new ArrayList<>();
        List<UsernameChangeEventRow> usernameChangeEvents = new ArrayList<>();

        for (MojangProfileToken token : tokens) {
            UUID id = UUIDUtils.parseUuid(token.getId());
            SkinRow skin = skinsByTextureId.get(token.getSkinAndCape().left().getTextureId());
            CapeTextureToken capeToken = token.getSkinAndCape().right();
            CapeRow cape = capeToken != null ? capesByTextureId.get(capeToken.getTextureId()) : null;

            skinChangeEvents.add(new SkinChangeEventRow(id, skin, Instant.now()));
            if (cape != null) {
                capeChangeEvents.add(new CapeChangeEventRow(id, cape, Instant.now()));
            }
            usernameChangeEvents.add(new UsernameChangeEventRow(id, token.getName(), null, Instant.now()));
            playerRows.add(new PlayerRow(
                    id,
                    token.getName(),
                    token.getLegacy() != null && token.getLegacy(),
                    0, skin, cape,
                    Instant.now(),
                    Instant.now()
            ));
        }

        this.playerRepository.saveAll(playerRows);
        StatisticsService.addTrackedPlayerCount(playerRows.size());

        CompletableFuture.allOf(
                CompletableFuture.runAsync(() -> this.skinChangeEventRepository.saveAll(skinChangeEvents), Main.EXECUTOR),
                CompletableFuture.runAsync(() -> this.capeChangeEventRepository.saveAll(capeChangeEvents), Main.EXECUTOR),
                CompletableFuture.runAsync(() -> this.usernameChangeEventRepository.saveAll(usernameChangeEvents), Main.EXECUTOR)
        ).join();
    }

    public void updatePlayers(List<PlayerUpdate> playerUpdates) {
        List<PlayerRow> playerRows = new ArrayList<>();
        List<SkinChangeEventRow> skinChangeEvents = new ArrayList<>();
        List<CapeChangeEventRow> capeChangeEvents = new ArrayList<>();
        List<UsernameChangeEventRow> usernameChangeEvents = new ArrayList<>();

        List<UpdatePlayerResult> updatePlayerResults = new ArrayList<>();
        for (PlayerUpdate playerUpdate : playerUpdates) {
            updatePlayerResults.add(this.updatePlayer(playerUpdate));
        }

        for (UpdatePlayerResult update : updatePlayerResults) {
            playerRows.add(update.playerRow());
            if (update.skinChangeEventRow() != null) {
                skinChangeEvents.add(update.skinChangeEventRow());
            }
            if (update.capeChangeEventRow() != null) {
                capeChangeEvents.add(update.capeChangeEventRow());
            }
            if (update.usernameChangeEventRow() != null) {
                usernameChangeEvents.add(update.usernameChangeEventRow());
            }
        }

        this.playerRepository.saveAll(playerRows);
        this.skinChangeEventRepository.saveAll(skinChangeEvents);
        this.capeChangeEventRepository.saveAll(capeChangeEvents);
        this.usernameChangeEventRepository.saveAll(usernameChangeEvents);
    }

    @Transactional
    public UpdatePlayerResult updatePlayer(PlayerUpdate playerUpdate) {
        PlayerRow playerRow = playerUpdate.playerRow();
        MojangProfileToken token = playerUpdate.token();

        SkinChangeEventRow skinChangeEventRow = null;
        CapeChangeEventRow capeChangeEventRow = null;
        UsernameChangeEventRow usernameChangeEventRow = null;

        if (playerRow.isLegacyAccount() != token.isLegacy()) {
            playerRow.setLegacyAccount(token.isLegacy());
        }

        Tuple<SkinTextureToken, CapeTextureToken> skinAndCape = token.getSkinAndCape();
        SkinTextureToken skinToken = skinAndCape.left();
        CapeTextureToken capeToken = skinAndCape.right();
        if (!playerRow.getSkin().getTextureId().equals(skinToken.getTextureId())) {
            SkinRow newSkin = this.skinService.getOrCreateSkinCached(skinToken);
            playerRow.setSkin(newSkin);
            skinChangeEventRow = new SkinChangeEventRow(playerRow.getId(), newSkin, Instant.now());
        }
        String oldCapeTextureId = playerRow.getCape() != null ? playerRow.getCape().getTextureId() : null;
        String newCapeTextureId = capeToken != null ? capeToken.getTextureId() : null;
        if (!Objects.equals(oldCapeTextureId, newCapeTextureId)) {
            CapeRow newCape = capeToken != null ? this.capeService.getOrCreateCapeCached(capeToken) : null;
            playerRow.setCape(newCape);
            if (newCape != null) {
                capeChangeEventRow = new CapeChangeEventRow(playerRow.getId(), newCape, Instant.now());
            }
        }

        String previousUsername = playerRow.getUsername();
        if (!previousUsername.equals(token.getName())) {
            playerRow.setUsername(token.getName());
            usernameChangeEventRow = new UsernameChangeEventRow(playerRow.getId(), token.getName(), previousUsername, Instant.now());
        }

        playerRow.setLastUpdated(Instant.now());
        return new UpdatePlayerResult(
                playerRow,
                skinChangeEventRow,
                capeChangeEventRow,
                usernameChangeEventRow
        );
    }

    public Set<UsernameHistory> getUsernameHistory(PlayerRow player) {
        return this.usernameChangeEventRepository.findByPlayerIdOrderByTimestampDesc(player.getId()).stream().map((row) ->
                new UsernameHistory(row.getNewUsername(), row.getPreviousUsername(), row.getTimestamp())).collect(Collectors.toSet());
    }

    public Set<Skin> getSkinHistory(PlayerRow player) {
        List<SkinChangeEventRow> events = this.skinChangeEventRepository.findByPlayerId(player.getId());
        if (events.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> skinIds = events.stream().map(e -> e.getSkin().getId()).collect(Collectors.toSet());
        Map<Long, Instant> firstSeenBySkinId = this.skinChangeEventRepository.findFirstTimestampsBySkinIds(player.getId(), skinIds)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Instant) row[1]));
        return events.stream().map(row -> {
            Skin skin = Skin.fromRow(row.getSkin());
            skin.setFirstSeen(firstSeenBySkinId.get(skin.getId()));
            return skin;
        }).collect(Collectors.toSet());
    }

    public Set<VanillaCape> getCapeHistory(PlayerRow player) {
        List<CapeChangeEventRow> events = this.capeChangeEventRepository.findByPlayerId(player.getId());
        if (events.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> capeIds = events.stream().map(e -> e.getCape().getId()).collect(Collectors.toSet());
        Map<Long, Instant> firstSeenByCapeId = this.capeChangeEventRepository.findFirstTimestampsByCapeIds(player.getId(), capeIds)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Instant) row[1]));
        return events.stream().map(row -> {
            VanillaCape cape = VanillaCape.fromRow(row.getCape());
            cape.setFirstSeen(firstSeenByCapeId.get(cape.getId()));
            return cape;
        }).collect(Collectors.toSet());
    }

    public List<FullPlayer> searchPlayers(String query) {
        return this.playerRepository.findByUsernameContainingIgnoreCase(query, Pageable.ofSize(MAX_PLAYER_SEARCH_RESULTS)).stream()
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
    public record UpdatePlayerResult(PlayerRow playerRow, SkinChangeEventRow skinChangeEventRow, CapeChangeEventRow capeChangeEventRow,
                                     UsernameChangeEventRow usernameChangeEventRow) {}
}
