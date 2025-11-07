package com.hn.nutricarebe.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class PkceStore {
    private static class Entry {
        final String verifier;
        final long expiresAt; // epoch millis

        Entry(String verifier, long expiresAt) {
            this.verifier = verifier;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final long ttlMillis = 10 * 60 * 1000; // 10 phút

    public void save(String state, String verifier) {
        map.put(state, new Entry(verifier, System.currentTimeMillis() + ttlMillis));
    }

    /** Lấy rồi xóa (one-time) */
    public String consume(String state) {
        Entry e = map.remove(state);
        if (e == null || e.expiresAt < System.currentTimeMillis()) return null;
        return e.verifier;
    }
}
