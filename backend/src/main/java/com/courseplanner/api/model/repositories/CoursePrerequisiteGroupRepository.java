package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.prereq.CoursePrerequisiteGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePrerequisiteGroupRepository extends JpaRepository<CoursePrerequisiteGroup, Long> {

    @Modifying
    @Query("DELETE FROM CoursePrerequisiteGroup g WHERE g.course.id = :courseId")
    void deleteByCourseId(Long courseId);
}
