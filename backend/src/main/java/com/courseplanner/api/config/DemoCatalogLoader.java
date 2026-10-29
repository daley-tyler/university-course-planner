package com.courseplanner.api.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** Loads invented catalog records only when both local and demo profiles are active. */
@Component
@Profile("local & demo")
public class DemoCatalogLoader implements ApplicationRunner {
    private final DataSource dataSource;
    public DemoCatalogLoader(DataSource dataSource) { this.dataSource = dataSource; }
    @Override
    public void run(ApplicationArguments arguments) {
        new ResourceDatabasePopulator(new ClassPathResource("demo/catalog.sql")).execute(dataSource);
    }
}
