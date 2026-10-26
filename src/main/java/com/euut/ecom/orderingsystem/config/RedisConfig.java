package com.euut.ecom.orderingsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        return template;
    }

//    @Bean
//    public DefaultRedisScript<Long> stockDecrementScript() {
//        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
//        script.setLocation(new ClassPathResource("scripts/stock_decrement.lua"));
//        script.setResultType(Long.class);
//        return script;
//    }

    @Bean
    public DefaultRedisScript<Long> batchStockDecrementScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/batch_stock_decrement.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean
    public DefaultRedisScript<Long> batchStockRestoreScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/batch_stock_restore.lua"));
        script.setResultType(Long.class);
        return script;
    }
}