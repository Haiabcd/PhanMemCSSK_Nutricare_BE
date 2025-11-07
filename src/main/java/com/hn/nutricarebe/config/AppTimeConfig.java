package com.hn.nutricarebe.config;

import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppTimeConfig {
    @Bean
    public ZoneId appZone(@Value("${app.timezone:Asia/Ho_Chi_Minh}") String tz) {
        return ZoneId.of(tz);
    }
}
