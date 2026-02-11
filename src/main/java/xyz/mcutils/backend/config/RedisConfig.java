package xyz.mcutils.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import xyz.mcutils.backend.model.redis.SubmitQueueItem;

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

    @Bean
    public RedisTemplate<String, SubmitQueueItem> submitQueueRedisTemplate() {
        RedisTemplate<String, SubmitQueueItem> template = new RedisTemplate<>();
        template.setConnectionFactory(lettuceConnectionFactory());
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(SubmitQueueItem.class));
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory() {
        log.info("Connecting to Redis at {}:{}/{} with Lettuce", host, port, database);

        RedisStandaloneConfiguration config =
                new RedisStandaloneConfiguration(host, port);
        config.setDatabase(database);

        if (!auth.trim().isEmpty()) {
            config.setPassword(auth);
        }

        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(10))
                        .shutdownTimeout(Duration.ofMillis(100))
                        .build();

        return new LettuceConnectionFactory(config, clientConfig);
    }
}
