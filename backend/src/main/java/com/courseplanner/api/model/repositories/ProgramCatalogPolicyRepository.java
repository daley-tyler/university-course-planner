package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.program.ProgramCatalogPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProgramCatalogPolicyRepository extends JpaRepository<ProgramCatalogPolicy, Long> {
    Optional<ProgramCatalogPolicy> findByCatalogVersion_Id(Long pcvId);
}
