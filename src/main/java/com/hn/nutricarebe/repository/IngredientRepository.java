package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Ingredient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {

    // Kiểm tra sự tồn tại của nguyên liệu theo tên
    @Query("select count(i) > 0 from Ingredient i where lower(i.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);

    // Lấy nguyên liệu theo ID
    @EntityGraph(attributePaths = {"aliases", "tags"})
    Optional<Ingredient> findWithCollectionsById(UUID id);

    // Lấy tất cả nguyên liệu
    @EntityGraph(attributePaths = {"aliases", "tags"})
    Slice<Ingredient> findAllBy(Pageable pageable);

    // Tìm nguyên liệu theo tên gần đúng
    @EntityGraph(attributePaths = {"aliases", "tags"})
    Slice<Ingredient> findByNameContainingIgnoreCase(String q, Pageable pageable);
}
