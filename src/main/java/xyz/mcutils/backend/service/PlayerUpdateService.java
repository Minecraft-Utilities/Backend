package xyz.mcutils.backend.service;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.Proxies;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.player.PlayerUpdateQueueItem;
import xyz.mcutils.backend.model.player.UUIDSubmission;
import xyz.mcutils.backend.model.response.UUIDSubmissionResponse;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.mongo.history.CapeHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.SkinHistoryRepository;
import xyz.mcutils.backend.repository.mongo.history.UsernameHistoryRepository;
import xyz.mcutils.backend.repository.redis.PlayerUpdateQueueRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;
import xyz.mcutils.backend.common.Cooldown;
import xyz.mcutils.backend.common.CooldownPriority;

@Service @Log4j2(topic = "Player Update Service")
public class PlayerUpdateService {
    private static final ThreadPoolExecutor EXECUTOR = (ThreadPoolExecutor) Executors.newFixedThreadPool(Proxies.getTotalProxies() * 4);
    
    // Rate limiting: 150 requests per minute per proxy
    private final Cooldown cooldown = new Cooldown(Cooldown.cooldownRequestsPerMinute(150 * Proxies.getTotalProxies()), 30);

    private final PlayerRepository playerRepository;
    private final PlayerUpdateQueueRepository playerUpdateQueueRepository;
    private final PlayerService playerService;
    private final MojangService mojangService;

    private final SkinHistoryRepository skinHistoryRepository;
    private final CapeHistoryRepository capeHistoryRepository;
    private final UsernameHistoryRepository usernameHistoryRepository;

    @Autowired
    public PlayerUpdateService(@NonNull PlayerRepository playerRepository, @NonNull PlayerUpdateQueueRepository playerUpdateQueueRepository, @NonNull PlayerService playerService,
                               @NonNull MojangService mojangService, @NonNull SkinHistoryRepository skinHistoryRepository, @NonNull CapeHistoryRepository capeHistoryRepository,
                               @NonNull UsernameHistoryRepository usernameHistoryRepository) {
        this.playerRepository = playerRepository;
        this.playerUpdateQueueRepository = playerUpdateQueueRepository;
        this.playerService = playerService;
        this.mojangService = mojangService;
        this.skinHistoryRepository = skinHistoryRepository;
        this.capeHistoryRepository = capeHistoryRepository;
        this.usernameHistoryRepository = usernameHistoryRepository;
    }

    @Scheduled(fixedRate = 50) // Run every 50ms
    public void runQueue() {
        if (EXECUTOR.getActiveCount() >= EXECUTOR.getMaximumPoolSize()) {
            return;
        }

        // Check if we can process a queue item (rate limiting)
        if (!cooldown.isReady()) {
            return;
        }

        // Run it async so we don't block the thread
        EXECUTOR.execute(this::processQueue);
    }

    @Scheduled(fixedRate = 30_000)
    public void refreshQueue() {
        if (playerUpdateQueueRepository.count() > 0) {
            return;
        }
        insertToQueue();
    }

    /**
     * Gets the oldest item from the queue and updates the player.
     */
    public void processQueue() {
        // Use the cooldown to respect rate limits
        try {
            cooldown.waitAndUse();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        // Find the oldest item in Redis queue
        PlayerUpdateQueueItem queueItem = playerUpdateQueueRepository.findFirstByOrderByTimeAddedAsc();
        if (queueItem == null) {
            return;
        }

        // Remove from Redis queue after fetching it
        playerUpdateQueueRepository.delete(queueItem);
        
        long start = System.currentTimeMillis();
        log.info("Processing queue item \"{}\"", queueItem.getUuid());
        
        try {
            boolean playerExists = playerRepository.existsById(queueItem.getUuid());
            Player player = playerService.getPlayer(queueItem.getUuid().toString(), false);

            // Refresh data (Mojang API) if the player exists
            if (playerExists) {
                player.refresh(mojangService, skinHistoryRepository, capeHistoryRepository, usernameHistoryRepository);
            }

            // Update submitter stats
            if (queueItem.getSubmitterUuid() != null // Submitter is not null
                    && !playerExists // Player has not existed before
                    && !queueItem.getSubmitterUuid().equals(queueItem.getUuid()) // Submitter is not the same as the player
            ) {
                Player submitter = playerService.getPlayer(queueItem.getSubmitterUuid().toString(), false);
                submitter.setUuidsContributed(submitter.getUuidsContributed() + 1);
                playerRepository.save(submitter);
                log.info("Incremented contributions for {} to {}", submitter.getUsername(), submitter.getUuidsContributed());

                // Set the contributed by for the player
                player.setContributedBy(submitter.getUniqueId());
            }

            // Save the player
            playerRepository.save(player);

            log.info("Finished processing queue item \"{}\" in {}ms ({} left)",
                    queueItem.getUuid(),
                    System.currentTimeMillis() - start,
                    playerUpdateQueueRepository.count()
            );
        } catch (Exception ex) {
            log.error("Failed to update player {}: {}", queueItem.getUuid(), ex.getMessage());
        }
    }

    /**
     * Inserts all players in the queue into Redis.
     */
    public void insertToQueue() {
        PageRequest pageRequest = PageRequest.of(
                0,
                500,
                Sort.by(Sort.Direction.ASC, "lastUpdated")
        );
        List<Player> players = playerRepository.findPlayersLastUpdatedBefore(
                System.currentTimeMillis() - (24 * 60 * 60 * 1000), // 24 hours ago or more
                pageRequest
        );

        List<PlayerUpdateQueueItem> newItems = players.stream()
                .map(player -> new PlayerUpdateQueueItem(player.getUniqueId(), null, System.currentTimeMillis()))
                .collect(Collectors.toList());

        // Add to Redis queue
        playerUpdateQueueRepository.saveAll(newItems);

        int size = newItems.size();
        if (size > 0) {
            log.info("Added {} items to Redis queue", size);
        }
    }

    /**
     * Adds the UUIDs to the database.
     *
     * @param submission the object containing the UUIDs to ingest
     * @return the number of UUIDs added
     */
    public UUIDSubmissionResponse submitUUIDs(UUIDSubmission submission) {
        List<PlayerUpdateQueueItem> queueItems = new ArrayList<>();

        int added = 0;
        for (UUID uuid : submission.getUuids()) {
            // Check if the player exists already
            if (playerRepository.existsById(uuid)) {
                continue;
            }

            // Check if the player is already in the Redis queue
            if (playerUpdateQueueRepository.existsById(uuid)) {
                continue;
            }

            queueItems.add(new PlayerUpdateQueueItem(uuid, submission.getAccountUuid(), System.currentTimeMillis()));
            added++;
        }

        Player player = submission.getAccountUuid() != null ? playerService.getPlayer(submission.getAccountUuid().toString(), false) : null;
        if (added > 0) {
            playerUpdateQueueRepository.saveAll(queueItems);

            log.info("{} UUIDs have been submitted{}", added, player != null ? " by " + player.getUsername() : "");
        }

        return new UUIDSubmissionResponse(added, player != null ? player.getUuidsContributed() : 0, (int) playerRepository.count());
    }
}