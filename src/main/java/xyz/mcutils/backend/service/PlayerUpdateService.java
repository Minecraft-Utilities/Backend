package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.player.PlayerUpdateQueueItem;
import xyz.mcutils.backend.model.player.UUIDSubmission;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.redis.PlayerCacheRepository;
import xyz.mcutils.backend.repository.redis.PlayerUpdateQueueRepository;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service @Log4j2(topic = "Player Update Service")
public class PlayerUpdateService {
    private static final int QUEUE_INTERVAL_MS = 400; // 150 executions per minute (60,000ms / 150 = 400ms)

    private final PlayerRepository playerRepository;
    private final PlayerCacheRepository playerCacheRepository;
    private final PlayerUpdateQueueRepository playerUpdateQueueRepository;
    private final PlayerService playerService;
    private final MojangService mojangService;

    private final Queue<PlayerUpdateQueueItem> memoryQueue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock queueLock = new ReentrantLock();
    private long lastQueueTime = -1;

    @Autowired
    public PlayerUpdateService(@NonNull PlayerRepository playerRepository,
                               @NonNull PlayerCacheRepository playerCacheRepository,
                               @NonNull PlayerUpdateQueueRepository playerUpdateQueueRepository,
                               @NonNull PlayerService playerService,
                               @NonNull MojangService mojangService) {
        this.playerRepository = playerRepository;
        this.playerCacheRepository = playerCacheRepository;
        this.playerUpdateQueueRepository = playerUpdateQueueRepository;
        this.playerService = playerService;
        this.mojangService = mojangService;
    }

    @PostConstruct
    public void init() {
        // On startup, load the queue from Redis into memory
        queueLock.lock();
        try {
            Iterable<PlayerUpdateQueueItem> redisQueue = playerUpdateQueueRepository.findAll();
            redisQueue.forEach(memoryQueue::add);
            log.info("Loaded {} items from Redis queue into memory", memoryQueue.size());
        } finally {
            queueLock.unlock();
        }
    }

    @Scheduled(fixedRate = 50) // Run every 50ms
    public void runQueue() {
        if (lastQueueTime == -1) {
            lastQueueTime = System.currentTimeMillis();
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastQueueTime < QUEUE_INTERVAL_MS) {
            return;
        }
        lastQueueTime = currentTime;

        if (memoryQueue.isEmpty()) {
            return;
        }

        // Add lock to ensure only one thread processes the queue at a time
        if (queueLock.tryLock()) {
            try {
                processQueue();
            } finally {
                queueLock.unlock();
            }
        } else {
            log.debug("Queue is already being processed by another thread");
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void refreshQueue() {
        if (!memoryQueue.isEmpty()) {
            return;
        }
        insertToQueue();
    }

    /**
     * Gets the oldest item from the queue and updates the player.
     */
    public void processQueue() {
        if (memoryQueue.isEmpty()) {
            return;
        }

        PlayerUpdateQueueItem queueItem = memoryQueue.peek();
        if (queueItem == null) {
            return;
        }

        long start = System.currentTimeMillis();
        log.info("Processing queue item \"{}\"", queueItem.getUuid());
        try {
            boolean playerExists = playerRepository.existsById(queueItem.getUuid());

            // getCachedPlayer will create the player if it doesn't exist
            CachedPlayer cachedPlayer = playerService.getCachedPlayer(queueItem.getUuid().toString());
            Player player = cachedPlayer.getPlayer();

            // Refresh data (Mojang API) if the player exists
            if (playerExists) {
                player.refresh(cachedPlayer, mojangService, playerRepository);
            }

            // Update submitter stats
            if (queueItem.getSubmitterUuid() != null // Submitter is not null
                    && !playerExists // Player has not existed before
                    && !queueItem.getSubmitterUuid().equals(queueItem.getUuid()) // Submitter is not the same as the player
            ) {
                CachedPlayer cachedSubmitter = playerService.getCachedPlayer(queueItem.getSubmitterUuid().toString());
                Player submitter = cachedSubmitter.getPlayer();
                submitter.setUuidsContributed(submitter.getUuidsContributed() + 1);
                playerRepository.save(submitter);
                playerCacheRepository.save(cachedSubmitter);
                log.info("Incremented contributions for {} to {}", submitter.getUsername(), submitter.getUuidsContributed());

                // Set the contributed by for the player
                player.setContributedBy(submitter.getUniqueId());
            }
            playerCacheRepository.save(cachedPlayer);
            playerRepository.save(player);
        } catch (Exception ex) {
            log.error("Failed to update player {}: {}", queueItem.getUuid(), ex.getMessage());
            ex.printStackTrace();
        } finally {
            // Remove from queues
            memoryQueue.poll();
            playerUpdateQueueRepository.delete(queueItem);

            log.info("Finished processing queue item \"{}\" in {}ms ({} left)",
                    queueItem.getUuid(),
                    System.currentTimeMillis() - start,
                    memoryQueue.size()
            );
        }
    }

    /**
     * Reloads the memory queue from Redis.
     */
    private void reloadMemoryQueueFromRedis() {
        queueLock.lock();
        try {
            memoryQueue.clear();
            Iterable<PlayerUpdateQueueItem> redisQueue = playerUpdateQueueRepository.findAll();
            redisQueue.forEach(memoryQueue::add);
            log.info("Reloaded {} items from queues", memoryQueue.size());
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Inserts all players in the queue into the memory queue.
     */
    public void insertToQueue() {
        log.info("Inserting players to queue...");
        PageRequest pageRequest = PageRequest.of(
                0,
                200,
                Sort.by(Sort.Direction.ASC, "lastUpdated")
        );
        List<Player> players = playerRepository.findPlayersLastUpdatedBefore(
                System.currentTimeMillis() - (24 * 60 * 60 * 1000), // 24 hours ago or more
                pageRequest
        );

        queueLock.lock();
        try {
            List<PlayerUpdateQueueItem> newItems = players.stream()
                    .map(player -> new PlayerUpdateQueueItem(player.getUniqueId(), null, System.currentTimeMillis()))
                    .collect(Collectors.toList());

            // Add to both queues
            memoryQueue.addAll(newItems);
            playerUpdateQueueRepository.saveAll(newItems);

            int size = newItems.size();
            if (size > 0) {
                log.info("Added {} items to queues", size);
            }
        } finally {
            queueLock.unlock();
        }
    }

    /**
     * Adds the UUIDs to the database.
     *
     * @param submission the object containing the UUIDs to ingest
     * @return the number of UUIDs added
     */
    public int submitUUIDs(UUIDSubmission submission) {
        List<PlayerUpdateQueueItem> queueItems = new ArrayList<>();

        int added = 0;
        for (UUID uuid : submission.getUuids()) {
            // Check if the player exists already
            if (playerRepository.existsById(uuid)) {
                continue;
            }

            // Check if the player is already in the queue
            Optional<PlayerUpdateQueueItem> existingQueueItem = memoryQueue.stream()
                    .filter(item -> item.getUuid().equals(uuid))
                    .findFirst();
            if (existingQueueItem.isPresent()) {
                continue;
            }

            queueItems.add(new PlayerUpdateQueueItem(uuid, submission.getAccountUuid(), System.currentTimeMillis()));
            added++;
        }

        memoryQueue.addAll(queueItems);
        playerUpdateQueueRepository.saveAll(queueItems);

        Player player = submission.getAccountUuid() != null ? playerService.getCachedPlayer(submission.getAccountUuid().toString()).getPlayer() : null;
        log.info("{} UUIDs have been submitted{}", added, player != null ? " by " + player.getUsername() : "");
        return added;
    }
}