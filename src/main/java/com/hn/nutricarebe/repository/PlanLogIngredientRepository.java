package com.hn.nutricarebe.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hn.nutricarebe.entity.PlanLogIngredient;

@Repository
public interface PlanLogIngredientRepository extends JpaRepository<PlanLogIngredient, UUID> {}
