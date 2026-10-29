package com.courseplanner.api.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.courseplanner.api.model.entities.plans.PlanSemester;

@Repository
public interface PlanSemesterRepository extends JpaRepository<PlanSemester, Long> {}
