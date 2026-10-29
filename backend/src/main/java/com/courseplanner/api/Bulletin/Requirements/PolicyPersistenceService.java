package com.courseplanner.api.Bulletin.Requirements;

import com.google.gson.Gson;
import com.courseplanner.api.model.entities.program.ProgramCatalogPolicy;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.repositories.ProgramCatalogPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyPersistenceService {

    private final ProgramCatalogPolicyRepository policyRepo;
    private final Gson gson = new Gson();

    @Transactional
    public void upsertPolicy(ProgramCatalogVersion pcv, PolicyExtractionResult result) {
        if (pcv == null || result == null) {
            return;
        }

        ProgramCatalogPolicy policy = policyRepo.findByCatalogVersion_Id(pcv.getId())
                .orElseGet(ProgramCatalogPolicy::new);

        policy.setCatalogVersion(pcv);
        policy.setGradeMinText(result.getGradeMinText());

        policy.setGradeMinCourses(result.getGradeMinCourses() != null
                ? gson.toJson(result.getGradeMinCourses())
                : null);

        policy.setNotes(result.getNotes());
        policy.setSourceUrl(result.getSourceUrl());
        policy.setScrapedAt(new Date());

        policyRepo.save(policy);
        log.debug("Upserted policy for PCV {} (program={})", pcv.getId(), pcv.getProgram().getName());
    }
}
