package com.courseplanner.api.model.entities.scrapeLog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bulletin_scrape_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulletinScrapeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "catalog_label", nullable = false, unique = true)
    private String catalogLabel;

    @Column(name = "catalog_year")
    private Integer catalogYear;

    @Column(name = "bulletin_root")
    private String bulletinRoot;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt = LocalDateTime.now();
}
