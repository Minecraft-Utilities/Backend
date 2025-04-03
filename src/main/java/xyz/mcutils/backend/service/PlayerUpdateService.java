package xyz.mcutils.backend.service;

import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xyz.mcutils.backend.common.Tuple;
import xyz.mcutils.backend.model.cache.CachedPlayer;
import xyz.mcutils.backend.model.player.Cape;
import xyz.mcutils.backend.model.player.Player;
import xyz.mcutils.backend.model.player.PlayerUpdateQueueItem;
import xyz.mcutils.backend.model.skin.Skin;
import xyz.mcutils.backend.model.token.MojangProfileToken;
import xyz.mcutils.backend.repository.mongo.PlayerRepository;
import xyz.mcutils.backend.repository.redis.PlayerUpdateQueueRepository;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service @Log4j2(topic = "Player Update Service")
public class PlayerUpdateService {
    private final PlayerRepository playerRepository;
    private final PlayerUpdateQueueRepository playerUpdateQueueRepository;
    private final PlayerService playerService;
    private final MojangService mojangService;

    private final Queue<PlayerUpdateQueueItem> memoryQueue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock queueLock = new ReentrantLock();

    @Autowired
    public PlayerUpdateService(@NonNull PlayerRepository playerRepository,
                               @NonNull PlayerUpdateQueueRepository playerUpdateQueueRepository,
                               @NonNull PlayerService playerService,
                               @NonNull MojangService mojangService) {
        this.playerRepository = playerRepository;
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

    @Scheduled(fixedRate = 3000)
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
            cachedPlayer.getPlayer().refresh(mojangService, playerService, playerRepository, false);
        } catch (Exception ex) {
            log.error("Failed to update player with UUID: {}", queueItem.getUuid(), ex);
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
                    .map(player -> new PlayerUpdateQueueItem(player.getUniqueId(), System.currentTimeMillis()))
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
}