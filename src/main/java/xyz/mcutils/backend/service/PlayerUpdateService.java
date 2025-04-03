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
    private final PlayerRepository playerRepository;
    private final PlayerCacheRepository playerCacheRepository;
    private final PlayerUpdateQueueRepository playerUpdateQueueRepository;
    private final PlayerService playerService;
    private final MojangService mojangService;

    private final Queue<PlayerUpdateQueueItem> memoryQueue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock queueLock = new ReentrantLock();

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

    /**
     * Gets the oldest item from the queue and updates the player.
     * <p>
     * This method is scheduled to run every second.
     * </p>
     */
    @Scheduled(fixedRate = 1_000)
    public void updatePlayer() {
        // If both queues are empty, try to fill them
        if (memoryQueue.isEmpty() && playerUpdateQueueRepository.count() == 0) {
            this.insertToQueue();
            return;
        }

        // Get the oldest item from memory queue
        PlayerUpdateQueueItem queueItem = memoryQueue.peek();
        if (queueItem == null) {
            // If memory queue is empty but Redis has items, reload from Redis
            reloadMemoryQueueFromRedis();
            return;
        }

        try {
            CachedPlayer cachedPlayer = playerService.getCachedPlayer(queueItem.getUuid().toString());
            Player player = cachedPlayer.getPlayer();
            player.refresh(mojangService, playerService, playerRepository, false);

            UUID submitterUuid = queueItem.getSubmitterUuid();
            if (submitterUuid != null) {
                CachedPlayer submitter = playerService.getCachedPlayer(submitterUuid.toString());
                Player submitterPlayer = submitter.getPlayer();
                submitterPlayer.setUuidsContributed(submitterPlayer.getUuidsContributed() + 1);

                playerRepository.save(submitterPlayer); // Update the submitter
                playerCacheRepository.save(submitter); // Update the submitter in the cache

                log.info("{} has contributed by submitting {} UUIDs", submitterPlayer.getUsername(), submitterPlayer.getUuidsContributed());
            }
        } catch (Exception ex) {
            log.error("Failed to update player with UUID: {}, error: {}", queueItem.getUuid(), ex.getMessage());
        } finally {
            // Remove from both queues
            queueLock.lock();
            try {
                memoryQueue.poll(); // Remove from memory
                playerUpdateQueueRepository.delete(queueItem); // Remove from Redis
                log.debug("Processed player update for UUID: {}", queueItem.getUuid());
            } finally {
                queueLock.unlock();
            }
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
        PageRequest pageRequest = PageRequest.of(
                0,
                20,
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