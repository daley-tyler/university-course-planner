package com.courseplanner.api.Bulletin;

import com.courseplanner.api.Bulletin.Programs.ProgramScraperService;
import com.courseplanner.api.model.repositories.ProgramCatalogVersionRepository;
import com.courseplanner.api.model.repositories.ProgramRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP and persistence pipeline using only independently authored local pages. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ProgramPipelineIntegrationTest {
    @Container static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",database::getJdbcUrl);
        registry.add("spring.datasource.username",database::getUsername);
        registry.add("spring.datasource.password",database::getPassword);
    }
    @Autowired ProgramScraperService scraper;
    @Autowired ProgramRepository programs;
    @Autowired ProgramCatalogVersionRepository versions;

    @Test void importsAndReimportsThirtyTwoInventedProgramsWithoutDuplicates() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        StringBuilder links=new StringBuilder();
        for(int i=1;i<=32;i++) {
            links.append("<li><a href='/invented/workshop-").append(i).append("/'>Invented Workshop ").append(i).append("</a></li>");
        }
        String root="/archive/2091-2092/";
        String school="<div id='degreesandprogramstextcontainer'><h2>Bachelor of Invented Workshop Studies</h2><ul>"+links+"</ul></div>";
        server.createContext(root,exchange->{
            String html=exchange.getRequestURI().getPath().equals(root)
                    ? "<div id='cl-menu'><ul><li class='isparent'><a href='/arts-science/'>Invented Workshop School</a></li></ul></div>"
                    : school;
            byte[] bytes=html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8");
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort()+root;
            var first=scraper.scrapeAndStoreForRoot(url);
            var second=scraper.scrapeAndStoreForRoot(url);
            assertThat(first).hasSize(32);
            assertThat(second).hasSize(32);
            assertThat(programs.count()).isEqualTo(32);
            assertThat(versions.count()).isEqualTo(32);
            assertThat(programs.findByCode("workshop-17")).isPresent().hasValueSatisfying(p->
                    assertThat(p.getName()).isEqualTo("Invented Workshop 17"));
        } finally { server.stop(0); }
    }
}
