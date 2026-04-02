package com.shanyangcode.infintechatagent.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://%s:%s".formatted(redisProperties.getHost(), redisProperties.getPort()))
                .setDatabase(redisProperties.getDatabase())
                .setTimeout((int) redisProperties.getTimeout().toMillis());

        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            config.useSingleServer().setPassword(redisProperties.getPassword());
        }

        return Redisson.create(config);
    }
}
