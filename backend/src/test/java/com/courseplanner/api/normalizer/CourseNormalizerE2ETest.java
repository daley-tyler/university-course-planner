package com.courseplanner.api.normalizer;

import com.courseplanner.api.model.entities.catalog.*;
import com.courseplanner.api.model.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full end-to-end test: hits /api/courses/normalize endpoint and checks DB state.
 * Uses a PostgreSQL Testcontainer instead of H2 to match production behavior.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseNormalizerE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MockMvc mockMvc;
    @Autowired CourseRepository courseRepo;
    @Autowired SubjectRepository subjectRepo;
    @Autowired CourseRestrictionRepository restrictionRepo;
    @Autowired CourseAttributeRepository courseAttrRepo;

    @BeforeEach
    void setupData() {
        restrictionRepo.deleteAll();
        courseAttrRepo.deleteAll();
        courseRepo.deleteAll();
        subjectRepo.deleteAll();

        Subject subj = new Subject();
        subj.setCode("CSE");
        subj.setName("Computer Science");
        subjectRepo.save(subj);

        Course c = new Course();
        c.setSubject(subj);
        c.setNumber("101");
        c.setTitle("Intro to Computing");
        c.setCredits(BigDecimal.valueOf(3));
        c.setAttributeText("FND - Creative Arts (IIA)");
        c.setRestrictionText("This section is ONLY open for the following campus: Sample Campus");
        courseRepo.save(c);
    }

    @Test
    void normalizationRejectsRequestsWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/courses/normalize")).andExpect(status().isForbidden());
        assertThat(courseRepo.findAll().get(0).isNormalized()).isFalse();
    }

    @Test
    void fullNormalizationFlow_works() throws Exception {

        mockMvc.perform(post("/api/courses/normalize")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("normalizer-test"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk());

        Course normalized = courseRepo.findAll().get(0);
        assertThat(normalized.isNormalized()).isTrue();

        assertThat(restrictionRepo.findAll()).isNotEmpty();
        assertThat(courseAttrRepo.findAll()).isNotEmpty();
    }
}
