-- Add bulletin scrape log table for detecting new bulletin years.
CREATE TABLE bulletin_scrape_log (
                                     id BIGSERIAL PRIMARY KEY,
                                     catalog_label TEXT NOT NULL UNIQUE,
                                     catalog_year INT,
                                     bulletin_root TEXT,
                                     scraped_at TIMESTAMP DEFAULT now()
);
