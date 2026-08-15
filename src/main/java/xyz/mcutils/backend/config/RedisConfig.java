package xyz.mcutils.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * @author Braydon
 */
@Configuration
@Slf4j
@EnableRedisRepositories(basePackages = "xyz.mcutils.backend.repository")
public class RedisConfig {

    @Value("${mc-utils.redis.host}")
    private String host;

    @Value("${mc-utils.redis.port}")
    private int port;

    @Value("${mc-utils.redis.database}")
    private int database;

    @Value("${mc-utils.redis.auth}")
    private String auth;

    @Value("${mc-utils.redis.command-timeout-seconds:120}")
    private int commandTimeoutSeconds;

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory());

        RedisSerializer<Object> serializer = RedisSerializer.json();
        template.setDefaultSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        return template;
    }

    @Bean(name = "queueRedisTemplate")
    public RedisTemplate<String, String> queueRedisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(queueLettuceConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Dedicated connection for the blocking queue consumers (BLPOP) so they can never
     * stall cache/repository traffic sharing the main connection (Lettuce processes
     * commands per connection in order; a pending BLPOP would block everything behind it).
     */
    @Bean
    public LettuceConnectionFactory queueLettuceConnectionFactory() {
        log.info("Connecting queue Redis at {}:{}/{} with dedicated Lettuce connection", host, port, database);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);

        if (!auth.trim().isEmpty()) {
            config.setPassword(auth);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(commandTimeoutSeconds))
                .shutdownTimeout(Duration.ofMillis(100))
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }

    @Bean
    @Primary
    public LettuceConnectionFactory lettuceConnectionFactory() {
        log.info("Connecting to Redis at {}:{}/{} with Lettuce", host, port, database);

        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);

        if (!auth.trim().isEmpty()) {
            config.setPassword(auth);
        }

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(commandTimeoutSeconds))
                .shutdownTimeout(Duration.ofMillis(100))
                .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }
}
