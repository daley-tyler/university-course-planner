package com.courseplanner.api.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.courseplanner.api.model.entities.validation.ValidationAssignment;

@Repository
public interface ValidationAssignmentRepository extends JpaRepository<ValidationAssignment, Long> {}
