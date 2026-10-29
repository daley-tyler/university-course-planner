package com.courseplanner.api.Bulletin.Requirements;

import com.courseplanner.api.Bulletin.Programs.ProgramPersistenceService;
import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.Bulletin.dto.ParsedReqNode;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.repositories.ProgramCatalogVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramRequirementScraper {

    private final ProgramPersistenceService programPersistenceService;
    private final ProgramCatalogVersionRepository pcvRepo;
    private final RequirementTreeParser requirementTreeParser;
    private final RequirementPersistenceService requirementPersistenceService;
    private final ProgramPolicyExtractor programPolicyExtractor;
    private final PolicyPersistenceService policyPersistenceService;

    /**
     * Phase 3 runner: given ProgramMeta, ensure Program & PCV exist,
     * then scrape requirements and persist req_node tree.
     */
    @Transactional
    public void scrapeAndPersistRequirements(ProgramMeta meta) {

        // Phase 2: upsert program + PCV (already implemented by you)
        ProgramCatalogVersion pcv = programPersistenceService.upsertProgram(meta);
        if (pcv == null) {
            log.error("Failed to upsert ProgramCatalogVersion for {}", meta.getProgramName());
            return;
        }

        // Fetch program page
        Document doc;
        try {
            doc = Jsoup.connect(meta.getProgramUrl())
                    .userAgent("Mozilla/5.0")
                    .timeout(15_000)
                    .get();
        } catch (IOException e) {
            log.error("Failed to fetch program page {}", meta.getProgramUrl(), e);
            return;
        }

        // Parse requirements
        ParsedReqNode rootTree = requirementTreeParser.parseRequirements(doc);

        log.debug("Parsed requirement root for program '{}': kind={}, children={}",
                meta.getProgramName(),
                rootTree != null ? rootTree.getKind() : "null",
                rootTree != null && rootTree.getChildren() != null ? rootTree.getChildren().size() : 0);


        // Persist into req_node*
        requirementPersistenceService.replaceRequirementsForProgram(pcv, rootTree);

        // Extract and persist policy/progression rules (narrative)
        PolicyExtractionResult policy = programPolicyExtractor.extract(doc, meta.getProgramUrl());
        policyPersistenceService.upsertPolicy(pcv, policy);

        log.info("Phase 3: requirements scraped & stored for program {} ({})",
                meta.getProgramName(), meta.getProgramUrl());

    }
}
