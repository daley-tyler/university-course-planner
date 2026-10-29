package com.courseplanner.api.model.repositories;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.courseplanner.api.model.entities.requirements.ReqNode;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReqNodeRepository extends JpaRepository<ReqNode, Long> {

    void deleteByCatalogVersion_Id(Long pcvId);// ← correct derived query
    List<ReqNode> findByCatalogVersion_IdAndParentIsNullOrderByOrderIndexAsc(Long pcvId);
    Optional<ReqNode> findFirstByCatalogVersion_IdAndParentIsNull(Long pcvId);
    List<ReqNode> findByCatalogVersion_IdOrderByOrderIndexAsc(Long pcvId);

    @EntityGraph(attributePaths = "parent")
    @Query("select r from ReqNode r where r.catalogVersion.id = :pcvId order by r.orderIndex asc")
    List<ReqNode> findWithParentByCatalogVersionId(@Param("pcvId") Long pcvId);

    @EntityGraph(attributePaths = "catalogVersion")
    @Query("select r from ReqNode r where r.id = :id")
    Optional<ReqNode> findWithCatalogVersionById(@Param("id") Long id);
}
