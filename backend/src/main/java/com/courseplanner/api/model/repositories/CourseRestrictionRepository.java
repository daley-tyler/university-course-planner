package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.catalog.*;
import com.courseplanner.api.model.entities.enums.RestrictionKind;
import com.courseplanner.api.model.entities.enums.RestrictionMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CourseRestrictionRepository extends JpaRepository<CourseRestriction, Long> {

    List<CourseRestriction> findByCourse(Course course);

    List<CourseRestriction> findByRestrictionTypeAndValue(RestrictionKind restrictionType, String value);

    List<CourseRestriction> findByRestrictionTypeAndModeAndValue(
            RestrictionKind restrictionType, RestrictionMode mode, String value);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from CourseRestriction r where r.course.id = :courseId")
    void deleteByCourseId(Long courseId);

    boolean existsByCourseIdAndRestrictionTypeAndModeAndValue(
            Long courseId,
            RestrictionKind restrictionType,
            RestrictionMode mode,
            String value
    );
}
