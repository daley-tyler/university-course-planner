package com.courseplanner.api.normalizer;

import com.courseplanner.api.Courses.normalizer.CourseNormalizer;
import com.courseplanner.api.model.entities.catalog.*;
import com.courseplanner.api.model.repositories.*;
import com.courseplanner.api.model.entities.enums.RestrictionKind;
import com.courseplanner.api.model.entities.enums.RestrictionMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@Import(CourseNormalizer.class)
@ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
class CourseNormalizerIntegrationTest {

    // --- Real PostgreSQL container for this test ---
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    // --- Bind the container DB to Spring Boot ---
    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");

        // Ensure Flyway runs real migrations
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Autowired private CourseRepository courseRepo;
    @Autowired private SubjectRepository subjectRepo;
    @Autowired private CourseRestrictionRepository restrictionRepo;
    @Autowired private CourseAttributeRepository courseAttrRepo;
    @Autowired private CourseNormalizer normalizer;

    @Test
    void persistsRestrictionsAndAttributes() {
        // Seed data
        Subject subj = subjectRepo.save(new Subject(null, "CSE", "Computer Science"));

        Course c = new Course();
        c.setSubject(subj);
        c.setNumber("101");
        c.setTitle("Intro to CS");
        c.setCredits(BigDecimal.valueOf(3));
        c.setRestrictionText("This section is ONLY open for the following campus: Sample Campus");
        c.setAttributeText("FND - Creative Arts (IIA)");
        c.setNormalized(false);

        courseRepo.save(c);

        // Normalize
        normalizer.normalizeCourse(c);

        // Validate restrictions
        List<CourseRestriction> restrictions = restrictionRepo.findAll();
        assertThat(restrictions).hasSize(1);
        assertThat(restrictions.get(0).getRestrictionType()).isEqualTo(RestrictionKind.CAMPUS);
        assertThat(restrictions.get(0).getMode()).isEqualTo(RestrictionMode.ONLY);
        assertThat(restrictions.get(0).getValue()).isEqualTo("Sample Campus");

        // Validate attributes
        assertThat(courseAttrRepo.findAll()).isNotEmpty();
    }
}
