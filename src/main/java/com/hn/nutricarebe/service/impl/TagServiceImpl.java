package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.entity.Tag;
import com.hn.nutricarebe.repository.TagRepository;
import com.hn.nutricarebe.service.TagService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class TagServiceImpl implements TagService {
    TagRepository tagRepository;


    @Override
    public List<String> findNameCodeByNormal(Set<String> normalized) {
        return tagRepository.findByNameCodeInIgnoreCase(normalized)
                .stream()
                .map(t -> t.getNameCode().toLowerCase())
                .distinct()
                .toList();
    }

    @Cacheable(value = "allTagsCache", key = "'v1'")
    public List<Tag> getAllTagsForPrompt() {
        return tagRepository.findAll();
    }

    // Lấy text danh mục Tag để chèn vào prompt
    @Override
    @Cacheable(value = "tagVocabCache", key = "'v1'")
    public String getTagVocabularyText() {
        List<Tag> tags = getAllTagsForPrompt();
        StringBuilder sb = new StringBuilder("DANH MỤC TAG HIỆN CÓ:\n");
        int count = 0, MAX = 500;
        for (Tag t : tags) {
            if (count++ >= MAX) break;
            String code = safe(t.getNameCode());
            String desc = safe(t.getDescription());
            if (desc != null && desc.length() > 200) desc = desc.substring(0, 200) + "...";
            sb.append("- ").append(code);
            if (desc != null && !desc.isBlank()) sb.append(" : ").append(desc);
            sb.append("\n");
        }
        if (tags.size() > MAX) sb.append("(Đã rút gọn: ").append(MAX).append("/").append(tags.size()).append(")\n");
        return sb.toString();
    }

    @Caching(evict = {
            @CacheEvict(value = "allTagsCache", key = "'v1'"),
            @CacheEvict(value = "tagVocabCache", key = "'v1'")
    })
    public Tag create(Tag tag) {
        return tagRepository.save(tag);
    }

    @Caching(evict = {
            @CacheEvict(value = "allTagsCache", key = "'v1'"),
            @CacheEvict(value = "tagVocabCache", key = "'v1'")
    })
    public Tag update(UUID id, Tag patch) {
        Tag t = tagRepository.findById(id).orElseThrow();
        t.setNameCode(patch.getNameCode());
        t.setDescription(patch.getDescription());
        return tagRepository.save(t);
    }

    @Caching(evict = {
            @CacheEvict(value = "allTagsCache", key = "'v1'"),
            @CacheEvict(value = "tagVocabCache", key = "'v1'")
    })
    public void delete(UUID id) {
        tagRepository.deleteById(id);
    }
    //======================================================================//
    private String safe(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\u0000]", "").replace('\t',' ').trim();
    }
}
