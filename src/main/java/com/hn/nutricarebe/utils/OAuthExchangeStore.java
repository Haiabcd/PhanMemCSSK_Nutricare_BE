package com.hn.nutricarebe.utils;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OAuthExchangeStore {
    public static final class Entry<T> {
        public final T value;
        public final long expiresAt; // epoch millis
        public Entry(T value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry<?>> map = new ConcurrentHashMap<>();
    private final long ttlMillis = 2 * 60 * 1000; // 2 phút (nên ngắn)
    private final Clock clock = Clock.systemUTC();

    /** Lưu value với TTL mặc định */
    public <T> void put(String key, T value) {
        long now = clock.millis();
        map.put(key, new Entry<>(value, now + ttlMillis));
    }

    /** Lấy rồi XÓA (one-time). Hết hạn -> null */
    @SuppressWarnings("unchecked")
    public <T> T take(String key, Class<T> type) {
        Entry<?> e = map.remove(key);
        if (e == null) return null;
        long now = clock.millis();
        if (e.expiresAt < now) return null;
        return (T) e.value;
    }

    /** (Tuỳ chọn) Lấy mà không xoá — chỉ dùng để debug */
    @SuppressWarnings("unused")
    public <T> T peek(String key, Class<T> type) {
        Entry<?> e = map.get(key);
        if (e == null || e.expiresAt < clock.millis()) return null;
        return type.cast(e.value);
    }
}
