package xyz.mcutils.backend.common;

/**
 * A cooldown system for rate limiting with burst support and priority levels
 */
public class Cooldown {
    private long lastUsed;
    private long lastRefresh;
    private int remainingBursts;
    
    // Separate burst buckets for different priorities
    private int backgroundBursts;
    private long backgroundLastRefresh;
    
    private final long cooldownMs;
    private final int maxBursts;
    private final long backgroundCooldownMs;
    private final int maxBackgroundBursts;
    
    /**
     * Creates a cooldown with the specified parameters
     * 
     * @param cooldownMs the cooldown time in milliseconds
     * @param maxBursts the maximum number of burst tokens
     * @param backgroundCooldownMs the background cooldown time (optional, defaults to 10x slower)
     * @param maxBackgroundBursts the maximum number of background burst tokens
     */
    public Cooldown(long cooldownMs, int maxBursts, Long backgroundCooldownMs, int maxBackgroundBursts) {
        this.cooldownMs = cooldownMs;
        this.maxBursts = maxBursts;
        this.maxBackgroundBursts = maxBackgroundBursts;
        
        // Background cooldown defaults to 10x slower if not specified
        this.backgroundCooldownMs = backgroundCooldownMs != null ? backgroundCooldownMs : cooldownMs * 10;
        
        long now = System.currentTimeMillis();
        this.lastUsed = now;
        this.lastRefresh = now;
        this.remainingBursts = maxBursts;
        this.backgroundLastRefresh = now;
        this.backgroundBursts = maxBackgroundBursts;
    }
    
    /**
     * Creates a cooldown with default background settings
     */
    public Cooldown(long cooldownMs, int maxBursts) {
        this(cooldownMs, maxBursts, null, 1);
    }
    
    /**
     * Use the cooldown. Will use a burst if available, otherwise updates the last used time.
     *
     * @param priority the priority level
     * @return true if the cooldown was ready and is now used, false if it wasn't ready
     */
    public boolean use(CooldownPriority priority) {
        if (priority == CooldownPriority.BACKGROUND) {
            return useBackground();
        }
        
        refreshBursts();
        
        if (remainingBursts > 0) {
            remainingBursts--;
            lastUsed = System.currentTimeMillis();
            return true;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastUsed >= cooldownMs) {
            lastUsed = now;
            return true;
        }
        return false;
    }
    
    /**
     * Use the cooldown with NORMAL priority
     */
    public boolean use() {
        return use(CooldownPriority.NORMAL);
    }
    
    private boolean useBackground() {
        refreshBackgroundBursts();
        
        if (backgroundBursts > 0) {
            backgroundBursts--;
            lastUsed = System.currentTimeMillis();
            return true;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastUsed >= backgroundCooldownMs) {
            lastUsed = now;
            return true;
        }
        return false;
    }
    
    private void refreshBursts() {
        long now = System.currentTimeMillis();
        long timeSinceRefresh = now - lastRefresh;
        
        if (timeSinceRefresh >= cooldownMs) {
            int newTokens = (int) (timeSinceRefresh / cooldownMs);
            
            // Update the refresh timestamp to account for the tokens we're adding
            lastRefresh += newTokens * cooldownMs;
            
            // Add new tokens, up to maxBursts
            remainingBursts = Math.min(maxBursts, remainingBursts + newTokens);
        }
    }
    
    private void refreshBackgroundBursts() {
        long now = System.currentTimeMillis();
        long timeSinceRefresh = now - backgroundLastRefresh;
        
        if (timeSinceRefresh >= backgroundCooldownMs) {
            int newTokens = (int) (timeSinceRefresh / backgroundCooldownMs);
            
            // Update the refresh timestamp to account for the tokens we're adding
            backgroundLastRefresh += newTokens * backgroundCooldownMs;
            
            // Add new tokens, up to maxBackgroundBursts
            backgroundBursts = Math.min(maxBackgroundBursts, backgroundBursts + newTokens);
        }
    }
    
