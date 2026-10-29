package com.courseplanner.api;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import com.courseplanner.api.config.DemoCatalogLoader;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "local", "demo"})
@Testcontainers(disabledWithoutDocker = true)
class CoursePlannerApiApplicationTests {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoCatalogLoader demo;

    @Test
    void contextLoads() { }

    @Test
    void searchesTheDemoCatalogOverHttp() {
        var result = http.getForEntity("/api/courses?q=Programming", JsonNode.class);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().path("items").get(0).path("code").asText()).isEqualTo("DEMO 101");
    }

    @Test
    void requirementCountMatchesTheFrontendContractOverHttp() {
        var programs = http.getForObject("/api/programs?catalogYear=2025", JsonNode.class);
        long id = programs.path("items").get(0).path("id").asLong();
        var root = http.getForObject("/api/programs/" + id + "/requirements?catalogYear=2025", JsonNode.class).path("root");
        assertThat(root.path("nRequired").asInt()).isEqualTo(2);
        assertThat(root.has("nrequired")).isFalse();
        assertThat(root.path("courseOptions").size()).isEqualTo(6);
    }

    @Test
    void localAuthenticationReturnsTheDemoIdentity() {
        var result = http.getForEntity("/api/auth/me", JsonNode.class);
        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().path("casId").asText()).isEqualTo("devuser");
        assertThat(result.getHeaders().getFirst("Set-Cookie")).doesNotContain("Secure;");
    }

    @Test
    void demoCoursesExposeTheirSchedulingPrerequisites() {
        var course = http.getForObject("/api/courses/{code}", JsonNode.class, "DEMO 201");
        assertThat(course.path("prereqs").get(0).asText()).isEqualTo("DEMO 101");
    }

    @Test
    void demoLoadingIsIdempotent() {
        demo.run(null);
        demo.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM req_node_course_option", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_prerequisite_course", Integer.class)).isEqualTo(2);
    }
}
