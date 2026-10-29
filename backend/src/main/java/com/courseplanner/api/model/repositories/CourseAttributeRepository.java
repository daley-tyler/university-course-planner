package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.catalog.CourseAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface CourseAttributeRepository extends JpaRepository<CourseAttribute, Long> {

    // Clear all links for a course (used by the normalizer)
    @Modifying
    @Query("DELETE FROM CourseAttribute ca WHERE ca.course.id = :courseId")
    void deleteByCourseId(Long courseId);

    // Add a safe link if it doesn’t exist yet
    @Modifying
    @Query(value = """
        INSERT INTO course_attribute(course_id, attribute_id)
        SELECT :courseId, :attributeId
        WHERE NOT EXISTS (
            SELECT 1 FROM course_attribute
            WHERE course_id = :courseId AND attribute_id = :attributeId
        )
        """, nativeQuery = true)
    void linkIfAbsent(Long courseId, Integer attributeId);

    @Query("""
        select ca from CourseAttribute ca
        join fetch ca.attribute a
        where ca.course.id in :courseIds
        """)
    List<CourseAttribute> findByCourseIdsWithAttribute(@Param("courseIds") List<Long> courseIds);
}
