package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.MealPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MealPlanItemRepository extends JpaRepository<MealPlanItem, UUID> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        delete from MealPlanItem i
        where i.day.user.id = :userId
          and i.day.date >= :fromDate
    """)
    int deleteItemsFromDate(UUID userId, LocalDate fromDate);


    @Modifying
    @Query("""
    delete from MealPlanItem i
    where i.day.id = :dayId
      and i.used = false
      and i.swapped = false
    """)
    void deleteUnusedItemsByDay(@Param("dayId") UUID dayId);

    List<MealPlanItem> findByDay_User_IdAndDay_Date(UUID userId, LocalDate date);

}
