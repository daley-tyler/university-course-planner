package com.courseplanner.api.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.courseplanner.api.model.entities.program.ProgramCatalogVersion;

import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProgramCatalogVersionRepository extends JpaRepository<ProgramCatalogVersion, Long> {
    Optional<ProgramCatalogVersion> findByProgramIdAndCatalogYear(Long programId, Integer catalogYear);

    Optional<ProgramCatalogVersion> findByProgramIdAndVersionLabel(Long programId, String versionLabel);

    @Query("select pcv from ProgramCatalogVersion pcv join fetch pcv.program where pcv.catalogYear = :catalogYear")
    List<ProgramCatalogVersion> findByCatalogYearWithProgram(Integer catalogYear);

    @Query("select pcv from ProgramCatalogVersion pcv join fetch pcv.program where pcv.versionLabel = :versionLabel")
    List<ProgramCatalogVersion> findByVersionLabelWithProgram(String versionLabel);
}
