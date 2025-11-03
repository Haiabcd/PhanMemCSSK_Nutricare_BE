package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {
    Set<Tag> findByNameCodeInIgnoreCase(Set<String> nameCodes);

    @Query(value = """
        SELECT t.*
        FROM tags t
        WHERE
          (:q IS NOT NULL AND :q <> '')
          AND (
            unaccent(lower(t.name_code)) LIKE unaccent(lower(CONCAT(:q, '%')))
            OR unaccent(lower(COALESCE(t.description, ''))) LIKE unaccent(lower(CONCAT('%', :q, '%')))
          )
        ORDER BY
          CASE
            WHEN unaccent(lower(t.name_code)) = unaccent(lower(:q)) THEN 0
            WHEN unaccent(lower(t.name_code)) LIKE unaccent(lower(CONCAT(:q, '%'))) THEN 1
            ELSE 2
          END,
          LENGTH(t.name_code),
          t.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Tag> autocompleteUnaccent(
            @Param("q") String keyword,
            @Param("limit") int limit
    );
}
