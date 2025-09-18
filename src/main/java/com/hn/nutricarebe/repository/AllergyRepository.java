package com.hn.nutricarebe.repository;


import com.hn.nutricarebe.entity.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, UUID> {
    boolean existsByName(String name);
}
