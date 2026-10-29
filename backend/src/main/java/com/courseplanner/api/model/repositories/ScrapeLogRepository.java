package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.scrapeLog.ScrapeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScrapeLogRepository extends JpaRepository<ScrapeLog, Long> {
    boolean existsByTerm(String term);
}
