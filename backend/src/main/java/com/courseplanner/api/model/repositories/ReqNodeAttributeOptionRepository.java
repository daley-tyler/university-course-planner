package com.courseplanner.api.model.repositories;

import com.courseplanner.api.model.entities.requirements.ReqNodeAttributeOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReqNodeAttributeOptionRepository extends JpaRepository<ReqNodeAttributeOption, Long> {

    @Query("""
        select rao from ReqNodeAttributeOption rao
        join fetch rao.attribute a
        where rao.reqNode.id in :nodeIds
        """)
    List<ReqNodeAttributeOption> findWithAttributeByReqNodeIds(@Param("nodeIds") List<Long> nodeIds);
}
