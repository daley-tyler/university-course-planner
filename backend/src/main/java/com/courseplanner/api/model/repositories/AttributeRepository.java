package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.catalog.Attribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttributeRepository extends JpaRepository<Attribute, Integer> {

    // Used by the normalizer to match attributes by code (case-insensitive)
    Optional<Attribute> findByCodeIgnoreCase(String code);

    // Fallback for matching by name (case-insensitive)
    Optional<Attribute> findByNameIgnoreCase(String name);
}
