package com.hn.nutricarebe.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching // Kích hoạt tính năng cache cho toàn ứng dụng
public class CacheConfig {
    @Bean
    public Caffeine<Object, Object> caffeineSpec() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(200);
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        //Tạo 2 cache
        CaffeineCacheManager cm = new CaffeineCacheManager("allTagsCache", "tagVocabCache");
        cm.setCaffeine(caffeine);
        return cm;
    }
}
