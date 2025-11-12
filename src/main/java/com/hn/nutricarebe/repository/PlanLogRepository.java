package com.hn.nutricarebe.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hn.nutricarebe.dto.response.DailyNutritionDto;
import com.hn.nutricarebe.dto.response.DateSlotProjection;
import com.hn.nutricarebe.dto.response.TopFoodDto;
import com.hn.nutricarebe.entity.PlanLog;
import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.enums.MealSlot;

@Repository
public interface PlanLogRepository extends JpaRepository<PlanLog, UUID> {
    @EntityGraph(attributePaths = {"food", "ingredients", "ingredients.ingredient"})
    List<PlanLog> findByUser_IdAndDateAndMealSlot(UUID userId, LocalDate date, MealSlot mealSlot);

    List<PlanLog> findByUser_IdAndDate(UUID userId, LocalDate date);

    List<PlanLog> findByUser_IdAndDateBetween(UUID userId, LocalDate start, LocalDate end);

    @Query(
            """
		select new com.hn.nutricarebe.dto.response.TopFoodDto(
			min(coalesce(pl.nameFood, f.name)),
			count(pl.id)
		)
		from PlanLog pl
		left join pl.food f
		where pl.user.id = :userId
		and pl.date between :start and :end
		group by lower(coalesce(pl.nameFood, f.name))
		order by count(pl.id) desc
		""")
    List<TopFoodDto> findTopFoodsOfUserBetween(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            Pageable pageable);

    @Query(
            """
	select new com.hn.nutricarebe.dto.response.DailyNutritionDto(
	pl.date,
	coalesce(sum(cast(pl.actualNutrition.proteinG as bigdecimal)), cast(0 as bigdecimal)),
	coalesce(sum(cast(pl.actualNutrition.carbG    as bigdecimal)), cast(0 as bigdecimal)),
	coalesce(sum(cast(pl.actualNutrition.fatG     as bigdecimal)), cast(0 as bigdecimal)),
	coalesce(sum(cast(pl.actualNutrition.fiberG   as bigdecimal)), cast(0 as bigdecimal))
	)
	from PlanLog pl
where pl.user.id = :userId
	and pl.date between :start and :end
	group by pl.date
	order by pl.date asc
	""")
    List<DailyNutritionDto> sumDailyNutritionByDateBetween(
            @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query(
            """
	select new com.hn.nutricarebe.dto.response.DateSlotProjection(pl.date, pl.mealSlot)
	from PlanLog pl
	where pl.user.id = :userId
	and pl.date between :start and :end
	group by pl.date, pl.mealSlot
	order by pl.date asc
	""")
    List<DateSlotProjection> findDistinctDateAndSlotByUserAndDateBetween(
            @Param("userId") UUID userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    long countBySource(LogSource source);

    // Đếm số log theo mealSlot và source
    @Query("SELECT p.mealSlot, COUNT(p) " + "FROM PlanLog p " + "WHERE p.source = :source " + "GROUP BY p.mealSlot")
    List<Object[]> countByMealSlotAndSource(@Param("source") LogSource source);

    // Tìm các món ăn được ghi log nhiều nhất theo nguồn
    @Query("""
    SELECT f.name AS name, COUNT(p) AS count
    FROM PlanLog p
    JOIN p.food f
    WHERE p.source = :source AND p.food IS NOT NULL
    GROUP BY f.name
    ORDER BY COUNT(p) DESC
""")
    List<Object[]> findTopFoodsBySource(@Param("source") LogSource source);

    // Tìm 15 người dùng có số log nhiều nhất
    @Query(
            """
		SELECT p.user.profile.name AS name, COUNT(p) AS count
		FROM PlanLog p
		WHERE p.user.profile.name IS NOT NULL
		GROUP BY p.user.profile.name
		ORDER BY COUNT(p) DESC
	""")
    List<Object[]> findTopUsersByLogCount();
}
