package com.courseplanner.api.api.services;

import com.courseplanner.api.api.dto.ProgramListResponse;
import com.courseplanner.api.api.dto.ProgramSummaryDto;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.entities.scrapeLog.BulletinScrapeLog;
import com.courseplanner.api.model.repositories.BulletinScrapeLogRepository;
import com.courseplanner.api.model.repositories.ProgramCatalogVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramQueryServiceImpl implements ProgramQueryService {

    private final ProgramCatalogVersionRepository programCatalogVersionRepository;
    private final BulletinScrapeLogRepository bulletinScrapeLogRepository;

    @Override
    public ProgramListResponse listPrograms(Integer catalogYear, String catalogLabel) {
        log.debug("Listing programs catalogYear={} catalogLabel={}", catalogYear, catalogLabel);
        ResolvedCatalog resolved = resolveCatalog(catalogYear, catalogLabel);

        List<ProgramCatalogVersion> versions;
        if (resolved.catalogLabel != null) {
            versions = programCatalogVersionRepository
                    .findByVersionLabelWithProgram(resolved.catalogLabel);
        } else if (resolved.catalogYear != null) {
            versions = programCatalogVersionRepository
                    .findByCatalogYearWithProgram(resolved.catalogYear);
        } else {
            versions = List.of();
        }

        List<ProgramSummaryDto> items = versions.stream()
                .map(version -> new ProgramSummaryDto(
                        version.getProgram().getId(),
                        version.getProgram().getCode(),
                        version.getProgram().getName(),
                        version.getProgram().getProgramType(),
                        version.getProgram().getDegreeType(),
                        version.getProgram().getCollege(),
                        version.getId()
                ))
                .toList();

        return new ProgramListResponse(resolved.catalogYear, resolved.catalogLabel, items);
    }

    @Override
    public ProgramCatalogVersion resolveCatalogVersion(Long programId, Integer catalogYear, String catalogLabel) {
        if (programId == null) {
            return null;
        }

        if (catalogYear == null && catalogLabel == null) {
            ResolvedCatalog resolved = resolveCatalog(null, null);
            catalogYear = resolved.catalogYear;
            catalogLabel = resolved.catalogLabel;
        }

        if (catalogYear != null) {
            Optional<ProgramCatalogVersion> byYear =
                    programCatalogVersionRepository.findByProgramIdAndCatalogYear(programId, catalogYear);
            if (byYear.isPresent()) {
                return byYear.get();
            }
        }

        if (catalogLabel != null) {
            return programCatalogVersionRepository.findByProgramIdAndVersionLabel(programId, catalogLabel)
                    .orElse(null);
        }

        return null;
    }

    private ResolvedCatalog resolveCatalog(Integer catalogYear, String catalogLabel) {
        Integer resolvedYear = catalogYear;
        String resolvedLabel = catalogLabel;

        if (resolvedLabel != null && resolvedYear == null) {
            resolvedYear = parseCatalogYear(resolvedLabel);
        }

        if (resolvedYear != null && resolvedLabel == null) {
            resolvedLabel = bulletinScrapeLogRepository
                    .findFirstByCatalogYearOrderByScrapedAtDesc(resolvedYear)
                    .map(BulletinScrapeLog::getCatalogLabel)
                    .orElse(null);
        }

        if (resolvedYear == null && resolvedLabel == null) {
            Optional<BulletinScrapeLog> latest = bulletinScrapeLogRepository
                    .findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "catalogYear")))
                    .getContent()
                    .stream()
                    .findFirst();
            if (latest.isPresent()) {
                resolvedYear = latest.get().getCatalogYear();
                resolvedLabel = latest.get().getCatalogLabel();
            }
        }

        return new ResolvedCatalog(resolvedYear, resolvedLabel);
    }

    private Integer parseCatalogYear(String label) {
        if (label == null) return null;
        String normalized = label.trim().toLowerCase(Locale.US);
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(digits.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ResolvedCatalog(Integer catalogYear, String catalogLabel) {}
}
