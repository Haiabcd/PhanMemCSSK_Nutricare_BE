package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Set<Tag> findByNameCodeInIgnoreCase(Set<String> nameCodes);
}
