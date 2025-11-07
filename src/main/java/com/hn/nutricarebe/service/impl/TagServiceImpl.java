package com.hn.nutricarebe.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.request.TagCreationRequest;
import com.hn.nutricarebe.dto.request.TagDto;
import com.hn.nutricarebe.entity.Tag;
import com.hn.nutricarebe.mapper.TagMapper;
import com.hn.nutricarebe.repository.TagRepository;
import com.hn.nutricarebe.service.TagService;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class TagServiceImpl implements TagService {
    TagRepository tagRepository;
    TagMapper tagMapper;

    @Override
    public List<String> findNameCodeByNormal(Set<String> normalized) {
        return tagRepository.findByNameCodeInIgnoreCase(normalized).stream()
                .map(t -> t.getNameCode().toLowerCase())
                .distinct()
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public List<TagDto> autocomplete(String keyword, int limit) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return Collections.emptyList();
        }
        int bounded = Math.max(1, Math.min(limit, 50));
        List<Tag> results = tagRepository.autocompleteUnaccent(q, bounded);
        return results.stream().map(tagMapper::toTagDto).toList();
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
        if (tags.size() > MAX)
            sb.append("(Đã rút gọn: ")
                    .append(MAX)
                    .append("/")
                    .append(tags.size())
                    .append(")\n");
        return sb.toString();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void save(TagCreationRequest request) {
        Tag tag = Tag.builder()
                .nameCode(request.getNameCode())
                .description(request.getDescription())
                .build();
        tagRepository.save(tag);
    }

    @Caching(
            evict = {
                @CacheEvict(value = "allTagsCache", key = "'v1'"),
                @CacheEvict(value = "tagVocabCache", key = "'v1'")
            })
    public Tag create(Tag tag) {
        return tagRepository.save(tag);
    }

    @Caching(
            evict = {
                @CacheEvict(value = "allTagsCache", key = "'v1'"),
                @CacheEvict(value = "tagVocabCache", key = "'v1'")
            })
    public Tag update(UUID id, Tag patch) {
        Tag t = tagRepository.findById(id).orElseThrow();
        t.setNameCode(patch.getNameCode());
        t.setDescription(patch.getDescription());
        return tagRepository.save(t);
    }

    @Caching(
            evict = {
                @CacheEvict(value = "allTagsCache", key = "'v1'"),
                @CacheEvict(value = "tagVocabCache", key = "'v1'")
            })
    public void delete(UUID id) {
        tagRepository.deleteById(id);
    }
    // ======================================================================//
    private String safe(String s) {
        if (s == null) return null;
        return s.replaceAll("[\\r\\u0000]", "").replace('\t', ' ').trim();
    }

    @Cacheable(value = "allTagsCache", key = "'v1'")
    public List<Tag> getAllTagsForPrompt() {
        return tagRepository.findAll();
    }
}
