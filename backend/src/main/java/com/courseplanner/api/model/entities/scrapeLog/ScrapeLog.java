package com.courseplanner.api.model.entities.scrapeLog;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "scrape_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ScrapeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String term;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt = LocalDateTime.now();
}
