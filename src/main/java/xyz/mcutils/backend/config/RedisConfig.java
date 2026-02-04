package xyz.mcutils.backend.config;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;

/**
 * @author Braydon
 */
@Configuration
@Slf4j
@EnableRedisRepositories(basePackages = "xyz.mcutils.backend.repository")
public class RedisConfig {
    /**
     * The Redis server host.
     */
    @Value("${mc-utils.redis.host}")
    private String host;

    /**
     * The Redis server port.
     */
    @Value("${mc-utils.redis.port}")
    private int port;

    /**
     * The Redis database index.
     */
    @Value("${mc-utils.redis.database}")
    private int database;

    /**
     * The optional Redis password.
     */
    @Value("${mc-utils.redis.auth}")
    private String auth;

    /**
     * Build the config to use for Redis.
     *
     * @return the config
     * @see RedisTemplate for config
     */
    @Bean @NonNull
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());

        RedisSerializer<Object> serializer = RedisSerializer.json();
        template.setDefaultSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        return template;
    }

    /**
     * Build the connection factory to use
     * when making connections to Redis.
     *
     * @return the built factory
     * @see JedisConnectionFactory for factory
     */
    @Bean @NonNull
    public JedisConnectionFactory jedisConnectionFactory() {
        log.info("Connecting to Redis at {}:{}/{} with connection pool", host, port, database);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);
        if (!auth.trim().isEmpty()) {
            log.info("Using auth...");
            config.setPassword(auth);
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(200);     // ⚠️ Increase to handle concurrent load
        poolConfig.setMaxIdle(100);      // Keep more idle connections ready
        poolConfig.setMinIdle(20);       // Pre-warm the pool
        poolConfig.setMaxWait(Duration.ofSeconds(2)); // Fail fast instead of waiting forever
        poolConfig.setTestOnBorrow(true); // Ensure connections are valid

        JedisClientConfiguration clientConfig = JedisClientConfiguration.builder()
                .usePooling()
                .poolConfig(poolConfig)
                .build();
        return new JedisConnectionFactory(config, clientConfig);
    }
}