    /**
     * Check if the cooldown is ready
     */
    public boolean isReady(CooldownPriority priority) {
        if (priority == CooldownPriority.BACKGROUND) {
            refreshBackgroundBursts();
            return backgroundBursts > 0 || System.currentTimeMillis() - lastUsed >= backgroundCooldownMs;
        }
        
        refreshBursts();
        return remainingBursts > 0 || System.currentTimeMillis() - lastUsed >= cooldownMs;
    }
    
    /**
     * Check if the cooldown is ready with NORMAL priority
     */
    public boolean isReady() {
        return isReady(CooldownPriority.NORMAL);
    }
    
    /**
     * Get the remaining time until next available use
     */
    public long getRemainingTime(CooldownPriority priority) {
        if (priority == CooldownPriority.BACKGROUND) {
            refreshBackgroundBursts();
            if (backgroundBursts > 0) return 0;
            
            long timeSinceUse = System.currentTimeMillis() - lastUsed;
            long remaining = backgroundCooldownMs - timeSinceUse;
            return Math.max(0, remaining);
        }
        
        refreshBursts();
        if (remainingBursts > 0) return 0;
        
        long timeSinceUse = System.currentTimeMillis() - lastUsed;
        long remaining = cooldownMs - timeSinceUse;
        return Math.max(0, remaining);
    }
    
    /**
     * Get the remaining time with NORMAL priority
     */
    public long getRemainingTime() {
        return getRemainingTime(CooldownPriority.NORMAL);
    }
    
    /**
     * Wait for the cooldown to be ready and consume it
     */
    public void waitAndUse(CooldownPriority priority) throws InterruptedException {
        while (!use(priority)) {
            awaitCooldown(priority);
        }
    }
    
    /**
     * Wait for the cooldown to be ready and consume it with NORMAL priority
     */
    public void waitAndUse() throws InterruptedException {
        waitAndUse(CooldownPriority.NORMAL);
    }
    
    /**
     * Wait for the cooldown to be ready
     */
    public void awaitCooldown(CooldownPriority priority) throws InterruptedException {
        long remainingTime = getRemainingTime(priority) * getCooldownMultiplier(priority);
        if (remainingTime > 0) {
            Thread.sleep(remainingTime);
        }
    }
    
    /**
     * Wait for the cooldown to be ready with NORMAL priority
     */
    public void awaitCooldown() throws InterruptedException {
        awaitCooldown(CooldownPriority.NORMAL);
    }
    
    /**
     * Get the cooldown multiplier for a given priority
     */
    public int getCooldownMultiplier(CooldownPriority priority) {
        // Adjust cooldown based on priority
        if (priority == CooldownPriority.LOW) {
            return 2;
        }
        return 1;
    }
    
    /**
     * Reset the cooldown and restore all bursts
     */
    public void reset() {
        long now = System.currentTimeMillis();
        lastUsed = now;
        remainingBursts = maxBursts;
        backgroundBursts = maxBackgroundBursts;
    }
    
    /**
     * Get the number of remaining burst tokens available
     */
    public int getRemainingBursts(CooldownPriority priority) {
        if (priority == CooldownPriority.BACKGROUND) {
            refreshBackgroundBursts();
            return backgroundBursts;
        }
        
        refreshBursts();
        return remainingBursts;
    }
    
    /**
     * Get the number of remaining burst tokens with NORMAL priority
     */
    public int getRemainingBursts() {
        return getRemainingBursts(CooldownPriority.NORMAL);
    }
    
    /**
     * Calculates the cooldown in milliseconds for a given number of requests per minute.
     */
    public static long cooldownRequestsPerMinute(int requestsPerMinute) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requestsPerMinute must be positive, got: " + requestsPerMinute);
        }
        
        // Ensure we don't get extremely small values that could cause timing issues
        long cooldownMs = 60_000L / requestsPerMinute;
        
        // Cap at a reasonable minimum (e.g., 1ms) to prevent timing issues
        return Math.max(1, cooldownMs);
    }
}
