package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.catalog.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    Optional<Subject> findByCode(String code);
}
