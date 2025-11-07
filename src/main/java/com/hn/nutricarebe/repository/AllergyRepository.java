package com.hn.nutricarebe.repository;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hn.nutricarebe.entity.Allergy;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, UUID> {
    boolean existsByNameIgnoreCase(String name);

    Slice<Allergy> findAllBy(Pageable pageable);

    Slice<Allergy> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
