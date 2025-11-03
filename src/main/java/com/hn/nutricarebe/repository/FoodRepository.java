package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.dto.overview.FoodTopKcalDto;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {
    // Kiểm tra sự tồn tại của món ăn
    @Query("select count(f) > 0 from Food f where lower(f.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);

    // Lấy món ăn theo ID
    @EntityGraph(attributePaths = {
            "mealSlots",
            "tags",
            "ingredients",
            "ingredients.ingredient"
    })
    Optional<Food> findWithCollectionsById(UUID id);

    // Tìm món ăn theo slot
    @EntityGraph(attributePaths = {
            "mealSlots",
            "tags",
            "ingredients",
            "ingredients.ingredient"
    })
    @Query("select distinct f from Food f join f.mealSlots ms where ms = :slot")
    Slice<Food> findByMealSlot(@Param("slot") MealSlot slot, Pageable pageable);


    // Tìm món ăn theo tên gần đúng
    @EntityGraph(attributePaths = {
            "mealSlots",
            "tags",
            "ingredients",
            "ingredients.ingredient"
    })
    Slice<Food> findByNameContainingIgnoreCase(String q, Pageable pageable);


    // Lấy tất cả món ăn
    @EntityGraph(attributePaths = {
            "mealSlots",
            "tags",
            "ingredients",
            "ingredients.ingredient"
    })
    Slice<Food> findAllBy(Pageable pageable);

    @Query(value = """
    SELECT f.*
    FROM foods f
    JOIN food_meal_slots ms ON ms.food_id = f.id
    WHERE ms.meal_slot = :slot
      AND f.kcal IS NOT NULL
      AND f.kcal BETWEEN :minKcal AND :maxKcal
    ORDER BY ABS(f.kcal - :perItemTargetKcal) ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Food> selectCandidatesBySlotAndKcalWindow(
            @Param("slot") String slot,
            @Param("minKcal") int minKcal,
            @Param("maxKcal") int maxKcal,
            @Param("perItemTargetKcal") int perItemTargetKcal,
            @Param("limit") int limit
    );

    @Query(
            value = """
        select f.*
        from foods f
        where unaccent(lower(f.name)) like unaccent(lower(concat('%', :kw, '%')))
        order by f.name asc
        """,
            countQuery = """
        select count(f.id)
        from foods f
        where unaccent(lower(f.name)) like unaccent(lower(concat('%', :kw, '%')))
        """,
            nativeQuery = true
    )
    Page<Food> searchByNameUnaccent(@Param("kw") String keyword, Pageable pageable);

    // Lấy danh sách thời gian tạo món ăn trong khoảng thời gian
    @Query("select f.createdAt from Food f " +
            "where f.createdAt >= :start and f.createdAt < :end")
    List<Instant> findCreatedAtBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end
    );

    // Đếm số món ăn được tạo trong khoảng thời gian
    @Query("SELECT COUNT(f) FROM Food f WHERE f.createdAt >= :start AND f.createdAt < :end")
    long countFoodsCreatedBetween(@Param("start") Instant start, @Param("end") Instant end);

    // Lấy 10 món ăn có kcal cao nhất
    @Query(value = """
    SELECT f.name AS name, f.kcal AS kcal
    FROM foods f
    WHERE f.kcal IS NOT NULL
    ORDER BY f.kcal DESC
    LIMIT 10
""", nativeQuery = true)
    List<Object[]> findTop10FoodsByKcalNative();

    // Lấy 10 món ăn có protein cao nhất
    @Query(value = """
    SELECT f.name AS name, f.proteinG AS proteinG
    FROM foods f
    WHERE f.proteinG IS NOT NULL
    ORDER BY f.proteinG DESC
    LIMIT 10
""", nativeQuery = true)
    List<Object[]> findTop10FoodsByProteinNative();

    /** Số món có đầy đủ 4 macro (nutrition có thể null) */
    @Query("""
        SELECT COUNT(f) FROM Food f
        WHERE f.nutrition IS NOT NULL
          AND f.nutrition.kcal     IS NOT NULL
          AND f.nutrition.proteinG IS NOT NULL
          AND f.nutrition.carbG    IS NOT NULL
          AND f.nutrition.fatG     IS NOT NULL
    """)
    long countFoodsWithCompleteMacros();

    /** Món năng lượng cao (kcal > threshold) */
    long countByNutrition_KcalGreaterThan(BigDecimal threshold);

    /** Món năng lượng thấp (kcal < threshold) */
    long countByNutrition_KcalLessThan(BigDecimal threshold);

    // Đếm số món ăn theo các bin năng lượng
    @Query(value = """
        SELECT
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 0    AND kcal < 200)  AS bin1,
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 200  AND kcal < 400)  AS bin2,
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 400  AND kcal < 600)  AS bin3,
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 600  AND kcal < 800)  AS bin4,
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 800  AND kcal < 1000) AS bin5,
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 1000 AND kcal < 1200) AS bin6,
          COUNT(*) FILTER (WHERE kcal IS NOT NULL AND kcal >= 1200)                 AS bin7,
          COUNT(*) FILTER (WHERE kcal IS NULL)                                      AS missing
        FROM foods
        """, nativeQuery = true)
    Object countEnergyBinsRaw();

    // Đếm tổng món có kcal < 300
    @Query(value = """
    SELECT COUNT(*) 
    FROM foods f 
    WHERE f.kcal < 300
""", nativeQuery = true)
    Long countFoodsWithLowKcal();

    // Đếm tổng món có kcal > 800
    @Query(value = """
    SELECT COUNT(*)
    FROM foods f
    WHERE f.kcal > 800
""", nativeQuery = true)
    Long countFoodsWithHighKcal();

    // Đếm số món có đầy đủ 5 chỉ số dinh dưỡng: kcal, proteinG, carbG, fatG, fiberG
    @Query("""
        SELECT COUNT(f)
        FROM Food f
        WHERE f.nutrition.kcal     IS NOT NULL AND f.nutrition.kcal     >= 0
          AND f.nutrition.proteinG IS NOT NULL AND f.nutrition.proteinG >= 0
          AND f.nutrition.carbG    IS NOT NULL AND f.nutrition.carbG    >= 0
          AND f.nutrition.fatG     IS NOT NULL AND f.nutrition.fatG     >= 0
          AND f.nutrition.fiberG   IS NOT NULL AND f.nutrition.fiberG   >= 0
    """)
    long countFoodsWithComplete5();
}
