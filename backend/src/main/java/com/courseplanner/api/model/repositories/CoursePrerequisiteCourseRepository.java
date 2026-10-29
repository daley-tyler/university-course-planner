package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.prereq.CoursePrerequisiteCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface CoursePrerequisiteCourseRepository extends JpaRepository<CoursePrerequisiteCourse, Long> {

    @Modifying
    @Query("""
        DELETE FROM CoursePrerequisiteCourse cpc
        WHERE cpc.group.id IN (
            SELECT g.id FROM CoursePrerequisiteGroup g WHERE g.course.id = :courseId
        )
    """)
    void deleteByCourseId(Long courseId);

    @Query("""
        select cpc from CoursePrerequisiteCourse cpc
        join fetch cpc.group g
        join fetch g.course c
        join fetch cpc.requiredCourse rc
        join fetch rc.subject rs
        where c.id in :courseIds
        """)
    List<CoursePrerequisiteCourse> findByCourseIdsWithCourses(@Param("courseIds") List<Long> courseIds);
}
