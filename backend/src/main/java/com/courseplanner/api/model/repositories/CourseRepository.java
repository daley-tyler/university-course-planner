package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.catalog.Course;
import com.courseplanner.api.model.entities.catalog.Subject;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

    // Needed by scraper
    Optional<Course> findBySubjectAndNumber(Subject subject, String number);

    // Needed by normalizer
    List<Course> findByNormalizedFalse();

    Optional<Course> findBySubject_CodeAndNumber(String code, String number);

    @Query("select distinct c.number from Course c")
    List<String> findDistinctNumbers();

}
