package com.courseplanner.api.normalizer;

import com.courseplanner.api.Courses.normalizer.CourseNormalizer;
import com.courseplanner.api.model.entities.catalog.*;
import com.courseplanner.api.model.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseNormalizerUnitTest {

    @Mock private CourseRepository courseRepo;
    @Mock private SubjectRepository subjectRepo;
    @Mock private AttributeRepository attributeRepo;
    @Mock private CourseAttributeRepository courseAttrRepo;
    @Mock private CoursePrerequisiteGroupRepository prereqGroupRepo;
    @Mock private CoursePrerequisiteCourseRepository prereqCourseRepo;
    @Mock private CourseRestrictionRepository restrictionRepo;

    @InjectMocks private CourseNormalizer normalizer;

    private Course course;

    @BeforeEach
    void setup() {
        course = new Course();
        course.setId(1L);
        course.setTitle("Intro to Testing");
        course.setCredits(BigDecimal.valueOf(3));
    }

    @Test
    void parsesMultipleAttributes() {
        when(attributeRepo.findByCodeIgnoreCase("IIA"))
                .thenReturn(Optional.of(new Attribute(1, "IIA", "Creative Arts", null)));
        when(attributeRepo.findByCodeIgnoreCase("PA4C"))
                .thenReturn(Optional.of(new Attribute(2, "PA4C", "Global Inquiry", null)));

        course.setAttributeText("FND - Creative Arts (IIA); PA - Global Inquiry (PA4C)");
        normalizer.normalizeCourse(course);

        verify(attributeRepo).findByCodeIgnoreCase("IIA");
        verify(attributeRepo).findByCodeIgnoreCase("PA4C");

        // Match actual behavior
        verify(courseAttrRepo).deleteByCourseId(1L);
        verify(courseAttrRepo, times(2)).linkIfAbsent(eq(1L), anyInt());
    }
    @Test
    void parsesPrerequisiteCourses() {
        course.setPrereqText("ACC 221 or ACC 211");
        normalizer.normalizeCourse(course);
        verify(prereqGroupRepo, atLeastOnce()).save(any());
    }
}
