package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Ingredient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Query(
            value = """
            select distinct i.*
            from ingredients i
            left join ingredient_aliases a
                   on a.ingredient_id = i.id
            where unaccent(lower(i.name)) like unaccent(lower(concat('%', :kw, '%')))
               or unaccent(lower(a.alias)) like unaccent(lower(concat('%', :kw, '%')))
            order by i.name asc
            """,
            countQuery = """
            select count(distinct i.id)
            from ingredients i
            left join ingredient_aliases a
                   on a.ingredient_id = i.id
            where unaccent(lower(i.name)) like unaccent(lower(concat('%', :kw, '%')))
               or unaccent(lower(a.alias)) like unaccent(lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true
    )
    Page<Ingredient> searchByNameOrAlias(@Param("kw") String keyword, Pageable pageable);


    Optional<Ingredient> findByNameIgnoreCase(String name);

    @Query("select i from Ingredient i join i.aliases a where lower(a) = lower(:alias)")
    Optional<Ingredient> findByAliasIgnoreCase(@Param("alias") String alias);
}
