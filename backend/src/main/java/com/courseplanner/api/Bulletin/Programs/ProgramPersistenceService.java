package com.courseplanner.api.Bulletin.Programs;

import com.courseplanner.api.Bulletin.dto.ProgramMeta;
import com.courseplanner.api.model.entities.program.Program;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;
import com.courseplanner.api.model.repositories.ProgramCatalogVersionRepository;
import com.courseplanner.api.model.repositories.ProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramPersistenceService {

    private final ProgramRepository programRepo;
    private final ProgramCatalogVersionRepository pcvRepo;


    @Transactional
    public ProgramCatalogVersion upsertProgram(ProgramMeta meta) {

        String programCode = meta.getSlug();
        String programName = meta.getProgramName();
        String degreeType  = extractDegreeTypeFromDegreeLabel(meta.getDegreeLabel());
        String programType = "DEGREE";
        String collegeName = meta.getCollegeName(); // <-- get college from meta

        AtomicBoolean isNewProgram = new AtomicBoolean(false);

        Program program = programRepo.findByCode(programCode)
                .orElseGet(() -> {
                    isNewProgram.set(true);
                    Program p = new Program();
                    p.setCode(programCode);
                    p.setName(programName);
                    p.setProgramType(programType);
                    p.setDegreeType(degreeType);
                    p.setCollege(collegeName); // <-- set on create
                    return programRepo.save(p);
                });

        if (!isNewProgram.get()) {
            boolean dirty = false;

            if (program.getName() == null || !program.getName().equals(programName)) {
                program.setName(programName);
                dirty = true;
            }

            if (program.getProgramType() == null || !program.getProgramType().equals(programType)) {
                program.setProgramType(programType);
                dirty = true;
            }

            if (degreeType != null &&
                    (program.getDegreeType() == null || !degreeType.equals(program.getDegreeType()))) {
                program.setDegreeType(degreeType);
                dirty = true;
            }

            // update college if changed
            if (collegeName != null &&
                    (program.getCollege() == null || !collegeName.equals(program.getCollege()))) {
                program.setCollege(collegeName);
                dirty = true;
            }

            if (dirty) {
                programRepo.save(program);
            }
        }


    // ===============================
        // 2) UPSERT ProgramCatalogVersion
        // ===============================
        Integer startYear = extractCatalogYear(meta.getCatalogLabel());
        if (startYear == null) {
            log.error("Unable to extract catalog year from {}", meta.getCatalogLabel());
            return null;
        }

        AtomicBoolean isNewPCV = new AtomicBoolean(false);

        ProgramCatalogVersion pcv = pcvRepo
                .findByProgramIdAndCatalogYear(program.getId(), startYear)
                .orElseGet(() -> {
                    isNewPCV.set(true);
                    ProgramCatalogVersion v = new ProgramCatalogVersion();
                    v.setProgram(program);
                    v.setCatalogYear(startYear);
                    v.setVersionLabel(meta.getCatalogLabel());
                    v.setEffectiveStart(date(startYear, Calendar.AUGUST, 1));
                    v.setEffectiveEnd(date(startYear + 1, Calendar.JULY, 31));
                    return pcvRepo.save(v);   // NEW PCV → only save once
                });

        // Only update PCV if it already existed
        if (!isNewPCV.get()) {
            if (meta.getCatalogLabel() != null &&
                    !meta.getCatalogLabel().equals(pcv.getVersionLabel())) {
                pcv.setVersionLabel(meta.getCatalogLabel());
                pcvRepo.save(pcv);
            }
        }

        return pcv;
    }

    /**
     * Derive degree type from the degreeLabel (heading).
     * Examples:
     *  - "Bachelor of Arts"                       -> "BA"
     *  - "Bachelor of Science"                   -> "BS"
     *  - "Bachelor of Integrative Studies"       -> "BIS"
     *  - "Bachelor of Arts/Bachelor of Science"  -> "BA,BS"
     *  - "Associate in Applied Science"          -> "AAS"
     *  - "Associate in Arts"                     -> "AA"
     *  - "Associate of Applied Business"         -> "AAB"
     *  - "Associate of Technical Study"          -> "ATS"
     *
     *  For Minors and Certificates return MINOR and CERTIFICATE respectively.
     */
    private String extractDegreeTypeFromDegreeLabel(String degreeLabel) {
        if (degreeLabel == null) return null;

        String lower = degreeLabel.toLowerCase(Locale.ROOT);
        List<String> codes = new ArrayList<>();

        // Bachelor-level
        if (lower.contains("bachelor of integrative studies")) {
            codes.add("BIS");
        }

        if (lower.contains("bachelor of arts")) {
            codes.add("BA");
        }

        if (lower.contains("bachelor of science")) {
            codes.add("BS");
        }

        if (lower.contains("bachelor of fine arts")) {
            codes.add("BFA");
        }

        if(lower.contains("bachelor of music")) {
            codes.add("BM");
        }

        // Associate-level
        if (lower.contains("associate in applied science")) {
            codes.add("AAS");
        }
        if (lower.contains("associate in arts")) {
            codes.add("AA");
        }
        if (lower.contains("associate of applied business")) {
            codes.add("AAB");
        }
        if (lower.contains("associate of technical study")) {
            codes.add("ATS");
        }

        // Minor or Certificate
        if (lower.contains("minor")) {
            codes.add("MINOR");
        }

        if (lower.contains("certificate")) {
            codes.add("CERTIFICATE");
        }

        if (lower.contains("co-major")) {
            codes.add("CO-MAJOR");
        }

        if (codes.isEmpty()) {
            return null;
        }

        // Deduplicate while preserving order
        LinkedHashSet<String> unique = new LinkedHashSet<>(codes);
        return String.join(",", unique);
    }

    private Integer extractCatalogYear(String label) {
        try {
            if (label == null) return null;
            return Integer.parseInt(label.split("-")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private Date date(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, day, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
