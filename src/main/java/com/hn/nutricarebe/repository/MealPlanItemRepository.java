package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.MealPlanItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface MealPlanItemRepository extends JpaRepository<MealPlanItem, UUID> {

    @Query("""
        select i.food from MealPlanItem i
        where i.day.user.id = :userId
          and i.day.date >= :fromDate
    """)
    Page<Food> findFoodsFromDate(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            Pageable pageable
    );


    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from MealPlanItem i
        where i.day.user.id = :userId
          and i.day.date >= :fromDate
    """)
    int deleteItemsFromDate(UUID userId, LocalDate fromDate);

}
