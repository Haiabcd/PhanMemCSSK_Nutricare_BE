package com.hn.nutricarebe.repository;


import com.hn.nutricarebe.entity.Allergy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, UUID> {
    boolean existsByName(String name);
    Slice<Allergy> findAllBy(Pageable pageable);
    Slice<Allergy> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
