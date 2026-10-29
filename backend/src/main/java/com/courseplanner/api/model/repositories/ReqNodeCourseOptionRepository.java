package com.courseplanner.api.model.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.courseplanner.api.model.entities.requirements.ReqNodeCourseOption;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ReqNodeCourseOptionRepository extends JpaRepository<ReqNodeCourseOption, Long> {
    List<ReqNodeCourseOption> findByReqNode_Id(Long reqNodeId);

    @Query("""
        select rco from ReqNodeCourseOption rco
        join fetch rco.course c
        join fetch c.subject s
        where rco.reqNode.id in :nodeIds
        """)
    List<ReqNodeCourseOption> findWithCourseByReqNodeIds(@Param("nodeIds") List<Long> nodeIds);
}
