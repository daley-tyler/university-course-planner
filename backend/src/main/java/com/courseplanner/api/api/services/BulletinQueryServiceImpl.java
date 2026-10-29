package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.BulletinListResponse;
import com.courseplanner.api.api.dto.BulletinSummaryDto;
import com.courseplanner.api.model.entities.scrapeLog.BulletinScrapeLog;
import com.courseplanner.api.model.repositories.BulletinScrapeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulletinQueryServiceImpl implements BulletinQueryService {

    private final BulletinScrapeLogRepository bulletinScrapeLogRepository;

    @Override
    public BulletinListResponse listRecent(int limit) {
        log.debug("Listing recent bulletins, limit={}", limit);
        int capped = Math.max(1, Math.min(limit, 10));

        List<BulletinScrapeLog> logs = bulletinScrapeLogRepository
                .findAll(PageRequest.of(0, capped, Sort.by(Sort.Direction.DESC, "catalogYear")))
                .getContent();

        Integer currentYear = logs.stream()
                .map(BulletinScrapeLog::getCatalogYear)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<BulletinSummaryDto> items = logs.stream()
                .map(log -> new BulletinSummaryDto(
                        log.getId(),
                        log.getCatalogYear(),
                        log.getCatalogLabel(),
                        log.getBulletinRoot(),
                        log.getScrapedAt(),
                        currentYear != null && currentYear.equals(log.getCatalogYear())
                ))
                .toList();

        return new BulletinListResponse(items);
    }
}
