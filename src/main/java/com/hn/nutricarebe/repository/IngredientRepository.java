package com.hn.nutricarebe.repository;


import com.hn.nutricarebe.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
    @Query("select count(i) > 0 from Ingredient i where lower(i.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);
}
