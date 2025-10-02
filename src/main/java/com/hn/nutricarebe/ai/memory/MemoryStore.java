package com.hn.nutricarebe.ai.memory;

import java.util.Map;
import java.util.UUID;

public interface MemoryStore {
    Map<String, Object> loadFacts(UUID userId);
    void upsertFact(UUID userId, String key, Object value);
}
