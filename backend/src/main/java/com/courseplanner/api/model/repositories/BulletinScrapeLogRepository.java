package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.scrapeLog.BulletinScrapeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BulletinScrapeLogRepository extends JpaRepository<BulletinScrapeLog, Long> {
    boolean existsByCatalogLabel(String catalogLabel);

    Optional<BulletinScrapeLog> findFirstByCatalogYearOrderByScrapedAtDesc(Integer catalogYear);
}
