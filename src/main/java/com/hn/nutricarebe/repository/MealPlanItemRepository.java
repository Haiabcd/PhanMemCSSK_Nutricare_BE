package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.MealPlanItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface MealPlanItemRepository extends JpaRepository<MealPlanItem, UUID> {

    @Query("""
    select distinct f
    from MealPlanItem i
    join i.day d
    join i.food f
    where d.user.id = :userId
      and d.date >= :fromDate
    order by f.id desc
""")
    Slice<Food> findFoodsFromDate(
            @Param("userId") UUID userId,
            @Param("fromDate") LocalDate fromDate,
            Pageable pageable
    );

}
