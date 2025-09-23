package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Food;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {
    @Query("select count(f) > 0 from Food f where lower(f.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);


    @Query("SELECT f FROM Food f ORDER BY f.createdAt DESC, f.id DESC")
    List<Food> findFirstPage(Pageable pageable);

    @Query("""
           SELECT f FROM Food f
           WHERE (f.createdAt < :createdAtCursor)
              OR (f.createdAt = :createdAtCursor AND f.id < :idCursor)
           ORDER BY f.createdAt DESC, f.id DESC
           """)
    List<Food> findNextPage(
            @Param("createdAtCursor") Instant createdAtCursor,
            @Param("idCursor") UUID idCursor,
            Pageable pageable
    );
}
