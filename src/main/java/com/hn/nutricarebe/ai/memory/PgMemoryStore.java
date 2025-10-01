package com.hn.nutricarebe.ai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hn.nutricarebe.entity.AiUserFact;
import com.hn.nutricarebe.repository.AiUserFactRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PgMemoryStore implements MemoryStore{

    AiUserFactRepository factRepo;
    ObjectMapper om;

    @Override
    public Map<String, Object> loadFacts(UUID userId) {
        var list = factRepo.findByUserId(userId);
        Map<String, Object> out = new HashMap<>();
        for (var f : list) {
            try { out.put(f.getKey(), om.readValue(f.getValue(), Object.class)); }
            catch (Exception ignored) {}
        }
        return out;
    }

    @Override
    public void upsertFact(UUID userId, String key, Object value) {
        try {
            var row = factRepo.findByUserIdAndKey(userId, key);
            String json = om.writeValueAsString(value);
            if (row == null) {
                row = new AiUserFact();
                row.setUserId(userId);
                row.setKey(key);
            }
            row.setValue(json);
            row.setSource("agent");
            factRepo.save(row);
        } catch (Exception ignored) {}
    }
}
